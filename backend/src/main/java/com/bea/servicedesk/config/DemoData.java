package com.bea.servicedesk.config;

import com.bea.servicedesk.model.Incident;
import com.bea.servicedesk.model.Priority;
import com.bea.servicedesk.model.Role;
import com.bea.servicedesk.model.Status;
import com.bea.servicedesk.model.UserAccount;
import com.bea.servicedesk.repo.IncidentRepository;
import com.bea.servicedesk.repo.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Optional local-demo seed data. Runs only when DEMO_SEED=true and takes the shared demo password
 * from DEMO_PASSWORD, so no credential is stored in source code.
 */
@Configuration
public class DemoData {
  private static final int MIN_DEMO_PASSWORD = 12;

  @Bean
  CommandLineRunner seed(
      UserRepository users, IncidentRepository incidents, PasswordEncoder encoder) {
    return args -> {
      if (!"true".equalsIgnoreCase(System.getenv("DEMO_SEED"))) {
        return;
      }
      String password = System.getenv("DEMO_PASSWORD");
      if (password == null || password.length() < MIN_DEMO_PASSWORD) {
        throw new IllegalStateException(
            "DEMO_SEED=true requires DEMO_PASSWORD with at least 12 characters");
      }
      if (users.findByEmailIgnoreCase("admin@demo.local").isPresent()) {
        return;
      }
      String hash = encoder.encode(password);
      users.save(new UserAccount("Demo Admin", "admin@demo.local", hash, Role.ADMIN));
      final UserAccount engineer =
          users.save(new UserAccount("Demo Engineer", "engineer@demo.local", hash, Role.ENGINEER));
      final UserAccount employee =
          users.save(new UserAccount("Demo Employee", "employee@demo.local", hash, Role.END_USER));
      Incident a = new Incident();
      a.title = "VPN connection unavailable";
      a.description = "Unable to connect to corporate VPN after login";
      a.category = "Network";
      a.priority = Priority.HIGH;
      a.requester = employee;
      a.assignee = engineer;
      a.status = Status.ASSIGNED;
      incidents.save(a);
      Incident b = new Incident();
      b.title = "Email delivery delayed";
      b.description = "Outgoing mail is queued for several minutes";
      b.category = "Email";
      b.priority = Priority.MEDIUM;
      b.requester = employee;
      incidents.save(b);
    };
  }
}
