// End-to-end API smoke test for a running stack (default http://localhost:8088/api).
// Exercises login, create, list, assign, progress, resolve, dashboard and permission rules,
// then writes every request/response status to a JSON evidence file.
//
// Requires the demo accounts: start the stack with DEMO_SEED=true and DEMO_PASSWORD set.
// Usage:  node scripts/smoke-test.mjs [--base http://localhost:8088/api] [--out file.json]
//         DEMO_PASSWORD must be set in the environment.
import { writeFileSync } from 'node:fs';

const arg = (name, fallback) => {
  const i = process.argv.indexOf(name);
  return i > -1 ? process.argv[i + 1] : fallback;
};
const BASE = arg('--base', process.env.BASE_URL || 'http://localhost:8088/api');
const OUT = arg('--out', 'smoke-test-result.json');
const PASSWORD = process.env.DEMO_PASSWORD;
if (!PASSWORD) {
  console.error('DEMO_PASSWORD is not set (start the stack with DEMO_SEED=true first).');
  process.exit(2);
}

const results = [];
let failures = 0;

async function call(method, path, { token, body } = {}) {
  const res = await fetch(BASE + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  const text = await res.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = text;
  }
  return { status: res.status, data };
}

async function step(id, description, method, path, opts, expectStatus, check) {
  const started = new Date().toISOString();
  let r;
  try {
    r = await call(method, path, opts);
  } catch (e) {
    r = { status: 0, data: String(e) };
  }
  const expected = Array.isArray(expectStatus) ? expectStatus : [expectStatus];
  let ok = expected.includes(r.status);
  let note = '';
  if (ok && check) {
    try {
      note = check(r.data) || '';
    } catch (e) {
      ok = false;
      note = e.message;
    }
  }
  if (!ok) failures += 1;
  results.push({ id, description, method, path, expected, actual: r.status, pass: ok, note, at: started });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${id}  ${method} ${path} -> ${r.status} (expected ${expected.join('/')}) ${note}`);
  return r.data;
}

const must = (cond, msg) => {
  if (!cond) throw new Error(msg);
};

const login = (email) => call('POST', '/auth/login', { body: { email, password: PASSWORD } });

const admin = (await login('admin@demo.local')).data?.token;
const engineer = (await login('engineer@demo.local')).data?.token;
const employee = (await login('employee@demo.local')).data?.token;
must(admin && engineer && employee, 'Demo logins failed - is DEMO_SEED=true and DEMO_PASSWORD correct?');

await step('S01', 'Login with valid credentials returns a JWT', 'POST', '/auth/login',
  { body: { email: 'employee@demo.local', password: PASSWORD } }, 200,
  (d) => must(typeof d.token === 'string' && d.user.role === 'END_USER', 'no token/role'));
await step('S02', 'Login with a wrong password is rejected', 'POST', '/auth/login',
  { body: { email: 'employee@demo.local', password: 'wrong-password-123' } }, 401);
await step('S03', 'Request without a token is rejected', 'GET', '/incidents', {}, [401, 403]);

const created = await step('S04', 'Employee creates an incident', 'POST', '/incidents',
  { token: employee, body: { title: 'Smoke test: laptop cannot reach Wi-Fi', description: 'Created by scripts/smoke-test.mjs', category: 'Network', priority: 'HIGH' } },
  201, (d) => must(d.status === 'OPEN', `status ${d.status}`));
const id = created?.id;
must(id, 'incident was not created');

await step('S05', 'Blank title fails validation', 'POST', '/incidents',
  { token: employee, body: { title: '', description: 'x', category: 'Network', priority: 'LOW' } }, 400);
await step('S06', 'Employee sees the new incident in their list', 'GET', '/incidents', { token: employee }, 200,
  (d) => must(d.some((i) => i.id === id), 'incident missing from list'));
await step('S07', 'Employee cannot open the admin report', 'GET', '/reports/summary', { token: employee }, 403);
await step('S08', 'Unassigned engineer cannot view the ticket', 'GET', `/incidents/${id}`, { token: engineer }, 403);

const engineers = await step('S09', 'Admin lists engineers', 'GET', '/engineers', { token: admin }, 200,
  (d) => must(d.length > 0, 'no engineers'));
const engineerId = engineers.find((e) => e.email === 'engineer@demo.local')?.id;

await step('S10', 'Admin assigns the ticket to the engineer', 'PATCH', `/incidents/${id}`,
  { token: admin, body: { assigneeId: engineerId } }, 200,
  (d) => must(d.status === 'ASSIGNED' && d.assignee?.id === engineerId, `status ${d.status}`));
await step('S11', 'Engineer now sees the ticket', 'GET', '/incidents', { token: engineer }, 200,
  (d) => must(d.some((i) => i.id === id), 'ticket not in engineer list'));
await step('S12', 'Engineer starts work and comments', 'PATCH', `/incidents/${id}`,
  { token: engineer, body: { status: 'IN_PROGRESS', comment: 'Checking the wireless profile' } }, 200,
  (d) => must(d.status === 'IN_PROGRESS', `status ${d.status}`));
await step('S13', 'Resolving without a resolution is refused', 'PATCH', `/incidents/${id}`,
  { token: engineer, body: { status: 'RESOLVED' } }, 400);
await step('S14', 'Engineer resolves with a resolution', 'PATCH', `/incidents/${id}`,
  { token: engineer, body: { status: 'RESOLVED', resolution: 'Re-installed the Wi-Fi driver' } }, 200,
  (d) => must(d.status === 'RESOLVED' && d.resolution, `status ${d.status}`));
await step('S15', 'Employee cannot update a ticket', 'PATCH', `/incidents/${id}`,
  { token: employee, body: { status: 'CLOSED' } }, 403);
await step('S16', 'Audit history records each step', 'GET', `/incidents/${id}/history`, { token: employee }, 200,
  (d) => {
    const actions = d.map((e) => e.action);
    for (const a of ['CREATED', 'ASSIGNED', 'STATUS_CHANGED', 'COMMENT', 'RESOLUTION']) must(actions.includes(a), `missing ${a}`);
    return actions.join(',');
  });
await step('S17', 'Requester was notified of the resolution', 'GET', '/notifications', { token: employee }, 200,
  (d) => must(d.some((n) => n.message === `Ticket #${id} is RESOLVED`), 'no resolution notification'));
await step('S18', 'Engineer was notified of the assignment', 'GET', '/notifications', { token: engineer }, 200,
  (d) => must(d.some((n) => n.message === `Ticket #${id} assigned to you`), 'no assignment notification'));
await step('S19', 'Admin dashboard counts the resolved ticket', 'GET', '/reports/summary', { token: admin }, 200,
  (d) => {
    must(d.byStatus.RESOLVED >= 1, 'RESOLVED count is 0');
    return `total=${d.total} resolved=${d.byStatus.RESOLVED}`;
  });
const me = await call('GET', '/me', { token: employee });
await step('S20', 'Engineer cannot change user roles', 'PATCH', `/users/${me.data.id}/role`,
  { token: engineer, body: { role: 'ADMIN' } }, 403);

const summary = {
  base: BASE,
  finishedAt: new Date().toISOString(),
  incidentId: id,
  total: results.length,
  passed: results.length - failures,
  failed: failures,
  results,
};
writeFileSync(OUT, JSON.stringify(summary, null, 2));
console.log(`\n${summary.passed}/${summary.total} checks passed. Evidence written to ${OUT}`);
process.exit(failures ? 1 : 0);
