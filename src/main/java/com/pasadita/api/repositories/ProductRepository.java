package com.pasadita.api.repositories;

import com.pasadita.api.entities.Product;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface ProductRepository extends CrudRepository<Product, Long> {

    @Modifying
    @Query("UPDATE Product p SET p.price = :price WHERE p.id = :id")
    void updatePriceById(@Param("id") Long id, @Param("price") BigDecimal price);

    List<Product> findByActiveTrue();

    @Query("SELECT p FROM Product p LEFT JOIN SaleDetail sd ON sd.product = p GROUP BY p ORDER BY COALESCE(SUM(sd.quantity), 0) DESC")
    List<Product> findAllOrderByTotalSoldDesc();

}
