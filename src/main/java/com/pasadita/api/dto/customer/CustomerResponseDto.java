package com.pasadita.api.dto.customer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerResponseDto {
    private Long id;
    private Long customerTypeId;
    private String customerTypeName;
    private String name;
    private String phone;
    private String address;
    private String city;
    private String postalCode;
    private BigDecimal customDiscount;
    private String notes;
    private boolean active;
}
