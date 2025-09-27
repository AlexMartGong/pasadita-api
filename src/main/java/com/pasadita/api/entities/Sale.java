package com.pasadita.api.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "saleDetails")
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "sales")
public class Sale {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sale_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", referencedColumnName = "employee_id", nullable = false)
    @NotNull(message = "Employee is required")
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", referencedColumnName = "customer_id", nullable = false)
    @NotNull(message = "Customer is required")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_method_id", referencedColumnName = "payment_method_id", nullable = false)
    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    @Column(nullable = false)
    @NotNull(message = "Datetime is required")
    @PastOrPresent(message = "Datetime cannot be in the future")
    private LocalDateTime datetime;

    @Column(nullable = false)
    @NotNull(message = "Subtotal is required")
    @DecimalMin(value = "0.00", message = "Subtotal must be non-negative")
    @Digits(integer = 12, fraction = 2, message = "Subtotal must have up to 12 digits and 2 decimal places")
    private BigDecimal subtotal;

    @Column(name = "discount_amount", nullable = false)
    @NotNull(message = "Discount amount is required")
    @DecimalMin(value = "0.00", message = "Discount amount must be non-negative")
    @Digits(integer = 10, fraction = 2, message = "Discount amount must have up to 10 digits and 2 decimal places")
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(nullable = false)
    @NotNull(message = "Total is required")
    @DecimalMin(value = "0.00", message = "Total must be non-negative")
    @Digits(integer = 12, fraction = 2, message = "Total must have up to 12 digits and 2 decimal places")
    private BigDecimal total;

    @Column(nullable = false)
    @NotNull(message = "Paid status is required")
    @Builder.Default
    private Boolean paid = false;

    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    private String notes;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<SaleDetail> saleDetails;
}
