package com.pasadita.api.repositories;

import com.pasadita.api.entities.CustomerType;
import org.springframework.data.repository.CrudRepository;

public interface CustomerTypeRepository extends CrudRepository<CustomerType, Long> {
}
