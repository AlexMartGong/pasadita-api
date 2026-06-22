package com.pasadita.api.controllers;

import com.pasadita.api.dto.product.ProductChangeStatusDto;
import com.pasadita.api.dto.product.ProductCreateDto;
import com.pasadita.api.dto.product.ProductResponseDto;
import com.pasadita.api.dto.product.ProductUpdateDto;
import com.pasadita.api.dto.product.ProductUpdatePriceDto;
import com.pasadita.api.services.product.ProductService;
import com.pasadita.api.utils.ValidationUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CAJERO', 'ROLE_PEDIDOS')")
    @GetMapping("/all")
    public ResponseEntity<List<ProductResponseDto>> getAllProducts() {
        return ResponseEntity.ok(productService.findAll());
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDto> getProductById(@PathVariable Long id) {
        return productService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CAJERO', 'ROLE_PEDIDOS')")
    @PostMapping("/save")
    public ResponseEntity<?> saveProduct(@Valid @RequestBody ProductCreateDto productDto, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ValidationUtils.getValidationErrors(result));
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(productService.save(productDto));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @PutMapping("/update/{id}")
    public ResponseEntity<?> updateProduct(@PathVariable Long id, @Valid @RequestBody ProductUpdateDto productUpdateDto, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ValidationUtils.getValidationErrors(result));
        }

        return ResponseEntity.ok(productService.update(id, productUpdateDto));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @PutMapping("/update-price/{id}")
    public ResponseEntity<?> updateProductPrice(@PathVariable Long id, @Valid @RequestBody ProductUpdatePriceDto productUpdatePriceDto, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ValidationUtils.getValidationErrors(result));
        }

        productService.updatePriceById(id, productUpdatePriceDto);
        return ResponseEntity.ok(Map.of("message", "The product price has been updated successfully"));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @PutMapping("/change-status/{id}")
    public ResponseEntity<?> changeProductStatus(@PathVariable Long id, @RequestBody ProductChangeStatusDto status) {
        productService.changeStatus(id, status);
        return ResponseEntity.ok(Map.of("message", "The product status has been changed successfully"));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CAJERO')")
    @PostMapping("/{id}/image")
    public ResponseEntity<ProductResponseDto> uploadProductImage(@PathVariable Long id,
                                                                 @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(productService.uploadImage(id, file));
    }
}
