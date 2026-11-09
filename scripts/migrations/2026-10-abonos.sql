-- Abonos y crédito en pedidos a domicilio (fase 1 + 1.1: estado de los abonos e historial de cambios).
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
    status            enum ('APLICADO', 'CANCELADO')     default 'APLICADO' not null,
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

create table sale_payment_history
(
    history_id                 bigint auto_increment
        primary key,
    payment_id                 bigint                                     not null,
    action                     enum ('CREADO', 'MODIFICADO', 'CANCELADO') not null,
    previous_amount            decimal(10, 2)                             null,
    new_amount                 decimal(10, 2)                             null,
    previous_payment_method_id bigint                                     null,
    new_payment_method_id      bigint                                     null,
    reason                     varchar(200)                               null,
    changed_by                 bigint                                     not null,
    changed_at                 datetime default CURRENT_TIMESTAMP         not null,
    constraint sale_payment_history_ibfk_payment
        foreign key (payment_id) references sale_payments (payment_id),
    constraint sale_payment_history_ibfk_employee
        foreign key (changed_by) references employees (employee_id),
    constraint sale_payment_history_ibfk_prev_method
        foreign key (previous_payment_method_id) references paymentmethods (payment_method_id),
    constraint sale_payment_history_ibfk_new_method
        foreign key (new_payment_method_id) references paymentmethods (payment_method_id)
);

create index idx_sale_payment_history_payment
    on sale_payment_history (payment_id);

create index idx_sale_payment_history_changed_at
    on sale_payment_history (changed_at);

-- Los pedidos PENDIENTE de hoy son las deudas actuales
update sales s
    join deliveryorders d on d.sale_id = s.sale_id
set s.paid = 0
where d.status = 'PENDIENTE';
