package com.pasadita.api.services.product;

import com.pasadita.api.dto.product.ProductCreateDto;
import com.pasadita.api.dto.product.ProductMapper;
import com.pasadita.api.dto.product.ProductResponseDto;
import com.pasadita.api.entities.Product;
import com.pasadita.api.repositories.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl implements ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductMapper productMapper;

    @Transactional(readOnly = true)
    @Override
    public List<ProductResponseDto> findAll() {
        return ((List<Product>) productRepository.findAll())
                .stream()
                .map(productMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> findByActiveTrue() {
        return productRepository.findByActiveTrue();
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<Product> findById(Long id) {
        return productRepository.findById(id);
    }

    @Transactional
    @Override
    public Optional<ProductResponseDto> save(ProductCreateDto productDto) {
        Product product = productMapper.toEntity(productDto);
        Product savedProduct = productRepository.save(product);
        return Optional.of(productMapper.toResponseDto(savedProduct));
    }

    @Transactional
    @Override
    public Optional<Product> update(Long id, Product product) {
        return productRepository.findById(id)
                .map(existingProduct -> {
                    existingProduct.setName(product.getName());
                    existingProduct.setPrice(product.getPrice());
                    existingProduct.setCategory(product.getCategory());
                    existingProduct.setUnitMeasure(product.getUnitMeasure());
                    return productRepository.save(existingProduct);
                });
    }

    @Transactional
    @Override
    public void updatePriceById(Long id, BigDecimal price) {
        productRepository.updatePriceById(id, price);
    }

    @Transactional
    @Override
    public Optional<Product> changeStatus(Long id, boolean status) {
        return productRepository.findById(id)
                .map(existingProduct -> {
                    existingProduct.setActive(status);
                    return productRepository.save(existingProduct);
                });
    }
}
