package com.pasadita.api.services.sale;

import com.pasadita.api.dto.deliveryorder.DeliveryOrderEmbeddedDto;
import com.pasadita.api.dto.deliveryorder.DeliveryOrderMapper;
import com.pasadita.api.dto.sale.SaleChangeStatusDto;
import com.pasadita.api.dto.sale.SaleCreateDto;
import com.pasadita.api.dto.sale.SaleMapper;
import com.pasadita.api.dto.sale.SaleResponseDto;
import com.pasadita.api.dto.sale.SaleUpdateDto;
import com.pasadita.api.dto.saledetail.SaleDetailResponseDto;
import com.pasadita.api.dto.ticket.TicketResponseDto;
import com.pasadita.api.entities.*;
import com.pasadita.api.repositories.CustomerRepository;
import com.pasadita.api.repositories.DeliveryOrderRepository;
import com.pasadita.api.repositories.EmployeeRepository;
import com.pasadita.api.repositories.PaymentMethodRepository;
import com.pasadita.api.repositories.ProductRepository;
import com.pasadita.api.repositories.SaleRepository;
import com.pasadita.api.repositories.SaleDetailRepository;
import com.pasadita.api.services.saledetail.SaleDetailService;
import com.pasadita.api.exceptions.BusinessRuleException;
import com.pasadita.api.exceptions.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class SaleServiceImpl implements SaleService {

    private static final BigDecimal NO_DISCOUNT_PRICE_LOWER_BOUND = BigDecimal.ONE;
    private static final BigDecimal NO_DISCOUNT_PRICE_UPPER_BOUND = new BigDecimal("10");

    private final SaleRepository saleRepository;
    private final EmployeeRepository employeeRepository;
    private final CustomerRepository customerRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final ProductRepository productRepository;
    private final DeliveryOrderRepository deliveryOrderRepository;
    private final SaleMapper saleMapper;
    private final DeliveryOrderMapper deliveryOrderMapper;
    private final SaleDetailService saleDetailService;
    private final SaleDetailRepository saleDetailRepository;

    @Override
    @Transactional(readOnly = true)
    public List<SaleResponseDto> findAll() {
        List<Sale> sales = saleRepository.findAllByOrderByIdDesc();
        return sales.stream()
                .map(saleMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<SaleResponseDto> save(SaleCreateDto saleCreateDto) {
        Employee employee = findEmployeeById(saleCreateDto.getEmployeeId());
        Customer customer = findCustomerById(saleCreateDto.getCustomerId());
        PaymentMethod paymentMethod = findPaymentMethodById(saleCreateDto.getPaymentMethodId());
        BigDecimal saleSubtotal = BigDecimal.ZERO;
        BigDecimal saleDiscountTotal = BigDecimal.ZERO;
        BigDecimal saleTotal = BigDecimal.ZERO;

        for (var detailDto : saleCreateDto.getSaleDetails()) {
            Product product = productRepository.findById(detailDto.getProductId())
                    .orElseThrow(() -> new EntityNotFoundException("Product not found with id: " + detailDto.getProductId()));

            BigDecimal unitPrice = product.getPrice();
            BigDecimal appliedDiscount = resolveApplicableUnitDiscount(unitPrice, detailDto.getDiscount());
            BigDecimal quantity = detailDto.getQuantity();

            BigDecimal detailSubtotal = unitPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
            BigDecimal detailDiscount = appliedDiscount.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
            BigDecimal detailTotal = detailSubtotal.subtract(detailDiscount);

            detailDto.setUnitPrice(unitPrice);
            detailDto.setDiscount(detailDiscount);
            detailDto.setSubtotal(detailSubtotal);
            detailDto.setTotal(detailTotal);

            saleSubtotal = saleSubtotal.add(detailSubtotal);
            saleDiscountTotal = saleDiscountTotal.add(detailDiscount);
            saleTotal = saleTotal.add(detailTotal);
        }

        saleSubtotal = saleSubtotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal saleDiscount = saleDiscountTotal.setScale(2, RoundingMode.HALF_UP);
        saleTotal = saleTotal.setScale(2, RoundingMode.HALF_UP);

        saleCreateDto.setSubtotal(saleSubtotal);
        saleCreateDto.setDiscountAmount(saleDiscount);
        saleCreateDto.setTotal(saleTotal);

        if (saleCreateDto.getAmountTendered().compareTo(saleTotal) < 0) {
            throw new BusinessRuleException("The amount tendered must be greater than or equal to the total");
        }

        Sale sale = saleMapper.toEntity(saleCreateDto, employee, customer, paymentMethod);
        Sale savedSale = saleRepository.save(sale);

        saleCreateDto.getSaleDetails().forEach(saleDetailDto -> {
            saleDetailDto.setSaleId(savedSale.getId());
            saleDetailService.save(saleDetailDto);
        });

        DeliveryOrderEmbeddedDto deliveryOrderDto = saleCreateDto.getDeliveryOrder();
        if (deliveryOrderDto != null) {
            Employee deliveryEmployee = findEmployeeById(deliveryOrderDto.getDeliveryEmployeeId());
            DeliveryOrder deliveryOrder = deliveryOrderMapper.toEntity(deliveryOrderDto, savedSale, deliveryEmployee);
            deliveryOrderRepository.save(deliveryOrder);
        }

        return Optional.ofNullable(saleMapper.toResponseDto(savedSale));
    }

    private BigDecimal resolveApplicableUnitDiscount(BigDecimal unitPrice, BigDecimal requestedUnitDiscount) {
        BigDecimal requested = requestedUnitDiscount != null ? requestedUnitDiscount : BigDecimal.ZERO;
        boolean isInNoDiscountRange = unitPrice.compareTo(NO_DISCOUNT_PRICE_LOWER_BOUND) >= 0
                && unitPrice.compareTo(NO_DISCOUNT_PRICE_UPPER_BOUND) <= 0;
        if (isInNoDiscountRange) {
            return BigDecimal.ZERO;
        }
        return requested.max(BigDecimal.ZERO).min(unitPrice);
    }

    @Override
    public Optional<SaleResponseDto> update(Long id, SaleUpdateDto saleUpdateDto) {
        Sale existingSale = saleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Sale not found with id: " + id));

        Employee employee = findEmployeeById(saleUpdateDto.getEmployeeId());
        Customer customer = findCustomerById(saleUpdateDto.getCustomerId());
        PaymentMethod paymentMethod = findPaymentMethodById(saleUpdateDto.getPaymentMethodId());

        saleMapper.updateEntity(existingSale, saleUpdateDto, employee, customer, paymentMethod);
        Sale updatedSale = saleRepository.save(existingSale);

        saleDetailRepository.deleteBySaleId(existingSale.getId());

        saleUpdateDto.getSaleDetails().forEach(saleDetailDto -> {
            saleDetailDto.setSaleId(updatedSale.getId());
            saleDetailService.save(saleDetailDto);
        });

        return Optional.ofNullable(saleMapper.toResponseDto(updatedSale));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SaleDetailResponseDto> getSaleDetails(Long saleId) {
        List<SaleDetail> saleDetails = saleDetailRepository.findBySaleIdOrderById(saleId);

        return saleDetails.stream()
                .map(sd -> SaleDetailResponseDto.builder()
                        .detailId(sd.getId())
                        .saleId(sd.getSale().getId())
                        .saleDate(sd.getSale().getDatetime())
                        .productId(sd.getProduct().getId())
                        .productName(sd.getProduct().getName())
                        .productCategory(sd.getProduct().getCategory().name())
                        .quantity(sd.getQuantity())
                        .unitPrice(sd.getUnitPrice())
                        .discount(sd.getDiscount())
                        .subtotal(sd.getSubtotal())
                        .total(sd.getTotal())
                        .build())
                .collect(Collectors.toList());
    }

    private Employee findEmployeeById(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EntityNotFoundException("Employee not found with id: " + employeeId));
    }

    @Override
    public Optional<SaleResponseDto> changeStatus(Long id, SaleChangeStatusDto changeStatusDto) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Sale not found with id: " + id));

        sale.setPaid(changeStatusDto.getPaid());
        Sale updatedSale = saleRepository.save(sale);

        return Optional.ofNullable(saleMapper.toResponseDto(updatedSale));
    }

    private Customer findCustomerById(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found with id: " + customerId));
    }

    private PaymentMethod findPaymentMethodById(Long paymentMethodId) {
        return paymentMethodRepository.findById(paymentMethodId)
                .orElseThrow(() -> new EntityNotFoundException("Payment method not found with id: " + paymentMethodId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TicketResponseDto> getTicket(Long saleId) {
        Optional<Sale> saleOptional = saleRepository.findWithDetailsById(saleId);

        if (saleOptional.isEmpty()) {
            return Optional.empty();
        }

        Sale sale = saleOptional.get();
        DeliveryOrder deliveryOrder = deliveryOrderRepository.findBySaleId(saleId).orElse(null);

        return Optional.of(saleMapper.toTicketResponseDto(sale, deliveryOrder));
    }
}
