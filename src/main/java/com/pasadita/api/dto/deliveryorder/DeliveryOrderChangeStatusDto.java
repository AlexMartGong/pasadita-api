package com.pasadita.api.dto.deliveryorder;

import com.pasadita.api.enums.delivery.DeliveryStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryOrderChangeStatusDto {
    @NotNull
    private DeliveryStatus status;
}
