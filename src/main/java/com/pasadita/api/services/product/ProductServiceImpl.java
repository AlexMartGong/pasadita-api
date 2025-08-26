package com.pasadita.api.services.product;

import com.pasadita.api.dto.product.*;
import com.pasadita.api.entities.Product;
import com.pasadita.api.repositories.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(readOnly = true)
    @Override
    public Optional<ProductResponseDto> findById(Long id) {
        return productRepository.findById(id)
                .map(productMapper::toResponseDto);
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
    public Optional<ProductResponseDto> update(Long id, ProductUpdateDto productUpdateDto) {
        return productRepository.findById(id)
                .map(existingProduct -> {
                    productMapper.updateEntityFromDto(existingProduct, productUpdateDto);
                    Product updatedProduct = productRepository.save(existingProduct);
                    return productMapper.toResponseDto(updatedProduct);
                });
    }

    @Transactional
    @Override
    public void updatePriceById(Long id, ProductUpdatePriceDto productUpdatePriceDto) {
        productRepository.updatePriceById(id, productUpdatePriceDto.getPrice());
    }

    @Transactional
    @Override
    public Optional<ProductResponseDto> changeStatus(Long id, ProductChangeStatusDto productChangeStatusDto) {
        return productRepository.findById(id)
                .map(existingProduct -> {
                    existingProduct.setActive(productChangeStatusDto.isActive());
                    Product updatedProduct = productRepository.save(existingProduct);
                    return productMapper.toResponseDto(updatedProduct);
                });
    }
}
