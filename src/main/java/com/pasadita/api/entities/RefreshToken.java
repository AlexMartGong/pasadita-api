package com.pasadita.api.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One refresh token of a session. Only the SHA-256 hex of the opaque value is stored. {@code familyId} groups every
 * rotation that stems from one login (one device); {@code replacedById} is set when the token was rotated, which
 * tells a rotated token (reuse candidate) apart from one revoked by logout or by an admin. All dates are UTC.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"employee", "tokenHash"})
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", referencedColumnName = "employee_id", nullable = false)
    private Employee employee;

    // char columns: columnDefinition keeps ddl-auto=validate from expecting varchar
    @Column(name = "token_hash", columnDefinition = "char(64)", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "family_id", columnDefinition = "char(36)", nullable = false)
    private String familyId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "replaced_by_id")
    private Long replacedById;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
