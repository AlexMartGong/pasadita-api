package com.pasadita.api.services.customer;

import com.pasadita.api.dto.customer.CustomerChangeStatusDto;
import com.pasadita.api.dto.customer.CustomerCreateDto;
import com.pasadita.api.dto.customer.CustomerResponseDto;
import com.pasadita.api.dto.customer.CustomerUpdateDto;

import java.util.List;
import java.util.Optional;

public interface CustomerService {

    List<CustomerResponseDto> findAll();

    Optional<CustomerResponseDto> save(CustomerCreateDto customerCreateDto);

    Optional<CustomerResponseDto> update(Long id, CustomerUpdateDto customerUpdateDto);

    Optional<CustomerResponseDto> changeStatus(Long id, CustomerChangeStatusDto productChangeStatusDto);

}
