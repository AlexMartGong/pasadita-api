package com.pasadita.api.services.deliveryorder;

import com.pasadita.api.dto.deliveryorder.DeliveryOrderCreateDto;
import com.pasadita.api.dto.deliveryorder.DeliveryOrderResponseDto;

import java.util.List;
import java.util.Optional;

public interface DeliveryOrderService {
    List<DeliveryOrderResponseDto> findAll();

    Optional<DeliveryOrderResponseDto> save(DeliveryOrderCreateDto deliveryOrderCreateDto);
}
