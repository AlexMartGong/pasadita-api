package com.pasadita.api.entities;

import com.pasadita.api.enums.delivery.DeliveryStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "deliveryorders")
public class DeliveryOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", referencedColumnName = "sale_id", nullable = false, unique = true)
    private Sale sale;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DeliveryStatus status;

    @Column(name = "request_date", nullable = false)
    @Builder.Default
    private LocalDateTime requestDate = LocalDateTime.now();

    @Column(name = "delivery_date")
    private LocalDateTime deliveryDate;

    @NotBlank(message = "Delivery address is required")
    @Size(max = 200, message = "Delivery address cannot exceed 200 characters")
    @Column(name = "delivery_address", nullable = false, length = 200)
    private String deliveryAddress;

    @NotBlank(message = "Contact phone is required")
    @Size(max = 15, message = "Contact phone cannot exceed 15 characters")
    @Column(name = "contact_phone", nullable = false, length = 15)
    private String contactPhone;

    @NotBlank(message = "Contact person is required")
    @Size(max = 100, message = "Contact person cannot exceed 100 characters")
    @Column(name = "contact_person", nullable = false, length = 100)
    private String contactPerson;

    @Column(name = "delivery_cost", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal deliveryCost = BigDecimal.ZERO;

    @Size(max = 1000, message = "Delivery instructions cannot exceed 1000 characters")
    @Column(name = "delivery_instructions", columnDefinition = "TEXT")
    private String deliveryInstructions;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_employee_id", referencedColumnName = "employee_id")
    private Employee deliveryEmployee;
}
