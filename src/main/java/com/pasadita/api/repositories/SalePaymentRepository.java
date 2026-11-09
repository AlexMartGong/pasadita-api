package com.pasadita.api.repositories;

import com.pasadita.api.dto.account.SalePaidAmount;
import com.pasadita.api.entities.SalePayment;
import com.pasadita.api.enums.account.PaymentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

// Cancelled payments count for nothing: every lookup that feeds debt, balances or preconditions filters by status.
// Only the clientRequestId lookups see every row, so a retry never re-applies a payment someone cancelled
public interface SalePaymentRepository extends CrudRepository<SalePayment, Long> {

    boolean existsBySaleIdAndStatus(Long saleId, PaymentStatus status);

    boolean existsByClientRequestId(String clientRequestId);

    List<SalePayment> findByClientRequestIdOrderByIdAsc(String clientRequestId);

    @EntityGraph(attributePaths = {"employee", "paymentMethod"})
    List<SalePayment> findBySaleIdInAndStatusOrderByDatetimeAscIdAsc(Collection<Long> saleIds, PaymentStatus status);

    // Every status: the note's payment list shows cancelled payments too
    @EntityGraph(attributePaths = {"employee", "paymentMethod"})
    List<SalePayment> findBySaleIdOrderByDatetimeAscIdAsc(Long saleId);

    @EntityGraph(attributePaths = {"sale", "employee", "paymentMethod"})
    Optional<SalePayment> findWithSaleById(Long id);

    // Scalar lookup: resolves the customer to lock without loading any entity before the lock
    @Query("SELECT p.sale.customer.id FROM SalePayment p WHERE p.id = :paymentId")
    Optional<Long> findCustomerIdById(@Param("paymentId") Long paymentId);

    @Query("""
            SELECT new com.pasadita.api.dto.account.SalePaidAmount(p.sale.id, SUM(p.amount))
            FROM SalePayment p
            WHERE p.sale.id IN :saleIds AND p.status = com.pasadita.api.enums.account.PaymentStatus.APLICADO
            GROUP BY p.sale.id
            """)
    List<SalePaidAmount> sumActiveAmountsBySaleIds(@Param("saleIds") Collection<Long> saleIds);
}
