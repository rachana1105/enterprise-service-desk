package com.bea.servicedesk.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Registered user with a BCrypt password hash and a single access role. */
@Entity
@Table(name = "users")
public class UserAccount {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public String name;

  @Column(nullable = false, unique = true)
  public String email;

  @Column(nullable = false)
  public String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public Role role = Role.END_USER;

  /** Creates an empty account; required by JPA. */
  public UserAccount() {}

  /**
   * Creates an account.
   *
   * @param name display name
   * @param email unique login email
   * @param passwordHash BCrypt hash of the password, never the plain text
   * @param role access role
   */
  public UserAccount(String name, String email, String passwordHash, Role role) {
    this.name = name;
    this.email = email;
    this.passwordHash = passwordHash;
    this.role = role;
  }
}
