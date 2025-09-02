package com.pasadita.api.services.customer;

import com.pasadita.api.dto.customer.CustomerTypeCreateDto;
import com.pasadita.api.dto.customer.CustomerTypeMapper;
import com.pasadita.api.dto.customer.CustomerTypeResponseDto;
import com.pasadita.api.dto.customer.CustomerTypeUpdateDto;
import com.pasadita.api.entities.CustomerType;
import com.pasadita.api.repositories.CustomerTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerTypeServiceImpl implements CustomerTypeService {

    private final CustomerTypeRepository customerTypeRepository;
    private final CustomerTypeMapper customerTypeMapper;

    @Override
    @Transactional(readOnly = true)
    public List<CustomerTypeResponseDto> getAllCustomerTypes() {
        return ((List<CustomerType>) customerTypeRepository.findAll())
                .stream()
                .map(customerTypeMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public Optional<CustomerTypeResponseDto> saveCustomerType(CustomerTypeCreateDto customerTypeCreateDto) {
        CustomerType customerType = customerTypeMapper.toEntity(customerTypeCreateDto);
        CustomerType savedCustomerType = customerTypeRepository.save(customerType);
        return Optional.ofNullable(customerTypeMapper.toDto(savedCustomerType));
    }

    @Override
    @Transactional
    public Optional<CustomerTypeResponseDto> updateCustomerType(CustomerTypeUpdateDto customerTypeUpdateDto) {
        return customerTypeRepository.findById(customerTypeUpdateDto.getId())
                .map(customerType -> {
                    customerTypeMapper.updateEntityFromDto(customerTypeUpdateDto, customerType);
                    CustomerType updatedCustomerType = customerTypeRepository.save(customerType);
                    return customerTypeMapper.toDto(updatedCustomerType);
                });
    }
}
