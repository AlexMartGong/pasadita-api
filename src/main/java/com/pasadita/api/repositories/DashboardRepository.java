package com.pasadita.api.repositories;

import com.pasadita.api.entities.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface DashboardRepository extends JpaRepository<Sale, Long> {

    // Q1 - Financial Summary: totalSales, averageTicket, totalDiscounts, deliveryRevenue
    @Query(nativeQuery = true, value = """
            SELECT
                COALESCE(SUM(s.total), 0) AS totalSales,
                COALESCE(AVG(s.total), 0) AS averageTicket,
                COALESCE(SUM(s.discount_amount), 0) + COALESCE((
                    SELECT SUM(sd.discount)
                    FROM saledetails sd
                    JOIN sales s2 ON sd.sale_id = s2.sale_id
                    WHERE s2.datetime BETWEEN :startDate AND :endDate
                ), 0) AS totalDiscounts,
                COALESCE((
                    SELECT SUM(d.delivery_cost)
                    FROM deliveryorders d
                    JOIN sales s3 ON d.sale_id = s3.sale_id
                    WHERE s3.datetime BETWEEN :startDate AND :endDate
                ), 0) AS deliveryRevenue
            FROM sales s
            WHERE s.datetime BETWEEN :startDate AND :endDate
            """)
    List<Object[]> findFinancialSummary(@Param("startDate") LocalDateTime startDate,
                                        @Param("endDate") LocalDateTime endDate);

    // Q2 - Top Products by quantity sold
    @Query(nativeQuery = true, value = """
            SELECT p.product_id, p.name, p.category,
                   SUM(sd.quantity) AS totalQuantitySold,
                   SUM(sd.total) AS totalRevenue
            FROM saledetails sd
            JOIN products p ON sd.product_id = p.product_id
            JOIN sales s ON sd.sale_id = s.sale_id
            WHERE s.datetime BETWEEN :startDate AND :endDate
            GROUP BY p.product_id, p.name, p.category
            ORDER BY totalQuantitySold DESC
            LIMIT 5
            """)
    List<Object[]> findTopProducts(@Param("startDate") LocalDateTime startDate,
                                   @Param("endDate") LocalDateTime endDate);

    // Q3 - Sales by Category
    @Query(nativeQuery = true, value = """
            SELECT p.category,
                   SUM(sd.quantity) AS totalQuantity,
                   SUM(sd.total) AS totalRevenue
            FROM saledetails sd
            JOIN products p ON sd.product_id = p.product_id
            JOIN sales s ON sd.sale_id = s.sale_id
            WHERE s.datetime BETWEEN :startDate AND :endDate
            GROUP BY p.category
            """)
    List<Object[]> findSalesByCategory(@Param("startDate") LocalDateTime startDate,
                                       @Param("endDate") LocalDateTime endDate);

    // Q4 - Dead Products (active products with no sales in period)
    @Query(nativeQuery = true, value = """
            SELECT p.product_id, p.name, p.category, p.unit_price
            FROM products p
            WHERE p.active = true
              AND p.product_id NOT IN (
                  SELECT DISTINCT sd.product_id
                  FROM saledetails sd
                  JOIN sales s ON sd.sale_id = s.sale_id
                  WHERE s.datetime BETWEEN :startDate AND :endDate
              )
            """)
    List<Object[]> findDeadProducts(@Param("startDate") LocalDateTime startDate,
                                    @Param("endDate") LocalDateTime endDate);

    // Q5 - Cashier Ranking
    @Query(nativeQuery = true, value = """
            SELECT e.employee_id, e.full_name,
                   COUNT(s.sale_id) AS salesCount,
                   SUM(s.total) AS totalSold
            FROM sales s
            JOIN employees e ON s.employee_id = e.employee_id
            WHERE e.position = 'ROLE_CAJERO'
              AND s.datetime BETWEEN :startDate AND :endDate
            GROUP BY e.employee_id, e.full_name
            ORDER BY totalSold DESC
            """)
    List<Object[]> findCashierRanking(@Param("startDate") LocalDateTime startDate,
                                      @Param("endDate") LocalDateTime endDate);

    // Q6 - Delivery Ranking
    @Query(nativeQuery = true, value = """
            SELECT e.employee_id, e.full_name,
                   COUNT(d.order_id) AS deliveriesCompleted
            FROM deliveryorders d
            JOIN employees e ON d.delivery_employee_id = e.employee_id
            JOIN sales s ON d.sale_id = s.sale_id
            WHERE d.status = 'ACTIVO'
              AND s.datetime BETWEEN :startDate AND :endDate
            GROUP BY e.employee_id, e.full_name
            ORDER BY deliveriesCompleted DESC
            """)
    List<Object[]> findDeliveryRanking(@Param("startDate") LocalDateTime startDate,
                                       @Param("endDate") LocalDateTime endDate);

    // Q7 - Top VIP Customers
    @Query(nativeQuery = true, value = """
            SELECT c.customer_id, c.name,
                   COUNT(s.sale_id) AS salesCount,
                   SUM(s.total) AS totalPurchased
            FROM sales s
            JOIN customers c ON s.customer_id = c.customer_id
            WHERE s.datetime BETWEEN :startDate AND :endDate
            GROUP BY c.customer_id, c.name
            ORDER BY totalPurchased DESC
            LIMIT 10
            """)
    List<Object[]> findTopVipCustomers(@Param("startDate") LocalDateTime startDate,
                                       @Param("endDate") LocalDateTime endDate);

    // Q8 - Sales by Customer Type
    @Query(nativeQuery = true, value = """
            SELECT ct.name AS customerTypeName,
                   COUNT(s.sale_id) AS salesCount,
                   SUM(s.total) AS totalRevenue
            FROM sales s
            JOIN customers c ON s.customer_id = c.customer_id
            JOIN customertypes ct ON c.customer_type_id = ct.customer_type_id
            WHERE s.datetime BETWEEN :startDate AND :endDate
            GROUP BY ct.customer_type_id, ct.name
            """)
    List<Object[]> findSalesByCustomerType(@Param("startDate") LocalDateTime startDate,
                                            @Param("endDate") LocalDateTime endDate);

    // Q9 - Registered vs Anonymous Sales
    @Query(nativeQuery = true, value = """
            SELECT
                COUNT(CASE WHEN s.customer_id IS NOT NULL THEN 1 END) AS registeredSalesCount,
                COALESCE(SUM(CASE WHEN s.customer_id IS NOT NULL THEN s.total END), 0) AS registeredSalesTotal,
                COUNT(CASE WHEN s.customer_id IS NULL THEN 1 END) AS anonymousSalesCount,
                COALESCE(SUM(CASE WHEN s.customer_id IS NULL THEN s.total END), 0) AS anonymousSalesTotal
            FROM sales s
            WHERE s.datetime BETWEEN :startDate AND :endDate
            """)
    List<Object[]> findRegisteredVsAnonymous(@Param("startDate") LocalDateTime startDate,
                                              @Param("endDate") LocalDateTime endDate);

    // Q10 - Sales Heatmap (day of week + hour in Mexico time - manual offset -6h)
    @Query(nativeQuery = true, value = """
            SELECT
                DAYOFWEEK(DATE_SUB(s.datetime, INTERVAL 6 HOUR)) AS dayOfWeek,
                HOUR(DATE_SUB(s.datetime, INTERVAL 6 HOUR)) AS hour,
                COUNT(s.sale_id) AS salesCount
            FROM sales s
            WHERE s.datetime BETWEEN :startDate AND :endDate
            GROUP BY dayOfWeek, hour
            """)
    List<Object[]> findSalesHeatmap(@Param("startDate") LocalDateTime startDate,
                                    @Param("endDate") LocalDateTime endDate);

    // Q11 - Average Ticket by Hour (Mexico time - manual offset -6h)
    @Query(nativeQuery = true, value = """
            SELECT
                HOUR(DATE_SUB(s.datetime, INTERVAL 6 HOUR)) AS hour,
                AVG(s.total) AS averageTicket
            FROM sales s
            WHERE s.datetime BETWEEN :startDate AND :endDate
            GROUP BY hour
            ORDER BY hour
            """)
    List<Object[]> findAverageTicketByHour(@Param("startDate") LocalDateTime startDate,
                                            @Param("endDate") LocalDateTime endDate);

    // Q12 - Total Debt (unpaid sales)
    @Query(nativeQuery = true, value = """
            SELECT COALESCE(SUM(s.total), 0) AS totalDebt
            FROM sales s
            WHERE s.paid = false
              AND s.datetime BETWEEN :startDate AND :endDate
            """)
    BigDecimal findTotalDebt(@Param("startDate") LocalDateTime startDate,
                             @Param("endDate") LocalDateTime endDate);

    // Q13 - Oldest Unpaid Sales
    @Query(nativeQuery = true, value = """
            SELECT s.sale_id, s.datetime, s.total,
                   c.name AS customerName,
                   e.full_name AS employeeName
            FROM sales s
            LEFT JOIN customers c ON s.customer_id = c.customer_id
            JOIN employees e ON s.employee_id = e.employee_id
            WHERE s.paid = false
              AND s.datetime BETWEEN :startDate AND :endDate
            ORDER BY s.datetime ASC
            LIMIT 10
            """)
    List<Object[]> findOldestUnpaidSales(@Param("startDate") LocalDateTime startDate,
                                          @Param("endDate") LocalDateTime endDate);

    // Q14 - Discount Impact: gross, discounts, net
    @Query(nativeQuery = true, value = """
            SELECT
                COALESCE(SUM(s.subtotal), 0) AS grossSales,
                COALESCE(SUM(s.discount_amount), 0) + COALESCE((
                    SELECT SUM(sd.discount)
                    FROM saledetails sd
                    JOIN sales s2 ON sd.sale_id = s2.sale_id
                    WHERE s2.datetime BETWEEN :startDate AND :endDate
                ), 0) AS totalDiscounts,
                COALESCE(SUM(s.total), 0) AS netSales
            FROM sales s
            WHERE s.datetime BETWEEN :startDate AND :endDate
            """)
    List<Object[]> findDiscountImpact(@Param("startDate") LocalDateTime startDate,
                                      @Param("endDate") LocalDateTime endDate);

    // Q15 - Average Items Per Sale
    @Query(nativeQuery = true, value = """
            SELECT COALESCE(AVG(item_count), 0)
            FROM (
                SELECT COUNT(sd.detail_id) AS item_count
                FROM saledetails sd
                JOIN sales s ON sd.sale_id = s.sale_id
                WHERE s.datetime BETWEEN :startDate AND :endDate
                GROUP BY sd.sale_id
            ) AS counts
            """)
    BigDecimal findAverageItemsPerSale(@Param("startDate") LocalDateTime startDate,
                                       @Param("endDate") LocalDateTime endDate);
}
