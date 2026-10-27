package com.pasadita.api.repositories;

import com.pasadita.api.dto.account.SalePaidAmount;
import com.pasadita.api.entities.SalePayment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SalePaymentRepository extends CrudRepository<SalePayment, Long> {

    boolean existsBySaleId(Long saleId);

    boolean existsByClientRequestId(String clientRequestId);

    List<SalePayment> findByClientRequestIdOrderByIdAsc(String clientRequestId);

    @EntityGraph(attributePaths = {"employee", "paymentMethod"})
    List<SalePayment> findBySaleIdInOrderByDatetimeAscIdAsc(Collection<Long> saleIds);

    @Query("""
            SELECT new com.pasadita.api.dto.account.SalePaidAmount(p.sale.id, SUM(p.amount))
            FROM SalePayment p
            WHERE p.sale.id IN :saleIds
            GROUP BY p.sale.id
            """)
    List<SalePaidAmount> sumAmountsBySaleIds(@Param("saleIds") Collection<Long> saleIds);
}
