package com.pasadita.api.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "customers")
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "customer_id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "customer_type_id", referencedColumnName = "customer_type_id")
    private CustomerType customerType;

    @Column(name = "postal_code")
    private String postalCode;

    @Column(name = "custom_discount")
    private BigDecimal custom_discount;

    private String name;
    private String phone;
    private String address;
    private String city;
    private String notes;
    private boolean active;
}
