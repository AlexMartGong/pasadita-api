package com.pasadita.api.dto.customer;

import com.pasadita.api.entities.CustomerType;
import org.springframework.stereotype.Component;

@Component
public class CustomerTypeMapper {

    public CustomerTypeResponseDto toDto(CustomerType customerType) {
        if (customerType == null) {
            return null;
        }

        return CustomerTypeResponseDto.builder()
                .id(customerType.getId())
                .name(customerType.getName())
                .description(customerType.getDescription())
                .discount(customerType.getDiscount())
                .build();
    }

    public CustomerType toEntity(CustomerTypeCreateDto dto) {
        if (dto == null) {
            return null;
        }

        return CustomerType.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .discount(dto.getDiscount())
                .build();
    }

    public void updateEntityFromDto(CustomerTypeUpdateDto dto, CustomerType entity) {
        if (dto == null || entity == null) {
            return;
        }

        if (dto.getName() != null) {
            entity.setName(dto.getName());
        }

        if (dto.getDescription() != null) {
            entity.setDescription(dto.getDescription());
        }

        if (dto.getDiscount() != null) {
            entity.setDiscount(dto.getDiscount());
        }
    }
}
