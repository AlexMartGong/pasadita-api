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
- **Framework**: Spring Boot 3.5.5 with Java 17
- **Database**: MySQL with JPA/Hibernate
- **Security**: JWT-based authentication with Spring Security
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
- Enums for categorization (`Category`, `UnitMeasure`, `Position`)
- Custom validation annotations (`@ExistsEmployee`)
- Separate DTOs for Create, Update, Response, and specific operations (ChangePassword, ChangeStatus)
- Dedicated mapper classes for entity-DTO conversion

## Development Guidelines

### Package Structure
- Group by feature/domain (employee, product, customer)
- Separate DTOs, services, and controllers by domain
- Common utilities in `com.pasadita.api.utils`
- Security configuration in `com.pasadita.api.security`

### Database Configuration
- Requires MySQL database named `la_pasadita_database`
- Default connection: localhost:3306 with root/root credentials
- Update `application.properties` for different environments
- Uses Hibernate dialect for MySQL

### Security Considerations
- JWT secret is configured in `application.properties` (change for production)
- Token expiration set to 24 hours (86400000ms)
- All endpoints require authentication except OPTIONS requests
- Admin role required for employee management endpoints

### Testing Approach
- Uses Spring Boot Test framework
- Main test class: `PasaditaApiApplicationTests`
- Spring Security Test support available
- REST Docs integration for API documentation

### Recent Additions
- PaymentMethod and Sale entities have been added to the domain model
- Working on sales management functionality