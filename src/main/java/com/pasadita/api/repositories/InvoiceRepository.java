package com.pasadita.api.repositories;

import com.pasadita.api.entities.Invoice;
import com.pasadita.api.enums.invoice.InvoiceStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.CrudRepository;

import java.util.Collection;
import java.util.Optional;

public interface InvoiceRepository extends CrudRepository<Invoice, Long> {

    @EntityGraph(attributePaths = {"sale", "customerFiscalData"})
    Optional<Invoice> findBySaleId(Long saleId);

    @EntityGraph(attributePaths = {"sale", "customerFiscalData"})
    Optional<Invoice> findWithDetailsByInvoiceId(Long invoiceId);

    boolean existsBySaleIdAndStatusIn(Long saleId, Collection<InvoiceStatus> statuses);
}
