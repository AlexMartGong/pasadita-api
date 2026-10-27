package com.pasadita.api.services.deliveryorder;

import com.pasadita.api.dto.deliveryorder.DeliveryOrderChangeStatusDto;
import com.pasadita.api.dto.deliveryorder.DeliveryOrderCreateDto;
import com.pasadita.api.dto.deliveryorder.DeliveryOrderMapper;
import com.pasadita.api.dto.deliveryorder.DeliveryOrderResponseDto;
import com.pasadita.api.entities.Customer;
import com.pasadita.api.entities.DeliveryOrder;
import com.pasadita.api.entities.Employee;
import com.pasadita.api.entities.Sale;
import com.pasadita.api.enums.delivery.DeliveryStatus;
import com.pasadita.api.exceptions.BusinessRuleException;
import com.pasadita.api.repositories.CustomerRepository;
import com.pasadita.api.repositories.DeliveryOrderRepository;
import com.pasadita.api.repositories.EmployeeRepository;
import com.pasadita.api.repositories.SalePaymentRepository;
import com.pasadita.api.repositories.SaleRepository;
import com.pasadita.api.services.account.CustomerAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryOrderServiceImplTest {

    @Mock private DeliveryOrderRepository deliveryOrderRepository;
    @Mock private SaleRepository saleRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private SalePaymentRepository salePaymentRepository;
    @Mock private CustomerAccountService customerAccountService;

    private DeliveryOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DeliveryOrderServiceImpl(deliveryOrderRepository, saleRepository, employeeRepository,
                new DeliveryOrderMapper(), customerRepository, salePaymentRepository, customerAccountService);
    }

    private DeliveryOrder pendingOrder() {
        Customer customer = Customer.builder().id(20L).name("Clienta").build();
        Sale sale = Sale.builder().id(50L).customer(customer).total(new BigDecimal("700.00")).paid(false).build();
        return DeliveryOrder.builder().id(200L).sale(sale).status(DeliveryStatus.PENDIENTE).build();
    }

    @ParameterizedTest
    @EnumSource(value = DeliveryStatus.class, names = {"ACTIVO", "PENDIENTE"})
    void manualChangeToActiveOrPendingIsRejected(DeliveryStatus targetStatus) {
        assertThatThrownBy(() -> service.changeStatus(200L, new DeliveryOrderChangeStatusDto(targetStatus)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("CANCELADO");
        verify(deliveryOrderRepository, never()).save(any());
    }

    @Test
    void cancellingAnOrderWhoseNoteHasPaymentsIsRejected() {
        DeliveryOrder deliveryOrder = pendingOrder();
        when(deliveryOrderRepository.findWithSaleById(200L)).thenReturn(Optional.of(deliveryOrder));
        when(salePaymentRepository.existsBySaleId(50L)).thenReturn(true);

        assertThatThrownBy(() -> service.changeStatus(200L, new DeliveryOrderChangeStatusDto(DeliveryStatus.CANCELADO)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("abonos");
        verify(customerRepository).findByIdForUpdate(20L);
        assertThat(deliveryOrder.getStatus()).isEqualTo(DeliveryStatus.PENDIENTE);
        verify(deliveryOrderRepository, never()).save(any());
    }

    @Test
    void cancellingAnOrderWithoutPaymentsMarksItCancelledAndUnpaid() {
        DeliveryOrder deliveryOrder = pendingOrder();
        when(deliveryOrderRepository.findWithSaleById(200L)).thenReturn(Optional.of(deliveryOrder));
        when(deliveryOrderRepository.save(deliveryOrder)).thenReturn(deliveryOrder);

        Optional<DeliveryOrderResponseDto> response =
                service.changeStatus(200L, new DeliveryOrderChangeStatusDto(DeliveryStatus.CANCELADO));

        assertThat(deliveryOrder.getStatus()).isEqualTo(DeliveryStatus.CANCELADO);
        assertThat(deliveryOrder.getSale().getPaid()).isFalse();
        assertThat(response).hasValueSatisfying(dto -> {
            assertThat(dto.getStatus()).isEqualTo(DeliveryStatus.CANCELADO);
            assertThat(dto.getBalance()).isEqualByComparingTo("0");
        });
    }

    @Test
    void orderForAnUnpaidSaleIsRejected() {
        Sale unpaidSale = Sale.builder().id(50L).customer(Customer.builder().id(20L).build()).paid(false).build();
        when(saleRepository.findById(50L)).thenReturn(Optional.of(unpaidSale));

        assertThatThrownBy(() -> service.save(DeliveryOrderCreateDto.builder().saleId(50L).build(), true))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("venta pagada");
        verifyNoInteractions(customerAccountService);
    }

    @Test
    void newOrderChecksCustomerCreditWithTheCallerRole() {
        Sale paidSale = Sale.builder().id(50L).customer(Customer.builder().id(20L).name("Clienta").build())
                .total(new BigDecimal("390.00")).paid(true).build();
        when(saleRepository.findById(50L)).thenReturn(Optional.of(paidSale));
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(Employee.builder().id(5L).build()));
        when(deliveryOrderRepository.save(any(DeliveryOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.save(DeliveryOrderCreateDto.builder()
                .saleId(50L)
                .deliveryEmployeeId(5L)
                .deliveryAddress("Calle 1")
                .contactPhone("3300000000")
                .build(), false);

        verify(customerAccountService).assertCreditAllowsNewOrder(20L, false);
    }

    @Test
    void creditCheckFailureStopsTheOrder() {
        Sale paidSale = Sale.builder().id(50L).customer(Customer.builder().id(20L).build()).paid(true).build();
        when(saleRepository.findById(50L)).thenReturn(Optional.of(paidSale));
        doThrow(new BusinessRuleException("Se requiere autorización del administrador"))
                .when(customerAccountService).assertCreditAllowsNewOrder(anyLong(), anyBoolean());

        assertThatThrownBy(() -> service.save(DeliveryOrderCreateDto.builder().saleId(50L).build(), false))
                .isInstanceOf(BusinessRuleException.class);
        verify(deliveryOrderRepository, never()).save(any());
    }
}
