package com.pasadita.api.entities;

import com.pasadita.api.enums.delivery.DeliveryStatus;
import com.pasadita.api.utils.DateTimeUtils;
import jakarta.persistence.*;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_employee_id", referencedColumnName = "employee_id")
    private Employee deliveryEmployee;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DeliveryStatus status;

    @Column(name = "request_date", nullable = false)
    @Builder.Default
    private LocalDateTime requestDate = DateTimeUtils.nowUtc();

    @Column(name = "delivery_address", nullable = false, length = 200)
    private String deliveryAddress;

    @Column(name = "contact_phone", nullable = false, length = 15)
    private String contactPhone;

    @Column(name = "delivery_cost", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal deliveryCost = BigDecimal.ZERO;
}
