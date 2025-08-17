package com.pasadita.api.controllers;

import com.pasadita.api.dto.product.ProductCreateDto;
import com.pasadita.api.dto.product.ProductResponseDto;
import com.pasadita.api.entities.Product;
import com.pasadita.api.services.product.ProductService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductService productService;

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @GetMapping("/all")
    public ResponseEntity<List<ProductResponseDto>> getAllProducts() {
        List<ProductResponseDto> products = productService.findAll();
        return ResponseEntity.ok(products);
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @GetMapping("/active")
    public ResponseEntity<List<Product>> getActiveProducts() {
        List<Product> activeProducts = productService.findByActiveTrue();
        return ResponseEntity.ok(activeProducts);
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable Long id) {
        return productService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @PostMapping("/save")
    public ResponseEntity<?> saveProduct(@Valid @RequestBody ProductCreateDto productDto, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(getValidationErrors(result));
        }

        try {
            ProductResponseDto savedProduct = productService.save(productDto)
                    .orElseThrow(() -> new RuntimeException("Error saving the product"));

            return ResponseEntity.status(HttpStatus.CREATED).body(savedProduct);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error saving the product: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @PutMapping("/update/{id}")
    public ResponseEntity<?> updateProduct(@PathVariable Long id, @Valid @RequestBody Product product, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(getValidationErrors(result));
        }

        try {
            Product updatedProduct = productService.update(id, product)
                    .orElseThrow(() -> new RuntimeException("Error updating the product"));

            return ResponseEntity.ok(updatedProduct);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error updating the product: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @PutMapping("/update-price/{id}")
    public ResponseEntity<?> updateProductPrice(@PathVariable Long id, @RequestBody BigDecimal price) {

        if (productService.findById(id).isEmpty()) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Product not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }

        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "The price must be greater than 0");
            return ResponseEntity.badRequest().body(error);
        }

        try {
            productService.updatePriceById(id, price);
            Map<String, String> response = new HashMap<>();
            response.put("message", "The product price has been updated successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error updating the product: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @PutMapping("/change-status/{id}")
    public ResponseEntity<?> changeProductStatus(@PathVariable Long id, @RequestBody boolean status) {
        if (productService.findById(id).isEmpty()) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Product not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }

        try {
            productService.changeStatus(id, status)
                    .orElseThrow(() -> new RuntimeException("Error changing the product status"));

            Map<String, String> response = new HashMap<>();
            response.put("message", "The product status has been changed successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error changing the product status: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }

    }

    private Map<String, String> getValidationErrors(BindingResult result) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : result.getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        return errors;
    }

}
