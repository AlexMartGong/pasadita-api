# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

### Senior Developer & SOLID Standards

- **Persona**: Act as a Senior Java Spring Boot Software Architect. Prioritize maintainability, scalability, and clarity
  over quick hacks.
- **SOLID & Architecture Principles**:
    - **S**: Strict Single Responsibility. Controllers only handle HTTP routing/validation, Services contain pure
      business logic, Repositories handle database queries, and Mappers strictly handle DTO-Entity conversions.
    - **O**: Always use interfaces for services (e.g., `EmployeeService` and `EmployeeServiceImpl`) to allow extension
      without modification.
    - **L**: Ensure implementations are entirely substitutable for their interfaces without altering program
      correctness.
    - **I**: Keep interfaces lean and domain-specific; don't force implementations to depend on unused methods.
    - **D**: STRICTLY use Constructor Injection via Lombok's `@RequiredArgsConstructor`. NEVER use field injection (
      `@Autowired`).
- **Spring Boot Best Practices**:
    - Apply `@Transactional` appropriately in the service layer. Always use `@Transactional(readOnly = true)` for fetch
      operations.
    - Prevent N+1 query problems by proactively using `@EntityGraph` in JPA Repositories when fetching related entities.
    - Enforce security at the Controller level using `@PreAuthorize`.
- **Clean Code**: Follow DRY and KISS. Avoid primitive obsession by using your domain DTOs. Ensure variables and
  methods have descriptive, intent-revealing names. Return appropriate HTTP status codes and use `Optional` for nullable
  responses.

## Common Development Commands

### Build and Run

```bash
# Build the project
./mvnw clean compile

# Run tests
./mvnw test

# Run a specific test class
./mvnw test -Dtest=PasaditaApiApplicationTests

# Run the application
./mvnw spring-boot:run

# Package the application
./mvnw clean package

# Skip tests during build
./mvnw clean package -DskipTests
```

### Development

```bash
# Run with production profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod

# Generate REST Docs documentation
./mvnw clean package
```

### Local Docker Stack

```bash
# Create the local env file (dummy values, stack boots as-is)
cp .env.example .env

# Build the prod image and start MySQL (host port 3307) + API (8080)
# The Dockerfile is runtime-only (copies target/*.jar), so build the JAR first
./mvnw clean package -DskipTests
docker build -t pasadita-api:prod .
docker compose -f docker-compose.local.yml up -d

# Re-seed the database (destroys local data; scriptLP.sql runs on fresh volume)
docker compose -f docker-compose.local.yml down -v
```

- Two local MySQLs coexist: the compose container on host port **3307** and the host's own MySQL on **3306**.
  `./mvnw spring-boot:run` uses the host DB (3306); the dockerized API uses the container DB (`local-db:3306`).
- `scriptLP.sql` is mounted as a MySQL init script, so the prod profile's `ddl-auto=validate` passes on a fresh
  volume; it seeds the `admin`/`123456` user (see Data Model Patterns).
- Secrets in `docker-compose.local.yml` are no longer hardcoded: they're interpolated (`${VAR}`) from a gitignored
  `.env` in the project root, which Docker Compose auto-loads. Copy `.env.example` (committed template with local-only
  dummy values) to `.env` and the stack boots without extra setup. `JWT_SECRET` must be valid base64 (`TokenJwtConfig`
  base64-decodes it).

## Architecture Overview

### Technology Stack

- **Framework**: Spring Boot 3.5.15 with Java 21
- **Database**: MySQL with JPA/Hibernate
- **Security**: JWT-based authentication with Spring Security
- **Real-time**: WebSocket for printer connections
- **Object Storage**: Cloudflare R2 (S3-compatible) via AWS SDK v2 for product image uploads
- **Documentation**: Spring REST Docs with AsciiDoc
- **Build Tool**: Maven with wrapper
- **Code Quality**: Qodana JVM Community linter
- **CI/CD**: GitHub Actions (deploy to DigitalOcean on main push, Qodana on PRs)

### Layered Architecture

The application follows a standard layered architecture:

1. **Controllers** (`com.pasadita.api.controllers`) - REST endpoints with `@PreAuthorize` security
2. **Services** (`com.pasadita.api.services`) - Business logic with interface/implementation pattern
3. **Repositories** (`com.pasadita.api.repositories`) - Data access layer extending JpaRepository
4. **Entities** (`com.pasadita.api.entities`) - JPA entities with Lombok annotations
5. **DTOs** (`com.pasadita.api.dto`) - Data transfer objects with dedicated mapper classes

### Security Implementation

- JWT token authentication with custom filters (`JwtAuthenticationFilter`, `JwtValidationFilter`)
- Role-based authorization using `@PreAuthorize` annotations
- Password encoding with BCrypt
- CORS configuration via `CorsConfig` class (`security/`): `app.cors.allowed-origins` (env `CORS_ALLOWED_ORIGINS`,
  prod default `https://lapasadita.app`) takes precedence, then `app.cors.allowed-origin-patterns`; with neither set,
  dev falls back to permissive patterns (`localhost`, `127.0.0.1`, `192.168.*`, `10.*`)
- Stateless session management
- Roles: `ROLE_ADMIN`, `ROLE_CAJERO` (cashier), `ROLE_PEDIDOS` (orders)

### Data Model Patterns

- Entities use Lombok annotations (`@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`)
- Enums for categorization (`Category`, `UnitMeasure`, `Position`, `DeliveryStatus`, `InvoiceStatus`)
- Custom validation annotations (`@ExistsEmployee`)
- Separate DTOs for Create, Update, Response, and specific operations (ChangePassword, ChangeStatus)
- Dedicated mapper classes for entity-DTO conversion
- **Schema source of truth**: `src/main/resources/scriptLP.sql` is the canonical MySQL DDL. Keep entities aligned (
  column names, nullability, length, indexes) so production `ddl-auto=validate` passes. The script ends with a seed
  `INSERT` for the `admin` employee (`ROLE_ADMIN`, password `123456`, verified BCrypt hash) — local convenience only;
  change the password in production. Never hand-write or copy BCrypt hashes from tutorials: generate them with
  `BCryptPasswordEncoder` and verify with `matches()` before inserting.

## Development Guidelines

### Package Structure

```
com.pasadita.api/
├── config/           # WebSocket, CORS, and @ConfigurationProperties (e.g., FacturacionProperties, R2Properties); S3Config exposes the S3Client bean
├── controllers/      # REST endpoints by domain
├── dto/              # DTOs organized by domain (customer, employee, product, sale, saledetail, deliveryorder, dashboard, ticket, fiscal, invoice)
├── entities/         # JPA entities
├── enums/            # Enums organized by category (product, user, delivery, invoice)
├── exceptions/       # Custom exceptions
├── repositories/     # Spring Data JPA repositories
├── security/         # Security configuration and JWT filters
├── services/         # Business logic (interface + implementation pattern by domain — includes fiscal, invoice, storage)
├── utils/            # Common utilities (DateTimeUtils, ValidationUtils)
└── validation/       # Custom validation annotations and validators
```

`PasaditaApiApplication` declares `@ConfigurationPropertiesScan("com.pasadita.api.config")` — any new
`@ConfigurationProperties` class must live under that package to be picked up.

### Database Configuration

- Requires MySQL database named `la_pasadita_database`
- Default connection: `jdbc:mysql://localhost:3306/la_pasadita_database`
- Default credentials: root/Root1234 (update in `application.properties` for different environments)
- Uses Hibernate dialect for MySQL with SQL logging enabled
- **Production** (`application-prod.properties`): Uses environment variables (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`,
  `JWT_SECRET`, `JWT_EXPIRATION`, `CORS_ALLOWED_ORIGINS`, `CSD_CER_PATH`, `CSD_KEY_PATH`, `CSD_PASSWORD`,
  `FACTURAPI_KEY`, `R2_ACCESS_KEY`,
  `R2_SECRET_KEY`, `R2_ENDPOINT`, `R2_BUCKET`, `R2_PUBLIC_URL`), Hibernate `ddl-auto=validate`, HikariCP pool (max 10)
- **Facturación (CFDI)**: `facturacion.emisor.*` (rfc, razon-social, regimen-fiscal, codigo-postal) and
  `facturacion.csd.*` (cer-path, key-path, password) are bound to `FacturacionProperties` (under `config/`).
  `facturapi.key` (env `FACTURAPI_KEY`) is the Facturapi secret bearer token; `FacturapiConfig` exposes both the
  `Facturapi` SDK bean and a shared `HttpClient` bean (`facturapiHttpClient`, declared with
  `@Bean(destroyMethod = "close")` — it's a singleton, never close it per call; the container closes it on
  shutdown) used for direct REST calls and proxy downloads. Dev `application.properties` provides dummy fallback defaults for `CSD_*` and `FACTURAPI_KEY`, so local
  runs/tests start without real secrets; production requires the real env vars
- **Object Storage (Cloudflare R2)**: `cloudflare.r2.*` (access-key, secret-key, endpoint, bucket [default
  `lapasadita-assets`], public-url) bound to `R2Properties` (record under `config/`), all from env vars (`R2_ACCESS_KEY`,
  `R2_SECRET_KEY`, `R2_ENDPOINT`, `R2_BUCKET`, `R2_PUBLIC_URL`). `S3Config` exposes the AWS SDK v2 `S3Client` bean
  (endpoint override, static R2 creds, `Region.US_EAST_1`, path-style access). The bean is built eagerly at startup;
  dev `application.properties` provides dummy fallback defaults for the `R2_*` vars so the context starts without real
  credentials, but production (`application-prod.properties`) requires the real env vars
- **Timezone Strategy**: Database stores all dates in UTC (`serverTimezone=UTC` in production)
- **Date Conversion**: Use `DateTimeUtils` class for timezone handling:
    - `DateTimeUtils.nowUtc()` - Get current time in UTC (for saving to DB)
    - `DateTimeUtils.nowMexico()` - Get current time in Mexico timezone (for default date range logic)
    - `DateTimeUtils.toMexicoTime(datetime)` - Convert UTC to Mexico time (for API responses)
    - `DateTimeUtils.toUtc(datetime)` - Convert Mexico time to UTC (for user input)

### Security Considerations

- JWT secret is configured in `application.properties` (change for production)
- Token expiration set to 24 hours (86400000ms)
- All endpoints require authentication except OPTIONS requests
- Admin role required for employee management endpoints
- Passwords are BCrypt-encoded before storage
- Employee authentication uses username/password to generate JWT token

### Service Layer Pattern

Services follow a consistent interface/implementation pattern:

- Interface defines contract (e.g., `EmployeeService`)
- Implementation class handles business logic (e.g., `EmployeeServiceImpl`)
- Services are organized by domain in subpackages
- Use `@Transactional` annotations for database operations
- Read-only operations use `@Transactional(readOnly = true)`
- **Newer services use constructor injection with Lombok `@RequiredArgsConstructor`** instead of `@Autowired`
- Class-level `@Transactional` can be applied with method-level overrides for read-only operations

### DTO Mapper Pattern

Each domain has a dedicated mapper class (e.g., `EmployeeMapper`, `CustomerMapper`):

- `toResponseDto(Entity)` - converts entity to response DTO
- `toEntity(CreateDto)` - converts create DTO to entity
- `updateEntityFromDto(Entity, UpdateDto)` - updates existing entity from update DTO
- Mappers are Spring components (`@Component`) for dependency injection

### Controller Patterns

- Use `@PreAuthorize` for role-based access control
- Validate request bodies with `@Valid` and `BindingResult`
- Return validation errors using `ValidationUtils.getValidationErrors(result)`
- Return appropriate HTTP status codes (200 OK, 201 CREATED, 404 NOT_FOUND, etc.)
- Use `Optional` for nullable responses

### Exception Handling

Centralized via `GlobalExceptionHandler` (`@RestControllerAdvice`):

| Exception                   | HTTP Status        | Usage                                                                          |
|-----------------------------|--------------------|--------------------------------------------------------------------------------|
| `EntityNotFoundException`   | 404 NOT_FOUND      | Entity lookup fails (throw from service)                                       |
| `BusinessRuleException`     | 400 BAD_REQUEST    | Domain rule violation (e.g., cancel already-delivered order)                   |
| `EmployeeInactiveException` | 401 (auth failure) | Extends `AuthenticationException`; thrown during login if employee is inactive |
| `Exception` (catch-all)     | 500                | Logged via SLF4J, returns generic message                                      |

- Always throw `EntityNotFoundException` or `BusinessRuleException` from services — never return null or handle HTTP
  status in the service layer.

### Repository Pattern

- All repositories extend `CrudRepository<Entity, Long>` or `JpaRepository<Entity, Long>`
- Custom query methods follow Spring Data JPA naming conventions (e.g., `findBySaleId`, `findByUsername`)
- Use `@EntityGraph` to optimize fetching and avoid N+1 queries (e.g.,
  `@EntityGraph(attributePaths = {"sale", "product"})`)
- Use `@Modifying` + `@Query` for custom update operations (e.g., `updatePriceById`)
- Use `@Query(nativeQuery = true, value = "...")` for complex analytics (e.g., `DashboardRepository` — 15 native queries
  with date range params)
- Repositories are organized by domain with corresponding entities

### Testing Approach

- Uses Spring Boot Test framework
- Main test class: `PasaditaApiApplicationTests`
- Pure Mockito unit tests (no Spring context) for service rules: `InvoiceServiceImplTest` (stamping happy path,
  RESICO ISR retention, SAT catalog validation, `PENDIENTE`/`ERROR` row reuse vs. finalized rejection, Facturapi
  rejection message transparency + `markAsError`, cancel/email flows), `SaleServiceImplTest`
  (discount rule: range bounds, cap, per-quantity accumulation, derived sale totals)
- Spring Security Test support available
- REST Docs integration for API documentation
- Surefire runs with `-XX:+EnableDynamicAgentLoading` (silences the JDK 21 dynamic-agent warning for Mockito/Byte
  Buddy)

### Domain Model

Current domains include:

- **Employee**: User management with positions (ADMIN, CAJERO, PEDIDOS)
- **Customer**: Customer management with customer types
- **CustomerType**: Customer categorization
- **Product**: Inventory with categories and unit measures
    - **Best-seller ordering (current month)**: `GET /api/products/all` → `ProductServiceImpl.findAll()` →
      `ProductRepository.findAllOrderByTotalSoldDesc(startDate, endDate)` ranks products by units sold in the
      **current month** (Mexico time). The service computes the month bounds (day 1 `00:00` → last day
      `LocalTime.MAX`) from `DateTimeUtils.nowMexico()` and converts them with `toUtc()` before querying
      (`sales.datetime` is UTC). The JPQL keeps `LEFT JOIN`s and filters inside the aggregate —
      `SUM(CASE WHEN s.datetime BETWEEN :startDate AND :endDate THEN sd.quantity ELSE 0 END)` — so out-of-month
      sales don't count but products with zero sales still appear (ranked last). No HTTP params; endpoint contract
      unchanged. Don't move the `BETWEEN` into the join `ON` — quantities from other months would be summed again
    - `claveProductoSat` (varchar 8) — SAT product/service code, optional, used for CFDI invoicing
    - `imageUrl` (varchar 255, nullable) — public URL of the product image in Cloudflare R2; set only via the image
      upload endpoint (see Object Storage), exposed in `ProductResponseDto`. `POST /api/products/{id}/image`
      (`ROLE_ADMIN`/`ROLE_CAJERO`) accepts a `MultipartFile` and calls `ProductService.uploadImage`
- **Sale**: Sales transactions with payment methods and sale details
    - Relationships: ManyToOne with Employee, **Customer (mandatory, NOT NULL)**, PaymentMethod
    - OneToMany with SaleDetail
    - Tracks subtotal, discount, total, paid status, notes, `amountTendered`; `changeDue` is computed in mappers
    - `SaleCreateDto` carries transient (non-persisted) routing fields: `stationId` (target printer) and
      `printTicket` (optional `Boolean`, `@Builder.Default = true`). When `printTicket` is `false`, the sale is still
      saved but the async WebSocket print is skipped. Omitted/`null` ⇒ prints (Jackson yields `null` on the no-args
      path, so the controller treats `null` as `true` for backward compat)
    - **Cash-drawer fallback**: the drawer opens via an electric pulse the thermal printer emits over RJ11 when it
      receives ESC/POS data, so skipping the ticket would leave it shut on cash sales. When `printTicket` is `false`
      **and** `paymentMethodId == 1L` (cash), `saveSale` instead dispatches a lightweight `OPEN_DRAWER` WebSocket
      command (no ticket) via `sendOpenDrawerAsync`
    - **Discount rule ("Regla de Alex")** — enforced in `SaleServiceImpl.save` via
      `resolveApplicableUnitDiscount(unitPrice, requestedUnitDiscount)`:
        - `SaleDetailCreateDto.discount` is interpreted as a **per-unit** discount (not a line amount)
        - Unit price within **[1, 10] inclusive** (`compareTo`, scale-insensitive): discount forced to `0` (protects
          cheap by-portion products like cilantro from being given away)
        - Any other unit price: discount clamped to `[0, unitPrice]` via `max(ZERO).min(unitPrice)` — negative
          requested discounts become `0`, and the net unit price never goes negative
        - Persisted line amounts: `subtotal = unitPrice × qty` and `discount = appliedUnitDiscount × qty` (each
          `setScale(2, HALF_UP)` after multiplying), then `total = subtotal − discount` **derived after rounding**
          (not `netUnitPrice × qty`), so the line invariant holds to the cent on fractional quantities (kilos/portions)
        - Sale-level `discountAmount` is **derived** as Σ of corrected line discounts and `total` as Σ of line
          totals — the client-sent values are ignored (service is the source of truth, same as
          `subtotal`/`unitPrice`); `total = subtotal − discountAmount` holds by construction
        - Applies only to `save`; `update()` re-inserts details without recomputing amounts (known gap)
- **SaleDetail**: Line items for sales
    - ManyToOne relationships with Sale and Product
    - Tracks quantity, unit price, subtotal, discount, and total (all server-computed under the discount rule above)
- **PaymentMethod**: Payment method catalog (cash, card, etc.)
    - `claveFormaPagoSat` (varchar 2) — SAT forma de pago code (e.g. `01` cash, `04` card). No CRUD layer; managed via
      SQL/seed
- **DeliveryOrder**: Delivery management with status tracking
    - OneToOne relationship with Sale
    - ManyToOne with Employee (delivery driver)
    - Tracks status, request date, delivery address, contact phone, and delivery cost
- **CustomerFiscalData**: Tax data for invoicing (separate from Customer to allow multiple RFCs / shared by walk-ins)
    - Fields: `rfc` (unique, indexed), `razonSocial`, `regimenFiscal`, `codigoPostalFiscal`, `usoCfdi`,
      `emailFacturacion`, plus optional phone/address
    - DTOs/services under `dto/fiscal/` and `services/fiscal/`
- **Invoice**: CFDI (Mexican fiscal invoice) tied to a Sale
    - OneToOne with `Sale` (unique), ManyToOne with `CustomerFiscalData`
    - Status enum (`InvoiceStatus`): `PENDIENTE`, `TIMBRADA`, `CANCELADA`, `ERROR` — defaults to `PENDIENTE`
    - Tracks `uuid` (SAT folio), `xmlUrl`, `pdfUrl`, `createdAt`, `timbradoAt`
    - Service rules: sale must be paid, fiscal data must be active
    - **Row reuse (sale_id is UNIQUE)**: both `createInvoiceRequest` and `timbrarInvoice` go through
      `findOrCreatePendingInvoice` — an existing `PENDIENTE`/`ERROR` row for the sale is **updated in place**
      (fiscal data refreshed, status reset to `PENDIENTE`) instead of inserting a new row (which would hit the
      unique constraint → `DataIntegrityViolationException` 500); `TIMBRADA`/`CANCELADA` are finalized and throw
      `BusinessRuleException`
    - DTOs/services under `dto/invoice/` and `services/invoice/`
    - **Stamping flow** (`InvoiceServiceImpl.timbrarInvoice`): customer + product creation use the Facturapi Java SDK,
      but the invoice POST is sent through the JDK `HttpClient` (`POST https://www.facturapi.io/v2/invoices` with
      `Authorization: Bearer ${facturapi.key}`) and parsed via Jackson `JsonNode`. The SDK 1.2.0 invoice deserializer
      is incompatible with CFDI 4.0 responses, so it is bypassed for that step
    - **Transaction isolation**: `timbrarInvoice`, `cancelInvoice`, and `sendInvoiceEmail` are deliberately **not**
      `@Transactional` — Facturapi SDK/HTTP calls (up to 20 s timeout) must not hold a MySQL/Hikari connection.
      Flow is three phases: persist `PENDIENTE` (short implicit repository tx), remote call outside any tx, persist
      `TIMBRADA`/`ERROR` result (short tx). Safe because `SaleRepository.findWithDetailsById` eager-fetches via
      `@EntityGraph` everything the stamping path touches (no lazy loads outside a tx). `createInvoiceRequest` and
      the read-only lookups keep `@Transactional` (pure DB). Error status is persisted via the inner
      `InvoiceErrorPersister` (`REQUIRES_NEW`). One subtlety: outside a surrounding tx,
      `repository.save(entityWithId)` runs `em.merge()` in its own short tx and returns a **copy** whose LAZY
      `sale`/`customerFiscalData` (no cascade) are uninitialized proxies that die when that tx commits
      (prod-only — `spring.jpa.open-in-view=false` is set only in `application-prod.properties`; dev OSIV masks
      it). The service therefore re-attaches the already-loaded `sale`/`customerFiscalData` onto every post-save
      copy before DTO mapping via `reattachAssociations` — in `findOrCreatePendingInvoice`, after the TIMBRADA
      save in `timbrarInvoice`, and in `cancelInvoice`
    - **Error transparency**: in `timbrarInvoice`, `BusinessRuleException` (e.g. Facturapi HTTP 4xx body) is caught,
      the invoice is marked `ERROR`, and the exception is **rethrown as-is** so the client sees the exact rejection
      reason; only truly unexpected `RuntimeException`s get wrapped as "Error inesperado al timbrar CFDI".
      `markAsError` guards **only** the remote stamping phase — once the CFDI is stamped at SAT the row is never
      flipped to `ERROR`: a failure persisting `TIMBRADA` is logged with the stamped UUID and rethrown raw (row
      stays `PENDIENTE`, retriable via row reuse)
    - **RESICO ISR retention**: `buildProductPayload(detail, applyIsrRetention)` conditionally appends a 1.25% ISR
      retention to the product `taxes` array as `{type:ISR, rate:0.0125, factor:Tasa, withholding:true}` (Facturapi's
      convention — `withholding:true` lands it in CFDI `Retenciones`; a separate `retentions` key would be ignored).
      The flag is computed once per invoice in `appliesResicoIsrRetention(fiscalData)`: true when the emisor régimen is
      RESICO (`626`, read from `FacturacionProperties.emisor().regimenFiscal()`) **and** the receptor RFC is 12 chars
      (persona moral). A 13-char RFC (persona física) gets only IVA-0, no retention
    - **Endpoints** (`InvoiceController`, `ROLE_ADMIN`/`ROLE_CAJERO`/`ROLE_PEDIDOS` unless noted):
      `POST /api/invoices` (creates a `PENDIENTE` row), `GET /api/invoices` (paginated list),
      `POST /api/invoices/timbrar` (executes stamping), `DELETE /api/invoices/{invoiceId}?motive=` (cancel, `ROLE_ADMIN`
      only, motive defaults `02`), `GET /api/invoices/sale/{saleId}`, `GET /api/invoices/sale/{saleId}/pdf` and `/xml`
      (server-side proxy downloads from Facturapi using the bearer secret; require `status == TIMBRADA`),
      `POST /api/invoices/sale/{saleId}/email?email=` (sends the invoice by email)
- **Ticket**: Read-only DTO for printing sale receipts via WebSocket
- **Dashboard**: Analytics/reporting domain — read-only stats aggregated over a date range
    - Uses `DashboardRepository` (extends `JpaRepository<Sale, Long>`) with 15 native SQL queries
    - Stats grouped into: FinancialSummary, ProductAnalysis, Operations, CustomerAnalysis, TimeAnalysis,
      FinancialHealth, BasketAnalysis
    - Endpoint: `GET /api/dashboard?startDate=&endDate=` (defaults to current month in Mexico time, converted to UTC for
      query)
    - Access restricted to `ROLE_ADMIN`

### Object Storage (Cloudflare R2)

Product images are stored in Cloudflare R2 (S3-compatible) via AWS SDK v2:

- **Config**: `R2Properties` (`@ConfigurationProperties(prefix = "cloudflare.r2")`, record under `config/`) +
  `S3Config` (`S3Client` bean). Version managed by the AWS SDK BOM (`software.amazon.awssdk:bom`) in `pom.xml`
- **Service** (`services/storage/`): `StorageService.uploadFile(MultipartFile file, String folder)` →
  `S3StorageServiceImpl` generates a UUID key (`folder/<uuid>.<ext>`, extension preserved), uploads via `putObject`,
  and returns `publicUrl + "/" + key`. I/O / SDK failures are wrapped in `BusinessRuleException` (400)
- **Flow**: `ProductServiceImpl.uploadImage(productId, file)` loads the product (404 if missing), uploads to the
  `products` folder, sets `imageUrl`, saves, and returns the DTO. Endpoint: `POST /api/products/{id}/image`
  (`ROLE_ADMIN`/`ROLE_CAJERO`)

### WebSocket Integration

The application includes WebSocket support for real-time printer connections:

- **Endpoint**: `/ws/printer?stationId={stationId}`
- **Handler**: `PrinterWebSocketHandler` manages station connections. Uses Lombok `@Slf4j` + `@RequiredArgsConstructor`;
  all logging is SLF4J (no `System.out`)
- **Thread safety**: `afterConnectionEstablished` stores each session wrapped in Spring's
  `ConcurrentWebSocketSessionDecorator` (sendTimeLimit 10 s, bufferSizeLimit 1 MB) — sends are dispatched from async
  threads (`CompletableFuture.runAsync` in `SaleController`) and raw WebSocket sessions are not thread-safe (concurrent
  `sendMessage` threw `IllegalStateException: The remote endpoint was in state [TEXT_FULL_WRITING]` and silently dropped
  the ticket/drawer command). On buffer overflow or send timeout the decorator throws `SessionLimitExceededException`
  and closes the session with `SESSION_NOT_RELIABLE`; close/error callbacks receive the raw session, but map cleanup is
  keyed by `stationId` (from the query string), so removal still works
- **Usage**: When a sale is created, tickets are sent asynchronously to connected printer stations. The async
  dispatch lives in `SaleController.saveSale` (not the service), gated on `SaleCreateDto.printTicket` (skip when
  `false`, print when `true`/`null`)
- Supports sending to specific station (`stationId`) or broadcasting to all connected stations
- **Drawer command** (decoupled from printing): `PrinterWebSocketHandler.sendOpenDrawerCommand(stationId)` sends a
  lightweight `{"type":"OPEN_DRAWER","timestamp":<utc>}` `TextMessage` to one station (uses SLF4J: `info` on send,
  `warn` if the station is offline, `error` on I/O failure). Triggered two ways:
    - Automatically in `saveSale` when `printTicket == false` and the sale is cash (`paymentMethodId == 1L`)
    - Manually via `POST /api/sales/open-drawer?stationId=` (`ROLE_ADMIN`/`ROLE_CAJERO`) — `200` + success map when
      `stationId` is present, `400` + error map when missing/blank. Both paths go through `sendOpenDrawerAsync`

### Entity Relationship Patterns

- Use `@ManyToOne(fetch = FetchType.LAZY)` for many-to-one relationships
- Use `@OneToMany(mappedBy = "...", cascade = CascadeType.ALL, fetch = FetchType.LAZY)` for one-to-many
- Use `@OneToOne` with `unique = true` constraint for one-to-one relationships
- Exclude collections from `@ToString` to avoid lazy loading issues
- Use `@EqualsAndHashCode(of = "id")` to prevent infinite loops in bidirectional relationships
- Default values use `@Builder.Default` annotation