package com.pasadita.api.repositories;

import com.pasadita.api.entities.PaymentMethod;
import org.springframework.data.repository.CrudRepository;

public interface PaymentMethodRepository extends CrudRepository<PaymentMethod, Long> {
}