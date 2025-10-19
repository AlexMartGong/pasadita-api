package com.pasadita.api.services.sale;

import com.pasadita.api.dto.sale.SaleCreateDto;
import com.pasadita.api.dto.sale.SaleMapper;
import com.pasadita.api.dto.sale.SaleResponseDto;
import com.pasadita.api.dto.sale.SaleUpdateDto;
import com.pasadita.api.dto.saledetail.SaleDetailResponseDto;
import com.pasadita.api.entities.*;
import com.pasadita.api.repositories.CustomerRepository;
import com.pasadita.api.repositories.EmployeeRepository;
import com.pasadita.api.repositories.PaymentMethodRepository;
import com.pasadita.api.repositories.SaleRepository;
import com.pasadita.api.repositories.SaleDetailRepository;
import com.pasadita.api.services.saledetail.SaleDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class SaleServiceImpl implements SaleService {

    private final SaleRepository saleRepository;
    private final EmployeeRepository employeeRepository;
    private final CustomerRepository customerRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final SaleMapper saleMapper;
    private final SaleDetailService saleDetailService;
    private final SaleDetailRepository saleDetailRepository;

    @Override
    @Transactional(readOnly = true)
    public List<SaleResponseDto> findAll() {
        List<Sale> sales = (List<Sale>) saleRepository.findAll();
        return sales.stream()
                .map(saleMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<SaleResponseDto> save(SaleCreateDto saleCreateDto) {
        Employee employee = findEmployeeById(saleCreateDto.getEmployeeId());
        Customer customer = findCustomerById(saleCreateDto.getCustomerId());
        PaymentMethod paymentMethod = findPaymentMethodById(saleCreateDto.getPaymentMethodId());

        Sale sale = saleMapper.toEntity(saleCreateDto, employee, customer, paymentMethod);
        Sale savedSale = saleRepository.save(sale);

        saleCreateDto.getSaleDetails().forEach(saleDetailDto -> {
            saleDetailDto.setSaleId(savedSale.getId());
            saleDetailService.save(saleDetailDto);
        });

        return Optional.ofNullable(saleMapper.toResponseDto(savedSale));
    }

    @Override
    public Optional<SaleResponseDto> update(Long id, SaleUpdateDto saleUpdateDto) {
        Sale existingSale = saleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sale not found with id: " + id));

        Employee employee = findEmployeeById(saleUpdateDto.getEmployeeId());
        Customer customer = findCustomerById(saleUpdateDto.getCustomerId());
        PaymentMethod paymentMethod = findPaymentMethodById(saleUpdateDto.getPaymentMethodId());

        saleMapper.updateEntity(existingSale, saleUpdateDto, employee, customer, paymentMethod);
        Sale updatedSale = saleRepository.save(existingSale);

        // Delete existing sale details
        saleDetailRepository.deleteBySaleId(existingSale.getId());

        // Create new sale details
        saleUpdateDto.getSaleDetails().forEach(saleDetailDto -> {
            saleDetailDto.setSaleId(updatedSale.getId());
            saleDetailService.save(saleDetailDto);
        });

        return Optional.ofNullable(saleMapper.toResponseDto(updatedSale));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SaleDetailResponseDto> getSaleDetails(Long saleId) {
        List<SaleDetail> saleDetails = saleDetailRepository.findBySaleIdOrderById(saleId);

        return saleDetails.stream()
                .map(sd -> SaleDetailResponseDto.builder()
                        .detailId(sd.getId())
                        .saleId(sd.getSale().getId())
                        .saleDate(sd.getSale().getDatetime())
                        .productId(sd.getProduct().getId())
                        .productName(sd.getProduct().getName())
                        .productCategory(sd.getProduct().getCategory().name())
                        .quantity(sd.getQuantity())
                        .unitPrice(sd.getUnitPrice())
                        .discount(sd.getDiscount())
                        .subtotal(sd.getSubtotal())
                        .total(sd.getTotal())
                        .build())
                .collect(Collectors.toList());
    }

    private Employee findEmployeeById(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found with id: " + employeeId));
    }

    private Customer findCustomerById(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Customer not found with id: " + customerId));
    }

    private PaymentMethod findPaymentMethodById(Long paymentMethodId) {
        return paymentMethodRepository.findById(paymentMethodId)
                .orElseThrow(() -> new RuntimeException("Payment method not found with id: " + paymentMethodId));
    }
}
