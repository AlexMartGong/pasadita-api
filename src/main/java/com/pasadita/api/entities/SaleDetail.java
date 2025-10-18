package com.pasadita.api.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "saledetails")
public class SaleDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "detail_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", referencedColumnName = "sale_id", nullable = false)
    @NotNull(message = "Sale is required")
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", referencedColumnName = "product_id", nullable = false)
    @NotNull(message = "Product is required")
    private Product product;

    @Column(nullable = false)
    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0.01", message = "Quantity must be positive")
    @Digits(integer = 10, fraction = 3, message = "Quantity must have up to 10 digits and 3 decimal places")
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false)
    @NotNull(message = "Unit price is required")
    @DecimalMin(value = "0.01", message = "Unit price must be positive")
    @Digits(integer = 10, fraction = 2, message = "Unit price must have up to 10 digits and 2 decimal places")
    private BigDecimal unitPrice;

    @Column(nullable = false)
    @NotNull(message = "Subtotal is required")
    @DecimalMin(value = "0.00", message = "Subtotal must be non-negative")
    @Digits(integer = 12, fraction = 2, message = "Subtotal must have up to 12 digits and 2 decimal places")
    private BigDecimal subtotal;

    @DecimalMin(value = "0.00", message = "Discount must be non-negative")
    @Digits(integer = 10, fraction = 2, message = "Discount must have up to 10 digits and 2 decimal places")
    @Builder.Default
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(nullable = false)
    @NotNull(message = "Total is required")
    @DecimalMin(value = "0.00", message = "Total must be non-negative")
    @Digits(integer = 12, fraction = 2, message = "Total must have up to 12 digits and 2 decimal places")
    private BigDecimal total;
}
