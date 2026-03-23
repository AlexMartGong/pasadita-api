package com.pasadita.api.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsDto {

    private FinancialSummaryDto financialSummary;
    private ProductAnalysisDto productAnalysis;
    private OperationsDto operations;
    private CustomerAnalysisDto customerAnalysis;
    private TimeAnalysisDto timeAnalysis;
    private FinancialHealthDto financialHealth;
    private BasketAnalysisDto basketAnalysis;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinancialSummaryDto {
        private BigDecimal totalSales;
        private BigDecimal averageTicket;
        private BigDecimal totalDiscounts;
        private BigDecimal deliveryRevenue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductAnalysisDto {
        private List<TopProductDto> topProducts;
        private List<CategorySalesDto> categorySales;
        private List<DeadProductDto> deadProducts;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopProductDto {
        private Long productId;
        private String productName;
        private String category;
        private BigDecimal totalQuantitySold;
        private BigDecimal totalRevenue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategorySalesDto {
        private String category;
        private BigDecimal totalQuantity;
        private BigDecimal totalRevenue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeadProductDto {
        private Long productId;
        private String productName;
        private String category;
        private BigDecimal price;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OperationsDto {
        private List<CashierRankingDto> cashierRanking;
        private List<DeliveryRankingDto> deliveryRanking;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CashierRankingDto {
        private Long employeeId;
        private String employeeName;
        private Long salesCount;
        private BigDecimal totalSold;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliveryRankingDto {
        private Long employeeId;
        private String employeeName;
        private Long deliveriesCompleted;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerAnalysisDto {
        private List<VipCustomerDto> vipCustomers;
        private List<CustomerTypeSalesDto> customerTypeSales;
        private RegisteredVsAnonymousDto registeredVsAnonymous;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VipCustomerDto {
        private Long customerId;
        private String customerName;
        private Long salesCount;
        private BigDecimal totalPurchased;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerTypeSalesDto {
        private String customerTypeName;
        private Long salesCount;
        private BigDecimal totalRevenue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegisteredVsAnonymousDto {
        private Long registeredSalesCount;
        private BigDecimal registeredSalesTotal;
        private Long anonymousSalesCount;
        private BigDecimal anonymousSalesTotal;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeAnalysisDto {
        private List<HeatmapCellDto> heatmap;
        private List<HourlyAverageDto> hourlyAverages;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HeatmapCellDto {
        private Integer dayOfWeek;
        private Integer hour;
        private Long salesCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HourlyAverageDto {
        private Integer hour;
        private BigDecimal averageTicket;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinancialHealthDto {
        private BigDecimal totalDebt;
        private List<UnpaidSaleDto> unpaidSales;
        private DiscountImpactDto discountImpact;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnpaidSaleDto {
        private Long saleId;
        private LocalDateTime saleDate;
        private BigDecimal total;
        private String customerName;
        private String employeeName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiscountImpactDto {
        private BigDecimal grossSales;
        private BigDecimal totalDiscounts;
        private BigDecimal netSales;
        private BigDecimal discountPercentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BasketAnalysisDto {
        private BigDecimal averageItemsPerSale;
    }
}
