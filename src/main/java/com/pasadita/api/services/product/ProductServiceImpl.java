package com.pasadita.api.services.product;

import com.pasadita.api.dto.product.*;
import com.pasadita.api.entities.Product;
import com.pasadita.api.exceptions.EntityNotFoundException;
import com.pasadita.api.repositories.ProductRepository;
import com.pasadita.api.services.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final StorageService storageService;

    @Transactional(readOnly = true)
    @Override
    public List<ProductResponseDto> findAll() {
        return productRepository.findAllOrderByTotalSoldDesc()
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
        Product existingProduct = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with id: " + id));
        productMapper.updateEntityFromDto(existingProduct, productUpdateDto);
        Product updatedProduct = productRepository.save(existingProduct);
        return Optional.of(productMapper.toResponseDto(updatedProduct));
    }

    @Transactional
    @Override
    public void updatePriceById(Long id, ProductUpdatePriceDto productUpdatePriceDto) {
        if (!productRepository.existsById(id)) {
            throw new EntityNotFoundException("Product not found with id: " + id);
        }
        productRepository.updatePriceById(id, productUpdatePriceDto.getPrice());
    }

    @Transactional
    @Override
    public Optional<ProductResponseDto> changeStatus(Long id, ProductChangeStatusDto productChangeStatusDto) {
        Product existingProduct = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with id: " + id));
        existingProduct.setActive(productChangeStatusDto.isActive());
        Product updatedProduct = productRepository.save(existingProduct);
        return Optional.of(productMapper.toResponseDto(updatedProduct));
    }

    @Transactional
    @Override
    public ProductResponseDto uploadImage(Long productId, MultipartFile file) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with id: " + productId));
        String imageUrl = storageService.uploadFile(file, "products");
        product.setImageUrl(imageUrl);
        Product updatedProduct = productRepository.save(product);
        return productMapper.toResponseDto(updatedProduct);
    }
}
