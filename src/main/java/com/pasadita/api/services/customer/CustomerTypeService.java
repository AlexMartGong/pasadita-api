package com.pasadita.api.services.customer;

import com.pasadita.api.dto.customer.CustomerTypeCreateDto;
import com.pasadita.api.dto.customer.CustomerTypeResponseDto;
import com.pasadita.api.dto.customer.CustomerTypeUpdateDto;

import java.util.List;
import java.util.Optional;

public interface CustomerTypeService {

    List<CustomerTypeResponseDto> getAllCustomerTypes();

    Optional<CustomerTypeResponseDto> saveCustomerType(CustomerTypeCreateDto customerTypeCreateDto);

    Optional<CustomerTypeResponseDto> updateCustomerType(CustomerTypeUpdateDto customerTypeUpdateDto);
}
