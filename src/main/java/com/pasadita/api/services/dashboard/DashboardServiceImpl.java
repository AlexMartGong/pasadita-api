package com.pasadita.api.services.dashboard;

import com.pasadita.api.dto.dashboard.DashboardStatsDto;
import com.pasadita.api.dto.dashboard.DashboardStatsDto.*;
import com.pasadita.api.repositories.DashboardRepository;
import com.pasadita.api.utils.DateTimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private final DashboardRepository dashboardRepository;

    @Override
    public DashboardStatsDto getStats(LocalDateTime startDate, LocalDateTime endDate) {
        return DashboardStatsDto.builder()
                .financialSummary(buildFinancialSummary(startDate, endDate))
                .productAnalysis(buildProductAnalysis(startDate, endDate))
                .operations(buildOperations(startDate, endDate))
                .customerAnalysis(buildCustomerAnalysis(startDate, endDate))
                .timeAnalysis(buildTimeAnalysis(startDate, endDate))
                .financialHealth(buildFinancialHealth(startDate, endDate))
                .basketAnalysis(buildBasketAnalysis(startDate, endDate))
                .build();
    }

    private FinancialSummaryDto buildFinancialSummary(LocalDateTime startDate, LocalDateTime endDate) {
        List<Object[]> rows = dashboardRepository.findFinancialSummary(startDate, endDate);
        if (rows.isEmpty()) {
            return FinancialSummaryDto.builder()
                    .totalSales(BigDecimal.ZERO)
                    .averageTicket(BigDecimal.ZERO)
                    .totalDiscounts(BigDecimal.ZERO)
                    .deliveryRevenue(BigDecimal.ZERO)
                    .build();
        }
        Object[] row = rows.get(0);
        return FinancialSummaryDto.builder()
                .totalSales(toBigDecimal(row[0]))
                .averageTicket(toBigDecimal(row[1]))
                .totalDiscounts(toBigDecimal(row[2]))
                .deliveryRevenue(toBigDecimal(row[3]))
                .build();
    }

    private ProductAnalysisDto buildProductAnalysis(LocalDateTime startDate, LocalDateTime endDate) {
        return ProductAnalysisDto.builder()
                .topProducts(mapTopProducts(dashboardRepository.findTopProducts(startDate, endDate)))
                .categorySales(mapCategorySales(dashboardRepository.findSalesByCategory(startDate, endDate)))
                .deadProducts(mapDeadProducts(dashboardRepository.findDeadProducts(startDate, endDate)))
                .build();
    }

    private OperationsDto buildOperations(LocalDateTime startDate, LocalDateTime endDate) {
        return OperationsDto.builder()
                .cashierRanking(mapCashierRanking(dashboardRepository.findCashierRanking(startDate, endDate)))
                .deliveryRanking(mapDeliveryRanking(dashboardRepository.findDeliveryRanking(startDate, endDate)))
                .build();
    }

    private CustomerAnalysisDto buildCustomerAnalysis(LocalDateTime startDate, LocalDateTime endDate) {
        return CustomerAnalysisDto.builder()
                .vipCustomers(mapVipCustomers(dashboardRepository.findTopVipCustomers(startDate, endDate)))
                .customerTypeSales(mapCustomerTypeSales(dashboardRepository.findSalesByCustomerType(startDate, endDate)))
                .registeredVsAnonymous(mapRegisteredVsAnonymous(dashboardRepository.findRegisteredVsAnonymous(startDate, endDate)))
                .build();
    }

    private TimeAnalysisDto buildTimeAnalysis(LocalDateTime startDate, LocalDateTime endDate) {
        return TimeAnalysisDto.builder()
                .heatmap(mapHeatmap(dashboardRepository.findSalesHeatmap(startDate, endDate)))
                .hourlyAverages(mapHourlyAverages(dashboardRepository.findAverageTicketByHour(startDate, endDate)))
                .build();
    }

    private FinancialHealthDto buildFinancialHealth(LocalDateTime startDate, LocalDateTime endDate) {
        BigDecimal totalDebt = dashboardRepository.findTotalDebt(startDate, endDate);

        List<Object[]> unpaidRows = dashboardRepository.findOldestUnpaidSales(startDate, endDate);
        List<UnpaidSaleDto> unpaidSales = unpaidRows.stream().map(row -> UnpaidSaleDto.builder()
                .saleId(toLong(row[0]))
                .saleDate(DateTimeUtils.toMexicoTime(toLocalDateTime(row[1])))
                .total(toBigDecimal(row[2]))
                .customerName((String) row[3])
                .employeeName((String) row[4])
                .build()
        ).toList();

        List<Object[]> discountRows = dashboardRepository.findDiscountImpact(startDate, endDate);
        DiscountImpactDto discountImpact;
        if (discountRows.isEmpty()) {
            discountImpact = DiscountImpactDto.builder()
                    .grossSales(BigDecimal.ZERO)
                    .totalDiscounts(BigDecimal.ZERO)
                    .netSales(BigDecimal.ZERO)
                    .discountPercentage(BigDecimal.ZERO)
                    .build();
        } else {
            Object[] row = discountRows.get(0);
            BigDecimal gross = toBigDecimal(row[0]);
            BigDecimal discounts = toBigDecimal(row[1]);
            BigDecimal net = toBigDecimal(row[2]);
            BigDecimal percentage = gross.compareTo(BigDecimal.ZERO) > 0
                    ? discounts.multiply(BigDecimal.valueOf(100)).divide(gross, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            discountImpact = DiscountImpactDto.builder()
                    .grossSales(gross)
                    .totalDiscounts(discounts)
                    .netSales(net)
                    .discountPercentage(percentage)
                    .build();
        }

        return FinancialHealthDto.builder()
                .totalDebt(totalDebt != null ? totalDebt : BigDecimal.ZERO)
                .unpaidSales(unpaidSales)
                .discountImpact(discountImpact)
                .build();
    }

    private BasketAnalysisDto buildBasketAnalysis(LocalDateTime startDate, LocalDateTime endDate) {
        BigDecimal avg = dashboardRepository.findAverageItemsPerSale(startDate, endDate);
        return BasketAnalysisDto.builder()
                .averageItemsPerSale(avg != null ? avg : BigDecimal.ZERO)
                .build();
    }

    // --- Mapping helpers ---

    private List<TopProductDto> mapTopProducts(List<Object[]> rows) {
        return rows.stream().map(row -> TopProductDto.builder()
                .productId(toLong(row[0]))
                .productName((String) row[1])
                .category((String) row[2])
                .totalQuantitySold(toBigDecimal(row[3]))
                .totalRevenue(toBigDecimal(row[4]))
                .build()
        ).toList();
    }

    private List<CategorySalesDto> mapCategorySales(List<Object[]> rows) {
        return rows.stream().map(row -> CategorySalesDto.builder()
                .category((String) row[0])
                .totalQuantity(toBigDecimal(row[1]))
                .totalRevenue(toBigDecimal(row[2]))
                .build()
        ).toList();
    }

    private List<DeadProductDto> mapDeadProducts(List<Object[]> rows) {
        return rows.stream().map(row -> DeadProductDto.builder()
                .productId(toLong(row[0]))
                .productName((String) row[1])
                .category((String) row[2])
                .price(toBigDecimal(row[3]))
                .build()
        ).toList();
    }

    private List<CashierRankingDto> mapCashierRanking(List<Object[]> rows) {
        return rows.stream().map(row -> CashierRankingDto.builder()
                .employeeId(toLong(row[0]))
                .employeeName((String) row[1])
                .salesCount(toLong(row[2]))
                .totalSold(toBigDecimal(row[3]))
                .build()
        ).toList();
    }

    private List<DeliveryRankingDto> mapDeliveryRanking(List<Object[]> rows) {
        return rows.stream().map(row -> DeliveryRankingDto.builder()
                .employeeId(toLong(row[0]))
                .employeeName((String) row[1])
                .deliveriesCompleted(toLong(row[2]))
                .build()
        ).toList();
    }

    private List<VipCustomerDto> mapVipCustomers(List<Object[]> rows) {
        return rows.stream().map(row -> VipCustomerDto.builder()
                .customerId(toLong(row[0]))
                .customerName((String) row[1])
                .salesCount(toLong(row[2]))
                .totalPurchased(toBigDecimal(row[3]))
                .build()
        ).toList();
    }

    private List<CustomerTypeSalesDto> mapCustomerTypeSales(List<Object[]> rows) {
        return rows.stream().map(row -> CustomerTypeSalesDto.builder()
                .customerTypeName((String) row[0])
                .salesCount(toLong(row[1]))
                .totalRevenue(toBigDecimal(row[2]))
                .build()
        ).toList();
    }

    private RegisteredVsAnonymousDto mapRegisteredVsAnonymous(List<Object[]> rows) {
        if (rows.isEmpty()) {
            return RegisteredVsAnonymousDto.builder()
                    .registeredSalesCount(0L)
                    .registeredSalesTotal(BigDecimal.ZERO)
                    .anonymousSalesCount(0L)
                    .anonymousSalesTotal(BigDecimal.ZERO)
                    .build();
        }
        Object[] row = rows.get(0);
        return RegisteredVsAnonymousDto.builder()
                .registeredSalesCount(toLong(row[0]))
                .registeredSalesTotal(toBigDecimal(row[1]))
                .anonymousSalesCount(toLong(row[2]))
                .anonymousSalesTotal(toBigDecimal(row[3]))
                .build();
    }

    private List<HeatmapCellDto> mapHeatmap(List<Object[]> rows) {
        return rows.stream().map(row -> HeatmapCellDto.builder()
                .dayOfWeek(toInt(row[0]))
                .hour(toInt(row[1]))
                .salesCount(toLong(row[2]))
                .build()
        ).toList();
    }

    private List<HourlyAverageDto> mapHourlyAverages(List<Object[]> rows) {
        return rows.stream().map(row -> HourlyAverageDto.builder()
                .hour(toInt(row[0]))
                .averageTicket(toBigDecimal(row[1]))
                .build()
        ).toList();
    }

    // --- Type conversion helpers ---

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }

    private Long toLong(Object value) {
        if (value == null) return 0L;
        return ((Number) value).longValue();
    }

    private Integer toInt(Object value) {
        if (value == null) return 0;
        return ((Number) value).intValue();
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime ldt) return ldt;
        if (value instanceof Timestamp ts) return ts.toLocalDateTime();
        return LocalDateTime.parse(value.toString());
    }
}
