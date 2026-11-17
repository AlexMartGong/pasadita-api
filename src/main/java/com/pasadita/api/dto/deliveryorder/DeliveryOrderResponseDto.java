package com.pasadita.api.dto.deliveryorder;

import com.pasadita.api.enums.delivery.DeliveryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryOrderResponseDto {
    private Long id;
    private Long saleId;
    private LocalDateTime requestDate;
    // Requested during Mexico's current day. The delivery list also carries PENDIENTE orders from earlier days,
    // which must stay out of the day's figures
    private boolean requestedToday;
    private Long customerId;
    private String customerName;
    private String deliveryAddress;
    private String contactPhone;
    private boolean paid;
    private BigDecimal total;
    private BigDecimal balance;
    // The note has active payments (abonos): the order can't be collected or cancelled, only its payments viewed
    private boolean hasPayments;
    private DeliveryStatus status;
}
