package com.pasadita.api.services.deliveryorder;

import com.pasadita.api.dto.deliveryorder.DeliveryOrderChangeStatusDto;
import com.pasadita.api.dto.deliveryorder.DeliveryOrderCreateDto;
import com.pasadita.api.dto.deliveryorder.DeliveryOrderResponseDto;
import com.pasadita.api.dto.deliveryorder.DeliveryOrderSummaryDto;
import com.pasadita.api.dto.deliveryorder.DeliveryOrderUpdateDto;

import java.util.List;
import java.util.Optional;

public interface DeliveryOrderService {
    DeliveryOrderSummaryDto findAll();

    Optional<DeliveryOrderResponseDto> save(DeliveryOrderCreateDto deliveryOrderCreateDto);

    Optional<DeliveryOrderResponseDto> update(Long id, DeliveryOrderUpdateDto deliveryOrderUpdateDto);

    Optional<DeliveryOrderResponseDto> changeStatus(Long id, DeliveryOrderChangeStatusDto deliveryOrderChangeStatusDto);
}
