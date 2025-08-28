package com.pasadita.api.dto.customer;

import com.pasadita.api.entities.Customer;
import com.pasadita.api.entities.CustomerType;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {

    public Customer toEntity(CustomerCreateDto dto, CustomerType customerType) {
        return Customer.builder()
                .customerType(customerType)
                .name(dto.getName())
                .phone(dto.getPhone())
                .address(dto.getAddress())
                .city(dto.getCity())
                .postalCode(dto.getPostalCode())
                .custom_discount(dto.getCustomDiscount())
                .notes(dto.getNotes())
                .active(true) // New customers are active by default
                .build();
    }

    public Customer updateEntity(Customer customer, CustomerUpdateDto dto, CustomerType customerType) {
        customer.setCustomerType(customerType);
        customer.setName(dto.getName());
        customer.setPhone(dto.getPhone());
        customer.setAddress(dto.getAddress());
        customer.setCity(dto.getCity());
        customer.setPostalCode(dto.getPostalCode());
        customer.setCustom_discount(dto.getCustomDiscount());
        customer.setNotes(dto.getNotes());
        return customer;
    }

    public Customer updateStatus(Customer customer, CustomerChangeStatusDto dto) {
        customer.setActive(dto.getActive());
        return customer;
    }

    public CustomerResponseDto toResponseDto(Customer customer) {
        return CustomerResponseDto.builder()
                .id(customer.getId())
                .customerTypeId(customer.getCustomerType() != null ? customer.getCustomerType().getId() : null)
                .customerTypeName(customer.getCustomerType() != null ? customer.getCustomerType().getName() : null)
                .name(customer.getName())
                .phone(customer.getPhone())
                .address(customer.getAddress())
                .city(customer.getCity())
                .postalCode(customer.getPostalCode())
                .customDiscount(customer.getCustom_discount())
                .notes(customer.getNotes())
                .active(customer.isActive())
                .build();
    }
}
