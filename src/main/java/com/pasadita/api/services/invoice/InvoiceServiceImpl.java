package com.pasadita.api.services.invoice;

import com.pasadita.api.config.FacturacionProperties;
import com.pasadita.api.dto.invoice.InvoiceCreateDto;
import com.pasadita.api.dto.invoice.InvoiceMapper;
import com.pasadita.api.dto.invoice.InvoiceResponseDto;
import com.pasadita.api.entities.CustomerFiscalData;
import com.pasadita.api.entities.Invoice;
import com.pasadita.api.entities.Sale;
import com.pasadita.api.enums.invoice.InvoiceStatus;
import com.pasadita.api.exceptions.BusinessRuleException;
import com.pasadita.api.exceptions.EntityNotFoundException;
import com.pasadita.api.repositories.CustomerFiscalDataRepository;
import com.pasadita.api.repositories.InvoiceRepository;
import com.pasadita.api.repositories.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

    private static final Set<InvoiceStatus> BLOCKING_STATUSES =
            Set.of(InvoiceStatus.PENDIENTE, InvoiceStatus.TIMBRADA);

    private final InvoiceRepository invoiceRepository;
    private final SaleRepository saleRepository;
    private final CustomerFiscalDataRepository fiscalDataRepository;
    private final InvoiceMapper invoiceMapper;
    private final FacturacionProperties facturacionProperties;

    @Override
    @Transactional
    public Optional<InvoiceResponseDto> createInvoiceRequest(InvoiceCreateDto dto) {
        Sale sale = saleRepository.findById(dto.getSaleId())
                .orElseThrow(() -> new EntityNotFoundException("Sale not found with id: " + dto.getSaleId()));

        if (Boolean.FALSE.equals(sale.getPaid())) {
            throw new BusinessRuleException("Cannot invoice an unpaid sale");
        }

        CustomerFiscalData fiscalData = fiscalDataRepository.findById(dto.getFiscalId())
                .orElseThrow(() -> new EntityNotFoundException("Customer fiscal data not found with id: " + dto.getFiscalId()));

        if (Boolean.FALSE.equals(fiscalData.getActive())) {
            throw new BusinessRuleException("Customer fiscal data is inactive: " + fiscalData.getRfc());
        }

        if (invoiceRepository.existsBySaleIdAndStatusIn(sale.getId(), BLOCKING_STATUSES)) {
            throw new BusinessRuleException("Sale already has an invoice in progress or stamped");
        }

        Invoice invoice = invoiceMapper.toEntity(dto, sale, fiscalData);
        Invoice saved = invoiceRepository.save(invoice);
        return Optional.of(invoiceMapper.toResponseDto(saved));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InvoiceResponseDto> getInvoiceBySaleId(Long saleId) {
        return Optional.of(invoiceRepository.findBySaleId(saleId)
                .map(invoiceMapper::toResponseDto)
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found for sale id: " + saleId)));
    }
}
