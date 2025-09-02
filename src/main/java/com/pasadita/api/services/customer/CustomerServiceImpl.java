package com.pasadita.api.services.customer;

import com.pasadita.api.dto.customer.*;
import com.pasadita.api.entities.Customer;
import com.pasadita.api.entities.CustomerType;
import com.pasadita.api.repositories.CustomerRepository;
import com.pasadita.api.repositories.CustomerTypeRepository;
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
        Optional<CustomerType> customerTypeOpt = customerTypeRepository.findById(customerCreateDto.getCustomerTypeId());
        if (customerTypeOpt.isEmpty()) {
            return Optional.empty();
        }

        Customer customer = customerMapper.toEntity(customerCreateDto, customerTypeOpt.get());
        Customer savedCustomer = customerRepository.save(customer);
        return Optional.of(customerMapper.toResponseDto(savedCustomer));
    }

    @Override
    @Transactional
    public Optional<CustomerResponseDto> update(Long id, CustomerUpdateDto customerUpdateDto) {
        Optional<CustomerType> customerTypeOpt = customerTypeRepository.findById(customerUpdateDto.getCustomerTypeId());
        return customerTypeOpt.flatMap(customerType -> customerRepository.findById(id)
                .map(existingCustomer -> {
                    customerMapper.updateEntity(existingCustomer, customerUpdateDto, customerType);
                    Customer savedCustomer = customerRepository.save(existingCustomer);
                    return customerMapper.toResponseDto(savedCustomer);
                }));
    }

    @Override
    @Transactional
    public Optional<CustomerResponseDto> changeStatus(Long id, CustomerChangeStatusDto customerChangeStatusDto) {
        return customerRepository.findById(id)
                .map(existingCustomer -> {
                    customerMapper.updateStatus(existingCustomer, customerChangeStatusDto);
                    Customer savedCustomer = customerRepository.save(existingCustomer);
                    return customerMapper.toResponseDto(savedCustomer);
                });
    }
}
