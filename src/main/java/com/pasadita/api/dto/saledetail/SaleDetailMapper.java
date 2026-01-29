package com.pasadita.api.dto.saledetail;

import com.pasadita.api.entities.SaleDetail;
import com.pasadita.api.entities.Sale;
import com.pasadita.api.entities.Product;
import com.pasadita.api.utils.DateTimeUtils;
import org.springframework.stereotype.Component;

@Component
public class SaleDetailMapper {

    public SaleDetail toEntity(SaleDetailCreateDto dto, Sale sale, Product product) {
        return SaleDetail.builder()
                .sale(sale)
                .product(product)
                .quantity(dto.getQuantity())
                .unitPrice(dto.getUnitPrice())
                .subtotal(dto.getSubtotal())
                .discount(dto.getDiscount())
                .total(dto.getTotal())
                .build();
    }

    public void updateEntity(SaleDetail saleDetail, SaleDetailUpdateDto dto, Product product) {
        if (product != null) {
            saleDetail.setProduct(product);
        }
        if (dto.getQuantity() != null) {
            saleDetail.setQuantity(dto.getQuantity());
        }
        if (dto.getUnitPrice() != null) {
            saleDetail.setUnitPrice(dto.getUnitPrice());
        }
        if (dto.getSubtotal() != null) {
            saleDetail.setSubtotal(dto.getSubtotal());
        }
        if (dto.getDiscount() != null) {
            saleDetail.setDiscount(dto.getDiscount());
        }
        if (dto.getTotal() != null) {
            saleDetail.setTotal(dto.getTotal());
        }
    }

    public SaleDetailResponseDto toResponseDto(SaleDetail saleDetail) {
        return SaleDetailResponseDto.builder()
                .detailId(saleDetail.getId())
                .saleId(saleDetail.getSale() != null ? saleDetail.getSale().getId() : null)
                .saleDate(saleDetail.getSale() != null ? DateTimeUtils.toMexicoTime(saleDetail.getSale().getDatetime()) : null)
                .productId(saleDetail.getProduct() != null ? saleDetail.getProduct().getId() : null)
                .productName(saleDetail.getProduct() != null ? saleDetail.getProduct().getName() : null)
                .productCategory(saleDetail.getProduct() != null ? saleDetail.getProduct().getCategory().name() : null)
                .quantity(saleDetail.getQuantity())
                .unitPrice(saleDetail.getUnitPrice())
                .discount(saleDetail.getDiscount())
                .subtotal(saleDetail.getSubtotal())
                .total(saleDetail.getTotal())
                .build();
    }
}
