package com.pasadita.api.entities;

import com.pasadita.api.enums.product.Category;
import com.pasadita.api.enums.product.UnitMeasure;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    @NotNull
    private Category category;

    @NotBlank
    private String name;

    @Column(name = "unit_price")
    @NotNull
    private BigDecimal price;

    @Column(name = "unit_measure")
    @Enumerated(EnumType.STRING)
    @NotNull
    private UnitMeasure unitMeasure;

    private boolean active;

}
