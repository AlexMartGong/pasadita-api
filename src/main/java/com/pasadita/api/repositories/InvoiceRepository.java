package com.pasadita.api.repositories;

import com.pasadita.api.entities.Invoice;
import com.pasadita.api.enums.invoice.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    @EntityGraph(attributePaths = {"sale", "customerFiscalData"})
    Optional<Invoice> findBySaleId(Long saleId);

    @EntityGraph(attributePaths = {"sale", "customerFiscalData"})
    Optional<Invoice> findWithDetailsByInvoiceId(Long invoiceId);

    @Override
    @EntityGraph(attributePaths = {"sale", "customerFiscalData"})
    Page<Invoice> findAll(Pageable pageable);

    boolean existsBySaleIdAndStatusIn(Long saleId, Collection<InvoiceStatus> statuses);
}
