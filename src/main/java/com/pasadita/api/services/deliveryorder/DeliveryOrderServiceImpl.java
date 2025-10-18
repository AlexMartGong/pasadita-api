package com.pasadita.api.services.deliveryorder;

import com.pasadita.api.dto.deliveryorder.DeliveryOrderChangeStatusDto;
import com.pasadita.api.dto.deliveryorder.DeliveryOrderCreateDto;
import com.pasadita.api.dto.deliveryorder.DeliveryOrderMapper;
import com.pasadita.api.dto.deliveryorder.DeliveryOrderResponseDto;
import com.pasadita.api.entities.DeliveryOrder;
import com.pasadita.api.entities.Employee;
import com.pasadita.api.entities.Sale;
import com.pasadita.api.repositories.DeliveryOrderRepository;
import com.pasadita.api.repositories.EmployeeRepository;
import com.pasadita.api.repositories.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
    public List<DeliveryOrderResponseDto> findAll() {
        List<DeliveryOrder> deliveryOrders = (List<DeliveryOrder>) deliveryOrderRepository.findAll();
        return deliveryOrders.stream()
                .map(deliveryOrderMapper::toResponseDto)
                .collect(Collectors.toList());
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
