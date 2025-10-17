package com.pasadita.api.repositories;

import com.pasadita.api.entities.SaleDetail;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SaleDetailRepository extends CrudRepository<SaleDetail, Long> {
    List<SaleDetail> findBySaleId(Long saleId);

    List<SaleDetail> findByProductId(Long productId);

    @Query("SELECT sd FROM SaleDetail sd " +
            "JOIN FETCH sd.sale s " +
            "JOIN FETCH sd.product p " +
            "WHERE s.id = :saleId " +
            "ORDER BY sd.id")
    List<SaleDetail> findBySaleIdWithDetails(@Param("saleId") Long saleId);
}
