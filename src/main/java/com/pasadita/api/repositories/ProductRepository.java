package com.pasadita.api.repositories;

import com.pasadita.api.entities.Product;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

public interface ProductRepository extends CrudRepository<Product, Long> {

    @Modifying
    @Transactional
    @Query("UPDATE Product p SET p.price = :price WHERE p.id = :id")
    void updatePriceById(@Param("id") Long id, @Param("price") BigDecimal price);

}
