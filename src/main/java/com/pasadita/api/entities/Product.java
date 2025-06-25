package com.pasadita.api.entities;

import com.pasadita.api.enums.product.Category;
import com.pasadita.api.enums.product.UnitMeasure;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(of = "id")
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @NotNull(message = "Category cannot be null")
    private Category category;

    @NotBlank
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    @Column(name = "unit_price")
    @NotNull
    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    @DecimalMax(value = "99999999.99", message = "Price must be less than 100 million")
    @Digits(integer = 6, fraction = 2, message = "Price must have up to 6 digits and 2 decimal places")
    private BigDecimal price;

    @Column(name = "unit_measure")
    @Enumerated(EnumType.STRING)
    @NotNull(message = "Unit measure cannot be null")
    private UnitMeasure unitMeasure;

    private boolean active;

}
