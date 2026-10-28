package com.pasadita.api.repositories;

import com.pasadita.api.dto.account.PaymentHistoryRow;
import com.pasadita.api.entities.SalePaymentHistory;
import com.pasadita.api.enums.account.PaymentHistoryAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

// Insert-only on purpose: the history is immutable, so this repository exposes no update or delete
public interface SalePaymentHistoryRepository extends Repository<SalePaymentHistory, Long> {

    String HISTORY_ROW_SELECT = """
            SELECT new com.pasadita.api.dto.account.PaymentHistoryRow(
                h.id, h.changedAt, h.action, h.previousAmount, h.newAmount,
                pm.id, pm.name, nm.id, nm.name, h.reason, e.id, e.fullName,
                p.id, s.id, d.id, c.id, c.name)
            """;

    // Sale has no reference back to its delivery order, so the order is joined explicitly
    String HISTORY_ROW_FROM = """
            FROM SalePaymentHistory h
            JOIN h.payment p
            JOIN p.sale s
            JOIN s.customer c
            JOIN h.changedBy e
            LEFT JOIN h.previousPaymentMethod pm
            LEFT JOIN h.newPaymentMethod nm
            LEFT JOIN DeliveryOrder d ON d.sale = s
            """;

    String SEARCH_FILTERS = """
            WHERE (:fromUtc IS NULL OR h.changedAt >= :fromUtc)
              AND (:toUtcExclusive IS NULL OR h.changedAt < :toUtcExclusive)
              AND (:employeeId IS NULL OR e.id = :employeeId)
              AND (:customerId IS NULL OR c.id = :customerId)
              AND (:action IS NULL OR h.action = :action)
            """;

    SalePaymentHistory save(SalePaymentHistory history);

    @Query("""
            SELECT DISTINCT h.payment.id FROM SalePaymentHistory h
            WHERE h.payment.id IN :paymentIds
              AND h.action = com.pasadita.api.enums.account.PaymentHistoryAction.MODIFICADO
            """)
    List<Long> findModifiedPaymentIds(@Param("paymentIds") Collection<Long> paymentIds);

    @Query(HISTORY_ROW_SELECT + HISTORY_ROW_FROM + "WHERE p.id = :paymentId ORDER BY h.changedAt ASC, h.id ASC")
    List<PaymentHistoryRow> findRowsByPaymentId(@Param("paymentId") Long paymentId);

    @Query(value = HISTORY_ROW_SELECT + HISTORY_ROW_FROM + SEARCH_FILTERS + "ORDER BY h.changedAt DESC, h.id DESC",
            countQuery = "SELECT COUNT(h) " + HISTORY_ROW_FROM + SEARCH_FILTERS)
    Page<PaymentHistoryRow> search(@Param("fromUtc") LocalDateTime fromUtc,
                                   @Param("toUtcExclusive") LocalDateTime toUtcExclusive,
                                   @Param("employeeId") Long employeeId,
                                   @Param("customerId") Long customerId,
                                   @Param("action") PaymentHistoryAction action,
                                   Pageable pageable);
}
