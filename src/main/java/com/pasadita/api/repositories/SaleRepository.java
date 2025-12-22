package com.pasadita.api.repositories;

import com.pasadita.api.entities.Sale;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface SaleRepository extends CrudRepository<Sale, Long> {

    @EntityGraph(attributePaths = {"employee", "customer", "paymentMethod", "saleDetails", "saleDetails.product"})
    Optional<Sale> findWithDetailsById(Long id);
}
