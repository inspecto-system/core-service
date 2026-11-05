package dev.inspecto.core.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** User account. */
@Entity
@Table(name = "users")
public class User {

  @Id private UUID id;

  @Column(nullable = false)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "full_name", nullable = false)
  private String fullName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Role role;

  @Column(nullable = false)
  private String locale;

  @Column(name = "failed_login_attempts", nullable = false)
  private int failedLoginAttempts;

  @Column(name = "locked_until")
  private Instant lockedUntil;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private long version;

  protected User() {
    // for JPA
  }

  /** Creates a new account; the email must already be normalized. */
  public User(UUID id, String email, String passwordHash, String fullName, Instant now) {
    this.id = id;
    this.email = email;
    this.passwordHash = passwordHash;
    this.fullName = fullName;
    this.role = Role.INSPECTOR;
    this.locale = "ru";
    this.createdAt = now;
    this.updatedAt = now;
  }

  /** Whether sign-in is temporarily blocked after repeated failures. */
  public boolean isLocked(Instant now) {
    return lockedUntil != null && lockedUntil.isAfter(now);
  }

  /** Counts a failed sign-in and locks the account when the limit is reached. */
  public void registerFailedLogin(Instant now, int maxAttempts, Duration lockDuration) {
    failedLoginAttempts++;
    if (failedLoginAttempts >= maxAttempts) {
      lockedUntil = now.plus(lockDuration);
      failedLoginAttempts = 0;
    }
    updatedAt = now;
  }

  /** Clears the failure counter after a successful sign-in. */
  public void registerSuccessfulLogin(Instant now) {
    if (failedLoginAttempts > 0 || lockedUntil != null) {
      failedLoginAttempts = 0;
      lockedUntil = null;
      updatedAt = now;
    }
  }

  /** Updates editable profile fields; {@code null} keeps the current value. */
  public void updateProfile(String newFullName, String newLocale, Instant now) {
    if (newFullName != null) {
      fullName = newFullName;
    }
    if (newLocale != null) {
      locale = newLocale;
    }
    updatedAt = now;
  }

  /** Replaces the password hash. */
  public void changePassword(String newPasswordHash, Instant now) {
    passwordHash = newPasswordHash;
    updatedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getFullName() {
    return fullName;
  }

  public Role getRole() {
    return role;
  }

  public String getLocale() {
    return locale;
  }

  public int getFailedLoginAttempts() {
    return failedLoginAttempts;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public long getVersion() {
    return version;
  }
}
