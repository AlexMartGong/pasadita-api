package com.pasadita.api.repositories;

import com.pasadita.api.entities.SaleDetail;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface SaleDetailRepository extends CrudRepository<SaleDetail, Long> {
    List<SaleDetail> findBySaleId(Long saleId);

    List<SaleDetail> findByProductId(Long productId);
}
