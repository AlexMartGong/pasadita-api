package com.pasadita.api.dto.sale;

import com.pasadita.api.entities.Sale;
import com.pasadita.api.entities.Employee;
import com.pasadita.api.entities.Customer;
import com.pasadita.api.entities.PaymentMethod;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class SaleMapper {

    public Sale toEntity(SaleCreateDto dto, Employee employee, Customer customer, PaymentMethod paymentMethod) {
        return Sale.builder()
                .employee(employee)
                .customer(customer)
                .paymentMethod(paymentMethod)
                .datetime(LocalDateTime.now())
                .subtotal(dto.getSubtotal())
                .discountAmount(dto.getDiscountAmount())
                .total(dto.getTotal())
                .paid(dto.getPaid())
                .notes(dto.getNotes())
                .build();
    }

    public void updateEntity(Sale sale, SaleUpdateDto dto, Employee employee, Customer customer, PaymentMethod paymentMethod) {
        sale.setEmployee(employee);
        sale.setCustomer(customer);
        sale.setPaymentMethod(paymentMethod);
        sale.setDatetime(LocalDateTime.now());
        sale.setSubtotal(dto.getSubtotal());
        sale.setDiscountAmount(dto.getDiscountAmount());
        sale.setTotal(dto.getTotal());
        sale.setPaid(dto.getPaid());
        sale.setNotes(dto.getNotes());
    }

    public SaleResponseDto toResponseDto(Sale sale) {
        return SaleResponseDto.builder()
                .id(sale.getId())
                .employeeId(sale.getEmployee() != null ? sale.getEmployee().getId() : null)
                .employeeName(sale.getEmployee() != null ? sale.getEmployee().getFullName() : null)
                .customerId(sale.getCustomer() != null ? sale.getCustomer().getId() : null)
                .customerName(sale.getCustomer() != null ? sale.getCustomer().getName() : null)
                .paymentMethodId(sale.getPaymentMethod() != null ? sale.getPaymentMethod().getId() : null)
                .paymentMethodName(sale.getPaymentMethod() != null ? sale.getPaymentMethod().getName() : null)
                .datetime(sale.getDatetime())
                .subtotal(sale.getSubtotal())
                .discountAmount(sale.getDiscountAmount())
                .total(sale.getTotal())
                .paid(sale.getPaid())
                .notes(sale.getNotes())
                .build();
    }
}
