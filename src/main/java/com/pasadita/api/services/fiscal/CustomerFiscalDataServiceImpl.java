package com.pasadita.api.services.fiscal;

import com.pasadita.api.dto.fiscal.CustomerFiscalDataCreateDto;
import com.pasadita.api.dto.fiscal.CustomerFiscalDataMapper;
import com.pasadita.api.dto.fiscal.CustomerFiscalDataResponseDto;
import com.pasadita.api.dto.fiscal.CustomerFiscalDataUpdateDto;
import com.pasadita.api.entities.CustomerFiscalData;
import com.pasadita.api.exceptions.BusinessRuleException;
import com.pasadita.api.exceptions.EntityNotFoundException;
import com.pasadita.api.repositories.CustomerFiscalDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class CustomerFiscalDataServiceImpl implements CustomerFiscalDataService {

    private final CustomerFiscalDataRepository repository;
    private final CustomerFiscalDataMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<CustomerFiscalDataResponseDto> findAll() {
        return StreamSupport.stream(repository.findAll().spliterator(), false)
                .map(mapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerFiscalDataResponseDto> findById(Long id) {
        return Optional.of(repository.findById(id)
                .map(mapper::toResponseDto)
                .orElseThrow(() -> new EntityNotFoundException("Customer fiscal data not found with id: " + id)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerFiscalDataResponseDto> findByRfc(String rfc) {
        return Optional.of(repository.findByRfc(rfc)
                .map(mapper::toResponseDto)
                .orElseThrow(() -> new EntityNotFoundException("Customer fiscal data not found with RFC: " + rfc)));
    }

    @Override
    @Transactional
    public Optional<CustomerFiscalDataResponseDto> save(CustomerFiscalDataCreateDto dto) {
        String rfc = dto.getRfc().toUpperCase();
        if (repository.existsByRfc(rfc)) {
            throw new BusinessRuleException("RFC already registered: " + rfc);
        }
        dto.setRfc(rfc);
        CustomerFiscalData entity = mapper.toEntity(dto);
        CustomerFiscalData saved = repository.save(entity);
        return Optional.of(mapper.toResponseDto(saved));
    }

    @Override
    @Transactional
    public Optional<CustomerFiscalDataResponseDto> update(Long id, CustomerFiscalDataUpdateDto dto) {
        CustomerFiscalData existing = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Customer fiscal data not found with id: " + id));

        String newRfc = dto.getRfc().toUpperCase();
        if (!existing.getRfc().equalsIgnoreCase(newRfc) && repository.existsByRfc(newRfc)) {
            throw new BusinessRuleException("RFC already registered: " + newRfc);
        }
        dto.setRfc(newRfc);

        mapper.updateEntity(existing, dto);
        CustomerFiscalData saved = repository.save(existing);
        return Optional.of(mapper.toResponseDto(saved));
    }
}
