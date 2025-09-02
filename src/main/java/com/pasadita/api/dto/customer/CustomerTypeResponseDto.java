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
public class CustomerTypeResponseDto {
    private Long id;
    private String name;
    private String description;
    private BigDecimal discount;
}
