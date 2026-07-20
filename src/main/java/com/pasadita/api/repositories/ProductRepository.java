package com.pasadita.api.repositories;

import com.pasadita.api.entities.Product;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface ProductRepository extends CrudRepository<Product, Long> {

    @Modifying
    @Query("UPDATE Product p SET p.price = :price WHERE p.id = :id")
    void updatePriceById(@Param("id") Long id, @Param("price") BigDecimal price);

    List<Product> findByActiveTrue();

    @Query("""
            SELECT p FROM Product p
            LEFT JOIN SaleDetail sd ON sd.product = p
            LEFT JOIN sd.sale s
            GROUP BY p
            ORDER BY COALESCE(SUM(CASE WHEN s.datetime BETWEEN :startDate AND :endDate THEN sd.quantity ELSE 0 END), 0) DESC
            """)
    List<Product> findAllOrderByTotalSoldDesc(@Param("startDate") LocalDateTime startDate,
                                              @Param("endDate") LocalDateTime endDate);

}
