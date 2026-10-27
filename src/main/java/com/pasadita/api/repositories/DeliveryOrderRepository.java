package com.pasadita.api.repositories;

import com.pasadita.api.entities.DeliveryOrder;
import com.pasadita.api.enums.delivery.DeliveryStatus;
import org.springframework.data.jpa.repository.EntityGraph;
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

    boolean existsBySaleId(Long saleId);

    @EntityGraph(attributePaths = {"sale"})
    Optional<DeliveryOrder> findWithSaleById(Long id);

    @EntityGraph(attributePaths = {"sale"})
    Optional<DeliveryOrder> findWithSaleBySaleId(Long saleId);

    // Scalar lookups: they resolve the customer to lock without loading any entity before the lock
    @Query("SELECT d.sale.customer.id FROM DeliveryOrder d WHERE d.id = :orderId")
    Optional<Long> findCustomerIdById(@Param("orderId") Long orderId);

    @Query("SELECT d.sale.customer.id FROM DeliveryOrder d WHERE d.sale.id = :saleId")
    Optional<Long> findCustomerIdBySaleId(@Param("saleId") Long saleId);

    @EntityGraph(attributePaths = {"sale"})
    @Query("""
            SELECT d FROM DeliveryOrder d
            WHERE d.sale.customer.id = :customerId AND d.status = :status
            ORDER BY d.sale.datetime ASC, d.sale.id ASC
            """)
    List<DeliveryOrder> findByCustomerIdAndStatusOldestFirst(@Param("customerId") Long customerId,
                                                             @Param("status") DeliveryStatus status);
}
