create table customer_fiscal_data
(
    fiscal_id            bigint auto_increment
        primary key,
    rfc                  varchar(13)                          not null,
    razon_social         varchar(150)                         not null,
    regimen_fiscal       varchar(3)                           not null,
    codigo_postal_fiscal varchar(5)                           not null,
    uso_cfdi             varchar(4)                           not null,
    email_facturacion    varchar(100)                         not null,
    phone                varchar(15)                          null,
    address              varchar(200)                         null,
    active               tinyint(1) default 1                 null,
    created_at           datetime   default CURRENT_TIMESTAMP null,
    constraint rfc_unique
        unique (rfc)
);

create index idx_rfc
    on customer_fiscal_data (rfc);

create table customertypes
(
    customer_type_id bigint auto_increment
        primary key,
    name             varchar(50)                not null,
    description      varchar(200)               null,
    discount         decimal(5, 2) default 0.00 null,
    constraint name
        unique (name)
);

create table customers
(
    customer_id      bigint auto_increment
        primary key,
    customer_type_id bigint               not null,
    name             varchar(100)         not null,
    phone            varchar(15)          not null,
    address          varchar(200)         null,
    city             varchar(50)          null,
    postal_code      varchar(10)          null,
    custom_discount  decimal(5, 2)        null,
    notes            text                 null,
    active           tinyint(1) default 1 null,
    constraint customers_ibfk_1
        foreign key (customer_type_id) references customertypes (customer_type_id)
);

create index customer_type_id
    on customers (customer_type_id);

create table employees
(
    employee_id bigint auto_increment
        primary key,
    full_name   varchar(150)                                       not null,
    phone       varchar(10)                                        not null,
    username    varchar(50)                                        not null,
    position    enum ('ROLE_ADMIN', 'ROLE_CAJERO', 'ROLE_PEDIDOS') not null,
    active      tinyint(1) default 1                               null,
    password    varchar(100)                                       not null,
    constraint username_UNIQUE
        unique (username)
);

create table paymentmethods
(
    payment_method_id    bigint auto_increment
        primary key,
    name                 varchar(50)          not null,
    active               tinyint(1) default 1 null,
    clave_forma_pago_sat varchar(2)           null comment 'Clave de forma de pago del SAT (ej. 01 para efectivo, 04 para tarjeta)',
    constraint name
        unique (name)
);

create table products
(
    product_id         bigint auto_increment
        primary key,
    category           enum ('FRUTAS', 'VERDURAS', 'HIERBAS', 'CHILES', 'GRANOS_CEREALES', 'FRUTOS_SECOS')      not null,
    name               varchar(100)                                                                             not null,
    unit_price         decimal(10, 2)                                                                           not null,
    unit_measure       enum ('PIEZA', 'PORCION', 'KILOGRAMO', 'GRAMO', 'LITRO', 'MILILITRO', 'CAJA', 'ARPILLA') not null,
    active             tinyint(1) default 1                                                                     null,
    clave_producto_sat varchar(8)                                                                               null comment 'Clave de producto/servicio del SAT (ej. 50301500)',
    image_url          varchar(255)                                                                             null
);

create table sales
(
    sale_id           bigint auto_increment
        primary key,
    employee_id       bigint                                   not null,
    customer_id       bigint                                   not null,
    payment_method_id bigint                                   not null,
    datetime          datetime       default CURRENT_TIMESTAMP null,
    subtotal          decimal(10, 2)                           not null,
    discount_amount   decimal(10, 2) default 0.00              null,
    total             decimal(10, 2)                           not null,
    paid              tinyint(1)     default 1                 null,
    notes             text                                     null,
    amount_tendered   decimal(10, 2)                           not null,
    client_request_id varchar(36)                              null,
    constraint uq_sales_client_request_id
        unique (client_request_id),
    constraint sales_ibfk_1
        foreign key (employee_id) references employees (employee_id),
    constraint sales_ibfk_2
        foreign key (customer_id) references customers (customer_id),
    constraint sales_ibfk_3
        foreign key (payment_method_id) references paymentmethods (payment_method_id)
);

create table deliveryorders
(
    order_id             bigint auto_increment
        primary key,
    sale_id              bigint                                                              not null,
    status               enum ('ACTIVO', 'PENDIENTE', 'CANCELADO') default 'ACTIVO'          null,
    request_date         datetime                                  default CURRENT_TIMESTAMP null,
    delivery_address     varchar(200)                                                        not null,
    contact_phone        varchar(15)                                                         not null,
    delivery_cost        decimal(10, 2)                            default 0.00              null,
    delivery_employee_id bigint                                                              null,
    constraint sale_id
        unique (sale_id),
    constraint deliveryorders_ibfk_1
        foreign key (sale_id) references sales (sale_id),
    constraint deliveryorders_ibfk_3
        foreign key (delivery_employee_id) references employees (employee_id)
);

create index delivery_employee_id
    on deliveryorders (delivery_employee_id);

create table invoices
(
    invoice_id  bigint auto_increment
        primary key,
    sale_id     bigint                                                                         not null,
    fiscal_id   bigint                                                                         not null,
    uuid        varchar(36)                                                                    null,
    status      enum ('PENDIENTE', 'TIMBRADA', 'CANCELADA', 'ERROR') default 'PENDIENTE'       null,
    xml_url     text                                                                           null,
    pdf_url     text                                                                           null,
    created_at  datetime                                             default CURRENT_TIMESTAMP null,
    timbrado_at datetime                                                                       null,
    constraint invoice_sale_unique
        unique (sale_id),
    constraint invoices_ibfk_fiscal
        foreign key (fiscal_id) references customer_fiscal_data (fiscal_id),
    constraint invoices_ibfk_sale
        foreign key (sale_id) references sales (sale_id)
);

create index idx_invoice_status
    on invoices (status);

create index idx_invoice_uuid
    on invoices (uuid);

create table saledetails
(
    detail_id  bigint auto_increment
        primary key,
    sale_id    bigint                      not null,
    product_id bigint                      not null,
    quantity   decimal(10, 3)              not null,
    unit_price decimal(10, 2)              not null,
    subtotal   decimal(12, 2)              not null,
    discount   decimal(10, 2) default 0.00 null,
    total      decimal(12, 2)              not null,
    constraint saledetails_ibfk_1
        foreign key (sale_id) references sales (sale_id),
    constraint saledetails_ibfk_2
        foreign key (product_id) references products (product_id)
);

create index product_id
    on saledetails (product_id);

create index sale_id
    on saledetails (sale_id);

create index customer_id
    on sales (customer_id);

create index employee_id
    on sales (employee_id);

create index payment_method_id
    on sales (payment_method_id);


