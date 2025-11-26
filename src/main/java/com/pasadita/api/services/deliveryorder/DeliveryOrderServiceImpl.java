package com.pasadita.api.services.deliveryorder;

import com.pasadita.api.dto.deliveryorder.DeliveryOrderChangeStatusDto;
import com.pasadita.api.dto.deliveryorder.DeliveryOrderCreateDto;
import com.pasadita.api.dto.deliveryorder.DeliveryOrderMapper;
import com.pasadita.api.dto.deliveryorder.DeliveryOrderResponseDto;
import com.pasadita.api.dto.deliveryorder.DeliveryOrderSummaryDto;
import com.pasadita.api.dto.deliveryorder.DeliveryOrderUpdateDto;
import com.pasadita.api.entities.DeliveryOrder;
import com.pasadita.api.entities.Employee;
import com.pasadita.api.entities.Sale;
import com.pasadita.api.repositories.DeliveryOrderRepository;
import com.pasadita.api.repositories.EmployeeRepository;
import com.pasadita.api.repositories.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class DeliveryOrderServiceImpl implements DeliveryOrderService {

    private final DeliveryOrderRepository deliveryOrderRepository;
    private final SaleRepository saleRepository;
    private final EmployeeRepository employeeRepository;
    private final DeliveryOrderMapper deliveryOrderMapper;

    @Override
    @Transactional(readOnly = true)
    public DeliveryOrderSummaryDto findAll() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        List<DeliveryOrder> deliveryOrders = deliveryOrderRepository.findByRequestDateBetween(startOfDay, endOfDay);
        List<DeliveryOrderResponseDto> orderDtos = deliveryOrders.stream()
                .map(deliveryOrderMapper::toResponseDto)
                .collect(Collectors.toList());

        int totalOrders = orderDtos.size();
        BigDecimal totalAmount = orderDtos.stream()
                .map(DeliveryOrderResponseDto::getTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return DeliveryOrderSummaryDto.builder()
                .orders(orderDtos)
                .totalOrders(totalOrders)
                .totalAmount(totalAmount)
                .build();
    }

    @Override
    public Optional<DeliveryOrderResponseDto> save(DeliveryOrderCreateDto deliveryOrderCreateDto) {
        Sale sale = findSaleById(deliveryOrderCreateDto.getSaleId());
        Employee deliveryEmployee = findEmployeeById(deliveryOrderCreateDto.getDeliveryEmployeeId());

        DeliveryOrder deliveryOrder = deliveryOrderMapper.toEntity(deliveryOrderCreateDto, sale, deliveryEmployee);
        DeliveryOrder savedDeliveryOrder = deliveryOrderRepository.save(deliveryOrder);

        return Optional.ofNullable(deliveryOrderMapper.toResponseDto(savedDeliveryOrder));
    }

    @Override
    public Optional<DeliveryOrderResponseDto> update(Long id, DeliveryOrderUpdateDto deliveryOrderUpdateDto) {
        DeliveryOrder deliveryOrder = deliveryOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Delivery order not found with id: " + id));

        Employee deliveryEmployee = findEmployeeById(deliveryOrderUpdateDto.getDeliveryEmployeeId());

        deliveryOrderMapper.updateEntity(deliveryOrder, deliveryOrderUpdateDto, deliveryEmployee);
        DeliveryOrder updatedDeliveryOrder = deliveryOrderRepository.save(deliveryOrder);

        return Optional.ofNullable(deliveryOrderMapper.toResponseDto(updatedDeliveryOrder));
    }

    @Override
    public Optional<DeliveryOrderResponseDto> changeStatus(Long id, DeliveryOrderChangeStatusDto deliveryOrderChangeStatusDto) {
        DeliveryOrder deliveryOrder = deliveryOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Delivery order not found with id: " + id));

        deliveryOrder.setStatus(deliveryOrderChangeStatusDto.getStatus());
        DeliveryOrder updatedDeliveryOrder = deliveryOrderRepository.save(deliveryOrder);

        return Optional.ofNullable(deliveryOrderMapper.toResponseDto(updatedDeliveryOrder));
    }

    private Sale findSaleById(Long saleId) {
        return saleRepository.findById(saleId)
                .orElseThrow(() -> new RuntimeException("Sale not found with id: " + saleId));
    }

    private Employee findEmployeeById(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found with id: " + employeeId));
    }
}
