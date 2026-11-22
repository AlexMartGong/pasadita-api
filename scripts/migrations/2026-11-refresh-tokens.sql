-- Sesiones persistentes: refresh tokens rotativos (solo el hash SHA-256, nunca el valor plano).
-- Aplicar en producción ANTES de desplegar el código: prod arranca con ddl-auto=validate.
-- Idempotente: se puede ejecutar más de una vez (MySQL 8.4).

create table if not exists refresh_tokens
(
    id             bigint auto_increment
        primary key,
    employee_id    bigint       not null,
    token_hash     char(64)     not null,
    family_id      char(36)     not null,
    expires_at     datetime     not null,
    revoked_at     datetime     null,
    replaced_by_id bigint       null,
    user_agent     varchar(255) null,
    created_at     datetime     not null,
    constraint uk_refresh_tokens_token_hash
        unique (token_hash),
    constraint fk_refresh_tokens_employee
        foreign key (employee_id) references employees (employee_id)
            on delete cascade,
    constraint fk_refresh_tokens_replaced_by
        foreign key (replaced_by_id) references refresh_tokens (id)
            on delete set null,
    index idx_refresh_tokens_employee (employee_id),
    index idx_refresh_tokens_family (family_id)
) engine = InnoDB
  default charset = utf8mb4
  collate = utf8mb4_0900_ai_ci;
