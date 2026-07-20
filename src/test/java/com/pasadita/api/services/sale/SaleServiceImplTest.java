package com.pasadita.api.services.sale;

import com.pasadita.api.dto.deliveryorder.DeliveryOrderMapper;
import com.pasadita.api.dto.sale.SaleCreateDto;
import com.pasadita.api.dto.sale.SaleMapper;
import com.pasadita.api.dto.saledetail.SaleDetailCreateDto;
import com.pasadita.api.entities.Customer;
import com.pasadita.api.entities.Employee;
import com.pasadita.api.entities.PaymentMethod;
import com.pasadita.api.entities.Product;
import com.pasadita.api.entities.Sale;
import com.pasadita.api.repositories.CustomerRepository;
import com.pasadita.api.repositories.DeliveryOrderRepository;
import com.pasadita.api.repositories.EmployeeRepository;
import com.pasadita.api.repositories.PaymentMethodRepository;
import com.pasadita.api.repositories.ProductRepository;
import com.pasadita.api.repositories.SaleDetailRepository;
import com.pasadita.api.repositories.SaleRepository;
import com.pasadita.api.services.saledetail.SaleDetailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SaleServiceImplTest {

    @Mock private SaleRepository saleRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private PaymentMethodRepository paymentMethodRepository;
    @Mock private ProductRepository productRepository;
    @Mock private DeliveryOrderRepository deliveryOrderRepository;
    @Mock private SaleMapper saleMapper;
    @Mock private DeliveryOrderMapper deliveryOrderMapper;
    @Mock private SaleDetailService saleDetailService;
    @Mock private SaleDetailRepository saleDetailRepository;

    private SaleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SaleServiceImpl(saleRepository, employeeRepository, customerRepository,
                paymentMethodRepository, productRepository, deliveryOrderRepository,
                saleMapper, deliveryOrderMapper, saleDetailService, saleDetailRepository);
    }

    private SaleCreateDto saveSaleWithSingleDetail(String unitPrice, BigDecimal requestedUnitDiscount, String quantity) {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(Employee.builder().id(1L).build()));
        when(customerRepository.findById(2L)).thenReturn(Optional.of(Customer.builder().id(2L).build()));
        when(paymentMethodRepository.findById(1L)).thenReturn(Optional.of(PaymentMethod.builder().id(1L).build()));
        when(productRepository.findById(10L)).thenReturn(Optional.of(
                Product.builder().id(10L).price(new BigDecimal(unitPrice)).build()));

        Sale persistedSale = Sale.builder().id(500L).build();
        when(saleMapper.toEntity(any(SaleCreateDto.class), any(), any(), any())).thenReturn(persistedSale);
        when(saleRepository.save(persistedSale)).thenReturn(persistedSale);

        SaleDetailCreateDto detail = SaleDetailCreateDto.builder()
                .productId(10L)
                .quantity(new BigDecimal(quantity))
                .unitPrice(new BigDecimal("999.99"))
                .subtotal(BigDecimal.ZERO)
                .discount(requestedUnitDiscount)
                .total(BigDecimal.ZERO)
                .build();

        SaleCreateDto dto = SaleCreateDto.builder()
                .employeeId(1L)
                .customerId(2L)
                .paymentMethodId(1L)
                .subtotal(BigDecimal.ZERO)
                .discountAmount(new BigDecimal("999.99"))
                .total(BigDecimal.ZERO)
                .amountTendered(new BigDecimal("100000"))
                .printTicket(false)
                .saleDetails(new ArrayList<>(List.of(detail)))
                .build();

        service.save(dto);
        return dto;
    }

    @Test
    void priceAtLowerBoundOfProtectedRangeForcesZeroDiscount() {
        SaleCreateDto dto = saveSaleWithSingleDetail("1.00", new BigDecimal("0.50"), "3.000");

        SaleDetailCreateDto detail = dto.getSaleDetails().getFirst();
        assertThat(detail.getDiscount()).isEqualByComparingTo("0.00");
        assertThat(detail.getSubtotal()).isEqualByComparingTo("3.00");
        assertThat(detail.getTotal()).isEqualByComparingTo("3.00");
    }

    @Test
    void priceAtUpperBoundOfProtectedRangeForcesZeroDiscount() {
        SaleCreateDto dto = saveSaleWithSingleDetail("10.00", new BigDecimal("3.00"), "1.000");

        SaleDetailCreateDto detail = dto.getSaleDetails().getFirst();
        assertThat(detail.getDiscount()).isEqualByComparingTo("0.00");
        assertThat(detail.getTotal()).isEqualByComparingTo("10.00");
    }

    @Test
    void priceInsideProtectedRangeForcesZeroDiscount() {
        SaleCreateDto dto = saveSaleWithSingleDetail("5.00", new BigDecimal("3.00"), "2.000");

        SaleDetailCreateDto detail = dto.getSaleDetails().getFirst();
        assertThat(detail.getDiscount()).isEqualByComparingTo("0.00");
        assertThat(detail.getSubtotal()).isEqualByComparingTo("10.00");
        assertThat(detail.getTotal()).isEqualByComparingTo("10.00");
    }

    @Test
    void unitDiscountAboveUnitPriceIsCappedSoTotalNeverGoesNegative() {
        SaleCreateDto dto = saveSaleWithSingleDetail("50.00", new BigDecimal("60.00"), "1.000");

        SaleDetailCreateDto detail = dto.getSaleDetails().getFirst();
        assertThat(detail.getDiscount()).isEqualByComparingTo("50.00");
        assertThat(detail.getSubtotal()).isEqualByComparingTo("50.00");
        assertThat(detail.getTotal()).isEqualByComparingTo("0.00");
    }

    @Test
    void validUnitDiscountAccumulatesByQuantityAndDrivesSaleTotals() {
        SaleCreateDto dto = saveSaleWithSingleDetail("50.00", new BigDecimal("5.00"), "2.000");

        SaleDetailCreateDto detail = dto.getSaleDetails().getFirst();
        assertThat(detail.getDiscount()).isEqualByComparingTo("10.00");
        assertThat(detail.getSubtotal()).isEqualByComparingTo("100.00");
        assertThat(detail.getTotal()).isEqualByComparingTo("90.00");

        assertThat(dto.getSubtotal()).isEqualByComparingTo("100.00");
        assertThat(dto.getDiscountAmount()).isEqualByComparingTo("10.00");
        assertThat(dto.getTotal()).isEqualByComparingTo("90.00");
    }

    @Test
    void fractionalQuantityKeepsLineAndSaleTotalsConsistent() {
        SaleCreateDto dto = saveSaleWithSingleDetail("50.00", new BigDecimal("5.00"), "0.333");

        SaleDetailCreateDto detail = dto.getSaleDetails().getFirst();
        assertThat(detail.getSubtotal()).isEqualByComparingTo("16.65");
        assertThat(detail.getDiscount()).isEqualByComparingTo("1.67");
        assertThat(detail.getTotal()).isEqualByComparingTo("14.98");

        assertThat(dto.getSubtotal()).isEqualByComparingTo("16.65");
        assertThat(dto.getDiscountAmount()).isEqualByComparingTo("1.67");
        assertThat(dto.getTotal()).isEqualByComparingTo("14.98");
    }

    @Test
    void negativeRequestedDiscountIsClampedToZero() {
        SaleCreateDto dto = saveSaleWithSingleDetail("50.00", new BigDecimal("-5.00"), "1.000");

        SaleDetailCreateDto detail = dto.getSaleDetails().getFirst();
        assertThat(detail.getDiscount()).isEqualByComparingTo("0.00");
        assertThat(detail.getSubtotal()).isEqualByComparingTo("50.00");
        assertThat(detail.getTotal()).isEqualByComparingTo("50.00");
    }

    @Test
    void nullRequestedDiscountIsTreatedAsZero() {
        SaleCreateDto dto = saveSaleWithSingleDetail("50.00", null, "1.500");
        SaleDetailCreateDto detail = dto.getSaleDetails().getFirst();
        assertThat(detail.getDiscount()).isEqualByComparingTo("0.00");
        assertThat(detail.getSubtotal()).isEqualByComparingTo("75.00");
        assertThat(detail.getTotal()).isEqualByComparingTo("75.00");
    }
}
