package com.pasadita.api.repositories;

import com.pasadita.api.entities.Invoice;
import com.pasadita.api.enums.invoice.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    @EntityGraph(attributePaths = {"sale", "customerFiscalData"})
    Optional<Invoice> findBySaleId(Long saleId);

    @EntityGraph(attributePaths = {"sale", "customerFiscalData"})
    Optional<Invoice> findWithDetailsByInvoiceId(Long invoiceId);

    @Override
    @EntityGraph(attributePaths = {"sale", "customerFiscalData"})
    Page<Invoice> findAll(Pageable pageable);

    boolean existsBySaleIdAndStatus(Long saleId, InvoiceStatus status);

    @Query("SELECT i.sale.id FROM Invoice i WHERE i.sale.id IN :saleIds AND i.status = :status")
    List<Long> findSaleIdsByStatus(@Param("saleIds") Collection<Long> saleIds, @Param("status") InvoiceStatus status);
}
