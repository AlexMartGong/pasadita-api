package com.pasadita.api.entities;

import com.pasadita.api.enums.account.PaymentHistoryAction;
import com.pasadita.api.utils.DateTimeUtils;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row per creation, modification or cancellation of a {@link SalePayment}. Immutable: no setters, Hibernate never
 * issues updates for it, and its repository exposes no update or delete.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"payment", "previousPaymentMethod", "newPaymentMethod", "changedBy"})
@EqualsAndHashCode(of = "id")
@Immutable
@Entity
@Table(name = "sale_payment_history")
public class SalePaymentHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", referencedColumnName = "payment_id", nullable = false)
    private SalePayment payment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentHistoryAction action;

    @Column(name = "previous_amount", precision = 10, scale = 2)
    private BigDecimal previousAmount;

    @Column(name = "new_amount", precision = 10, scale = 2)
    private BigDecimal newAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_payment_method_id", referencedColumnName = "payment_method_id")
    private PaymentMethod previousPaymentMethod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "new_payment_method_id", referencedColumnName = "payment_method_id")
    private PaymentMethod newPaymentMethod;

    @Column(length = 200)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by", referencedColumnName = "employee_id", nullable = false)
    private Employee changedBy;

    @Column(name = "changed_at", nullable = false)
    @Builder.Default
    private LocalDateTime changedAt = DateTimeUtils.nowUtc();
}
