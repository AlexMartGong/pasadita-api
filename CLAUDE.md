# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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
# Run with development profile and live reload
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Generate documentation
./mvnw clean package
```

## Architecture Overview

### Technology Stack
- **Framework**: Spring Boot 3.5.8 with Java 17
- **Database**: MySQL with JPA/Hibernate
- **Security**: JWT-based authentication with Spring Security
- **Real-time**: WebSocket for printer connections
- **Documentation**: Spring REST Docs with AsciiDoc
- **Build Tool**: Maven with wrapper

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
- CORS configuration for cross-origin requests
- Stateless session management

### Data Model Patterns
- Entities use Lombok annotations (`@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`)
- Enums for categorization (`Category`, `UnitMeasure`, `Position`, `DeliveryStatus`)
- Custom validation annotations (`@ExistsEmployee`)
- Separate DTOs for Create, Update, Response, and specific operations (ChangePassword, ChangeStatus)
- Dedicated mapper classes for entity-DTO conversion

## Development Guidelines

### Package Structure
```
com.pasadita.api/
├── config/           # WebSocket configuration and handlers
├── controllers/      # REST endpoints by domain
├── dto/              # DTOs organized by domain (customer, employee, product, sale, ticket, etc.)
├── entities/         # JPA entities
├── enums/            # Enums organized by category (product, user, delivery)
├── exceptions/       # Custom exceptions
├── repositories/     # Spring Data JPA repositories
├── security/         # Security configuration and JWT filters
├── services/         # Business logic (interface + implementation pattern by domain)
├── utils/            # Common utilities (e.g., ValidationUtils)
└── validation/       # Custom validation annotations and validators
```

### Database Configuration
- Requires MySQL database named `la_pasadita_database`
- Default connection: `jdbc:mysql://localhost:3306/la_pasadita_database`
- Default credentials: root/Root1234 (update in `application.properties` for different environments)
- Uses Hibernate dialect for MySQL with SQL logging enabled
- **Timezone Strategy**: Database stores all dates in UTC (`serverTimezone=UTC` in production)
- **Date Conversion**: Use `DateTimeUtils` class for timezone handling:
  - `DateTimeUtils.nowUtc()` - Get current time in UTC (for saving to DB)
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

### Repository Pattern
- All repositories extend `CrudRepository<Entity, Long>` or `JpaRepository<Entity, Long>`
- Custom query methods follow Spring Data JPA naming conventions (e.g., `findBySaleId`, `findByUsername`)
- Use `@EntityGraph` to optimize fetching and avoid N+1 queries (e.g., `@EntityGraph(attributePaths = {"sale", "product"})`)
- Repositories are organized by domain with corresponding entities

### Testing Approach
- Uses Spring Boot Test framework
- Main test class: `PasaditaApiApplicationTests`
- Spring Security Test support available
- REST Docs integration for API documentation

### Domain Model
Current domains include:
- **Employee**: User management with positions (ADMIN, CASHIER, DRIVER, etc.)
- **Customer**: Customer management with customer types
- **CustomerType**: Customer categorization
- **Product**: Inventory with categories and unit measures
- **Sale**: Sales transactions with payment methods and sale details
  - Relationships: ManyToOne with Employee, Customer (optional), PaymentMethod
  - OneToMany with SaleDetail
  - Tracks subtotal, discount, total, paid status, and notes
- **SaleDetail**: Line items for sales
  - ManyToOne relationships with Sale and Product
  - Tracks quantity, unit price, and subtotal
- **PaymentMethod**: Payment method catalog (cash, card, etc.)
- **DeliveryOrder**: Delivery management with status tracking
  - OneToOne relationship with Sale
  - ManyToOne with Employee (delivery driver)
  - Tracks status, request date, delivery address, contact phone, and delivery cost
- **Ticket**: Read-only DTO for printing sale receipts via WebSocket

### WebSocket Integration
The application includes WebSocket support for real-time printer connections:
- **Endpoint**: `/ws/printer?stationId={stationId}`
- **Handler**: `PrinterWebSocketHandler` manages station connections
- **Usage**: When a sale is created, tickets are sent asynchronously to connected printer stations
- Supports sending to specific station or broadcasting to all connected stations

### Entity Relationship Patterns
- Use `@ManyToOne(fetch = FetchType.LAZY)` for many-to-one relationships
- Use `@OneToMany(mappedBy = "...", cascade = CascadeType.ALL, fetch = FetchType.LAZY)` for one-to-many
- Use `@OneToOne` with `unique = true` constraint for one-to-one relationships
- Exclude collections from `@ToString` to avoid lazy loading issues
- Use `@EqualsAndHashCode(of = "id")` to prevent infinite loops in bidirectional relationships
- Default values use `@Builder.Default` annotation