package com.pasadita.api.repositories;

import com.pasadita.api.dto.account.CustomerDebtView;
import com.pasadita.api.entities.Customer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends CrudRepository<Customer, Long> {

    // Serializes every operation that applies payments or checks credit for the same customer
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Customer c WHERE c.id = :id")
    Optional<Customer> findByIdForUpdate(@Param("id") Long id);

    // Active payments are pre-aggregated per sale so a note with several payments doesn't count its total twice
    @Query(nativeQuery = true, value = """
            SELECT c.customer_id AS customerId, c.name AS name, c.phone AS phone,
                   c.credit_limit AS creditLimit,
                   SUM(s.total - COALESCE(p.paid_amount, 0)) AS totalDebt,
                   COUNT(s.sale_id) AS pendingNotes
            FROM deliveryorders d
            JOIN sales s ON s.sale_id = d.sale_id
            JOIN customers c ON c.customer_id = s.customer_id
            LEFT JOIN (SELECT sale_id, SUM(amount) AS paid_amount
                       FROM sale_payments
                       WHERE status = 'APLICADO'
                       GROUP BY sale_id) p ON p.sale_id = s.sale_id
            WHERE d.status = 'PENDIENTE'
            GROUP BY c.customer_id, c.name, c.phone, c.credit_limit
            HAVING totalDebt > 0
            ORDER BY totalDebt DESC
            """)
    List<CustomerDebtView> findCustomersWithDebt();
}
