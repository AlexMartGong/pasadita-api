package com.pasadita.api.services.product;

import com.pasadita.api.dto.product.ProductResponseDto;
import com.pasadita.api.entities.Product;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ProductService {

    List<ProductResponseDto> findAll();

    Optional<Product> findById(Long id);

    Optional<Product> save(Product product);

    Optional<Product> update(Long id, Product product);

    Optional<Product> changeStatus(Long id, boolean status);

    void updatePriceById(Long id, BigDecimal price);

    List<Product> findByActiveTrue();

}
