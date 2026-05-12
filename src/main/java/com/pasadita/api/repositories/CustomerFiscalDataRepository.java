package com.pasadita.api.repositories;

import com.pasadita.api.entities.CustomerFiscalData;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface CustomerFiscalDataRepository extends CrudRepository<CustomerFiscalData, Long> {

    Optional<CustomerFiscalData> findByRfc(String rfc);

    boolean existsByRfc(String rfc);
}
