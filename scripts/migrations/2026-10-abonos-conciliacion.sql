-- Consultas de solo lectura para conciliar las deudas tras aplicar 2026-10-abonos.sql.
-- Lo abonado realmente a cada nota se captura con POST /api/sales/{saleId}/payments (solo ADMIN).

-- Deudas actuales, por cliente y de la nota más vieja a la más nueva
select c.customer_id, c.name, s.sale_id, d.order_id, s.datetime, s.total, s.amount_tendered
from deliveryorders d
         join sales s on s.sale_id = d.sale_id
         join customers c on c.customer_id = s.customer_id
where d.status = 'PENDIENTE'
order by c.name, s.datetime;

-- Inconsistencias a revisar a mano: ventas sin pagar con pedido ACTIVO
select s.sale_id, d.order_id, s.total
from sales s
         join deliveryorders d on d.sale_id = s.sale_id
where s.paid = 0
  and d.status = 'ACTIVO';
