package com.pasadita.api.entities;

import com.pasadita.api.enums.product.Category;
import com.pasadita.api.enums.product.UnitMeasure;
import jakarta.persistence.*;
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

    private String name;

    @Enumerated(EnumType.STRING)
    private Category category;

    @Column(name = "unit_price")
    private BigDecimal price;

    @Column(name = "unit_measure")
    @Enumerated(EnumType.STRING)
    private UnitMeasure unitMeasure;

    private boolean active;
}
