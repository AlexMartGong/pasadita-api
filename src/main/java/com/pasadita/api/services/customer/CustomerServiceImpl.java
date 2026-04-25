package com.pasadita.api.services.customer;

import com.pasadita.api.dto.customer.*;
import com.pasadita.api.entities.Customer;
import com.pasadita.api.entities.CustomerType;
import com.pasadita.api.repositories.CustomerRepository;
import com.pasadita.api.repositories.CustomerTypeRepository;
import com.pasadita.api.exceptions.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerTypeRepository customerTypeRepository;
    private final CustomerMapper customerMapper;

    @Override
    @Transactional(readOnly = true)
    public List<CustomerResponseDto> findAll() {
        return ((List<Customer>) customerRepository.findAll())
                .stream()
                .map(customerMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public Optional<CustomerResponseDto> save(CustomerCreateDto customerCreateDto) {
        CustomerType customerType = customerTypeRepository.findById(customerCreateDto.getCustomerTypeId())
                .orElseThrow(() -> new EntityNotFoundException("Customer type not found with id: " + customerCreateDto.getCustomerTypeId()));
        Customer customer = customerMapper.toEntity(customerCreateDto, customerType);
        Customer savedCustomer = customerRepository.save(customer);
        return Optional.of(customerMapper.toResponseDto(savedCustomer));
    }

    @Override
    @Transactional
    public Optional<CustomerResponseDto> update(Long id, CustomerUpdateDto customerUpdateDto) {
        CustomerType customerType = customerTypeRepository.findById(customerUpdateDto.getCustomerTypeId())
                .orElseThrow(() -> new EntityNotFoundException("Customer type not found with id: " + customerUpdateDto.getCustomerTypeId()));
        Customer existingCustomer = customerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found with id: " + id));
        customerMapper.updateEntity(existingCustomer, customerUpdateDto, customerType);
        Customer savedCustomer = customerRepository.save(existingCustomer);
        return Optional.of(customerMapper.toResponseDto(savedCustomer));
    }

    @Override
    @Transactional
    public Optional<CustomerResponseDto> changeStatus(Long id, CustomerChangeStatusDto customerChangeStatusDto) {
        Customer existingCustomer = customerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found with id: " + id));
        customerMapper.updateStatus(existingCustomer, customerChangeStatusDto);
        Customer savedCustomer = customerRepository.save(existingCustomer);
        return Optional.of(customerMapper.toResponseDto(savedCustomer));
    }
}
