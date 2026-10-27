-- Abonos y crédito en pedidos a domicilio (fase 1).
-- Aplicar en producción ANTES de desplegar el código: prod arranca con ddl-auto=validate.

alter table customers
    add column credit_limit decimal(10, 2) default 0.00 not null;

create table sale_payments
(
    payment_id        bigint auto_increment
        primary key,
    sale_id           bigint                             not null,
    employee_id       bigint                             not null,
    payment_method_id bigint                             not null,
    amount            decimal(10, 2)                     not null,
    datetime          datetime default CURRENT_TIMESTAMP null,
    notes             varchar(200)                       null,
    client_request_id varchar(36)                        null,
    constraint sale_payments_ibfk_sale
        foreign key (sale_id) references sales (sale_id),
    constraint sale_payments_ibfk_employee
        foreign key (employee_id) references employees (employee_id),
    constraint sale_payments_ibfk_method
        foreign key (payment_method_id) references paymentmethods (payment_method_id)
);

create index idx_sale_payments_sale
    on sale_payments (sale_id);

create index idx_sale_payments_request
    on sale_payments (client_request_id);

-- Los pedidos PENDIENTE de hoy son las deudas actuales
update sales s
    join deliveryorders d on d.sale_id = s.sale_id
set s.paid = 0
where d.status = 'PENDIENTE';
