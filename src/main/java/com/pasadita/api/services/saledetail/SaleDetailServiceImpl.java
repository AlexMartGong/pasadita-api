package com.pasadita.api.services.saledetail;

import com.pasadita.api.dto.saledetail.SaleDetailCreateDto;
import com.pasadita.api.dto.saledetail.SaleDetailMapper;
import com.pasadita.api.dto.saledetail.SaleDetailResponseDto;
import com.pasadita.api.dto.saledetail.SaleDetailUpdateDto;
import com.pasadita.api.entities.Product;
import com.pasadita.api.entities.Sale;
import com.pasadita.api.entities.SaleDetail;
import com.pasadita.api.repositories.ProductRepository;
import com.pasadita.api.repositories.SaleDetailRepository;
import com.pasadita.api.repositories.SaleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class SaleDetailServiceImpl implements SaleDetailService {

    @Autowired
    private SaleDetailRepository saleDetailRepository;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SaleDetailMapper saleDetailMapper;

    @Transactional(readOnly = true)
    @Override
    public List<SaleDetailResponseDto> findAll() {
        return ((List<SaleDetail>) saleDetailRepository.findAll())
                .stream()
                .map(saleDetailMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<SaleDetailResponseDto> findById(Long id) {
        return saleDetailRepository.findById(id)
                .map(saleDetailMapper::toResponseDto);
    }

    @Transactional(readOnly = true)
    @Override
    public List<SaleDetailResponseDto> findBySaleId(Long saleId) {
        return saleDetailRepository.findBySaleId(saleId)
                .stream()
                .map(saleDetailMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Override
    public List<SaleDetailResponseDto> findByProductId(Long productId) {
        return saleDetailRepository.findByProductId(productId)
                .stream()
                .map(saleDetailMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional
    @Override
    public Optional<SaleDetailResponseDto> save(SaleDetailCreateDto saleDetailDto) {
        Optional<Sale> saleOpt = saleRepository.findById(saleDetailDto.getSaleId());
        Optional<Product> productOpt = productRepository.findById(saleDetailDto.getProductId());

        if (saleOpt.isPresent() && productOpt.isPresent()) {
            SaleDetail saleDetail = saleDetailMapper.toEntity(saleDetailDto, saleOpt.get(), productOpt.get());
            SaleDetail savedSaleDetail = saleDetailRepository.save(saleDetail);
            return Optional.of(saleDetailMapper.toResponseDto(savedSaleDetail));
        }
        return Optional.empty();
    }

    @Transactional
    @Override
    public Optional<SaleDetailResponseDto> update(Long id, SaleDetailUpdateDto saleDetailUpdateDto) {
        return saleDetailRepository.findById(id)
                .map(existingSaleDetail -> {
                    Product product = null;
                    if (saleDetailUpdateDto.getProductId() != null) {
                        product = productRepository.findById(saleDetailUpdateDto.getProductId()).orElse(null);
                    }
                    saleDetailMapper.updateEntity(existingSaleDetail, saleDetailUpdateDto, product);
                    SaleDetail updatedSaleDetail = saleDetailRepository.save(existingSaleDetail);
                    return saleDetailMapper.toResponseDto(updatedSaleDetail);
                });
    }

    @Transactional
    @Override
    public void deleteById(Long id) {
        saleDetailRepository.deleteById(id);
    }
}
