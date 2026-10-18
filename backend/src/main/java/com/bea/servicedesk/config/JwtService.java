package com.bea.servicedesk.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Issues and verifies HMAC-SHA signed JSON Web Tokens valid for 24 hours. */
@Service
public class JwtService {
  private static final long TOKEN_LIFETIME_MS = 86_400_000L;
  private static final int MIN_SECRET_BYTES = 32;

  private final String secret;
  private SecretKey key;

  /**
   * Creates the service from the configured secret.
   *
   * @param secret value of {@code app.jwt.secret}, supplied through the JWT_SECRET variable
   */
  public JwtService(@Value("${app.jwt.secret}") String secret) {
    this.secret = secret;
  }

  /** Builds the signing key and refuses to start with a short or development secret. */
  @PostConstruct
  public void initializeKey() {
    byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
    if (bytes.length < MIN_SECRET_BYTES || secret.startsWith("change-this")) {
      throw new IllegalStateException(
          "JWT_SECRET must be a private random value of at least 32 bytes");
    }
    key = Keys.hmacShaKeyFor(bytes);
  }

  /**
   * Issues a token for a user.
   *
   * @param email user email stored as the token subject
   * @return compact signed JWT
   */
  public String issue(String email) {
    return Jwts.builder()
        .subject(email)
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + TOKEN_LIFETIME_MS))
        .signWith(key)
        .compact();
  }

  /**
   * Verifies a token signature and expiry and returns its subject.
   *
   * @param token compact JWT
   * @return user email
   */
  public String subject(String token) {
    return Jwts.parser()
        .verifyWith(key)
        .build()
        .parseSignedClaims(token)
        .getPayload()
        .getSubject();
  }
}
