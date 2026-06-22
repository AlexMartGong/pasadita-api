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

## Architecture Overview

### Technology Stack

- **Framework**: Spring Boot 3.5.11 with Java 17
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
- CORS configuration via `CorsConfig` class (production restricted to `https://lapasadita.app`)
- Stateless session management
- Roles: `ROLE_ADMIN`, `ROLE_CAJERO` (cashier), `ROLE_PEDIDOS` (orders)

### Data Model Patterns

- Entities use Lombok annotations (`@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`)
- Enums for categorization (`Category`, `UnitMeasure`, `Position`, `DeliveryStatus`, `InvoiceStatus`)
- Custom validation annotations (`@ExistsEmployee`)
- Separate DTOs for Create, Update, Response, and specific operations (ChangePassword, ChangeStatus)
- Dedicated mapper classes for entity-DTO conversion
- **Schema source of truth**: `src/main/resources/scriptLP.sql` is the canonical MySQL DDL. Keep entities aligned (
  column names, nullability, length, indexes) so production `ddl-auto=validate` passes.

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
  `JWT_SECRET`, `JWT_EXPIRATION`, `CSD_CER_PATH`, `CSD_KEY_PATH`, `CSD_PASSWORD`, `FACTURAPI_KEY`, `R2_ACCESS_KEY`,
  `R2_SECRET_KEY`, `R2_ENDPOINT`, `R2_BUCKET`, `R2_PUBLIC_URL`), Hibernate `ddl-auto=validate`, HikariCP pool (max 10)
- **Facturación (CFDI)**: `facturacion.emisor.*` (rfc, razon-social, regimen-fiscal, codigo-postal) and
  `facturacion.csd.*` (cer-path, key-path, password) are bound to `FacturacionProperties` (under `config/`).
  `facturapi.key` (env `FACTURAPI_KEY`) is the Facturapi secret bearer token; `FacturapiConfig` exposes both the
  `Facturapi` SDK bean and a shared `HttpClient` bean (`facturapiHttpClient`) used for direct REST calls and proxy
  downloads
- **Object Storage (Cloudflare R2)**: `cloudflare.r2.*` (access-key, secret-key, endpoint, bucket [default
  `lapasadita-assets`], public-url) bound to `R2Properties` (record under `config/`), all from env vars (`R2_ACCESS_KEY`,
  `R2_SECRET_KEY`, `R2_ENDPOINT`, `R2_BUCKET`, `R2_PUBLIC_URL`). `S3Config` exposes the AWS SDK v2 `S3Client` bean
  (endpoint override, static R2 creds, `Region.US_EAST_1`, path-style access). The bean is built eagerly at startup, so
  the `R2_*` vars must resolve or context startup fails
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
- Spring Security Test support available
- REST Docs integration for API documentation

### Domain Model

Current domains include:

- **Employee**: User management with positions (ADMIN, CAJERO, PEDIDOS)
- **Customer**: Customer management with customer types
- **CustomerType**: Customer categorization
- **Product**: Inventory with categories and unit measures
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
- **SaleDetail**: Line items for sales
    - ManyToOne relationships with Sale and Product
    - Tracks quantity, unit price, subtotal, discount, and total
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
    - Service rules: sale must be paid, fiscal data must be active, no duplicate active invoice per sale
    - DTOs/services under `dto/invoice/` and `services/invoice/`
    - **Stamping flow** (`InvoiceServiceImpl.timbrarInvoice`): customer + product creation use the Facturapi Java SDK,
      but the invoice POST is sent through the JDK `HttpClient` (`POST https://www.facturapi.io/v2/invoices` with
      `Authorization: Bearer ${facturapi.key}`) and parsed via Jackson `JsonNode`. The SDK 1.2.0 invoice deserializer
      is incompatible with CFDI 4.0 responses, so it is bypassed for that step
    - **Endpoints** (`InvoiceController`, all `ROLE_ADMIN`/`ROLE_CAJERO`): `POST /api/invoices` (creates a `PENDIENTE`
      row), `POST /api/invoices/timbrar` (executes stamping), `GET /api/invoices/sale/{saleId}`,
      `GET /api/invoices/sale/{saleId}/pdf` and `/xml` (server-side proxy downloads from Facturapi using the bearer
      secret; require `status == TIMBRADA`)
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
- **Handler**: `PrinterWebSocketHandler` manages station connections
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