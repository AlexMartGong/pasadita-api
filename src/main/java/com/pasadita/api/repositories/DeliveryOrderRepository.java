package com.pasadita.api.repositories;

import com.pasadita.api.entities.DeliveryOrder;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DeliveryOrderRepository extends CrudRepository<DeliveryOrder, Long> {

    @Query("SELECT d FROM DeliveryOrder d WHERE d.requestDate >= :startOfDay AND d.requestDate < :endOfDay")
    List<DeliveryOrder> findByRequestDateBetween(@Param("startOfDay") LocalDateTime startOfDay,
                                                   @Param("endOfDay") LocalDateTime endOfDay);

    Optional<DeliveryOrder> findBySaleId(Long saleId);
}
