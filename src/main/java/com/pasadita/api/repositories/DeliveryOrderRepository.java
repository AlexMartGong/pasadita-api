package com.pasadita.api.repositories;

import com.pasadita.api.entities.DeliveryOrder;
import org.springframework.data.repository.CrudRepository;

public interface DeliveryOrderRepository extends CrudRepository<DeliveryOrder, Long> {
}
