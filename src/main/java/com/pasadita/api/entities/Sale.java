package com.pasadita.api.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "sales")
public class Sale {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sale_id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "employee_id", referencedColumnName = "employee_id")
    private Employee employee;

    @ManyToOne
    @JoinColumn(name = "customer_id", referencedColumnName = "customer_id")
    private Customer customer;

    @ManyToOne
    @JoinColumn(name = "payment_method_id", referencedColumnName = "payment_method_id")
    private PaymentMethod paymentMethod;

    private LocalDateTime datetime;
    private BigDecimal subtotal;

    @Column(name = "discount_amount")
    private BigDecimal discountAmount;

    private BigDecimal total;
    private boolean paid;
    private String notes;
}
