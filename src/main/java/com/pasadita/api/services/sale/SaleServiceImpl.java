package com.pasadita.api.services.sale;

import com.pasadita.api.dto.sale.SaleCreateDto;
import com.pasadita.api.dto.sale.SaleMapper;
import com.pasadita.api.dto.sale.SaleResponseDto;
import com.pasadita.api.dto.sale.SaleUpdateDto;
import com.pasadita.api.entities.*;
import com.pasadita.api.repositories.CustomerRepository;
import com.pasadita.api.repositories.EmployeeRepository;
import com.pasadita.api.repositories.PaymentMethodRepository;
import com.pasadita.api.repositories.SaleRepository;
import com.pasadita.api.services.saledetail.SaleDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

    @Override
    @Transactional(readOnly = true)
    public List<SaleResponseDto> findAll() {
        List<Sale> sales = (List<Sale>) saleRepository.findAll();
        return sales.stream()
                .map(saleMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    public SaleResponseDto save(SaleCreateDto saleCreateDto) {
        Employee employee = findEmployeeById(saleCreateDto.getEmployeeId());
        Customer customer = findCustomerById(saleCreateDto.getCustomerId());
        PaymentMethod paymentMethod = findPaymentMethodById(saleCreateDto.getPaymentMethodId());

        Sale sale = saleMapper.toEntity(saleCreateDto, employee, customer, paymentMethod);
        Sale savedSale = saleRepository.save(sale);

        saleCreateDto.getSaleDetails().forEach(saleDetailDto -> {
            saleDetailDto.setSaleId(savedSale.getId());
            saleDetailService.save(saleDetailDto);
        });

        return saleMapper.toResponseDto(savedSale);
    }

    @Override
    public SaleResponseDto update(Long id, SaleUpdateDto saleUpdateDto) {
        Sale existingSale = saleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sale not found with id: " + id));

        Employee employee = findEmployeeById(saleUpdateDto.getEmployeeId());
        Customer customer = findCustomerById(saleUpdateDto.getCustomerId());
        PaymentMethod paymentMethod = findPaymentMethodById(saleUpdateDto.getPaymentMethodId());

        saleMapper.updateEntity(existingSale, saleUpdateDto, employee, customer, paymentMethod);
        Sale updatedSale = saleRepository.save(existingSale);

        return saleMapper.toResponseDto(updatedSale);
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
