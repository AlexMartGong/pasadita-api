# Pasadita API

A comprehensive RESTful API for managing a small business, built with Spring Boot. This API provides complete functionality for managing employees, products, customers, sales, delivery orders, CFDI invoicing (Mexican electronic invoices), and business analytics with JWT-based authentication and role-based authorization.

## Table of Contents

- [Features](#features)
- [Technology Stack](#technology-stack)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Configuration](#configuration)
- [Running the Application](#running-the-application)
- [Local Docker Stack](#local-docker-stack)
- [API Endpoints](#api-endpoints)
- [Architecture](#architecture)
- [Security](#security)
- [Testing](#testing)
- [Development](#development)
- [License](#license)

## Features

- **Employee Management**: Full CRUD operations with role-based access control
- **Product Management**: Manage product catalog with categories, pricing, and product images stored in Cloudflare R2
- **Customer Management**: Customer and customer type administration
- **Customer Fiscal Data**: Tax data (RFC, régimen fiscal, uso CFDI) for invoicing, decoupled from customers
- **Sales Management**: Complete sales tracking with detailed line items
- **Delivery Orders**: Track and manage delivery orders with status updates
- **CFDI Invoicing**: Mexican electronic invoices (CFDI 4.0) stamped through Facturapi, with PDF/XML download and email delivery
- **Dashboard Analytics**: Aggregated financial, product, customer, and time-based stats over a date range (admin only)
- **Ticket Printing**: Real-time receipt printing to POS stations via WebSocket, including cash-drawer open commands
- **JWT Authentication**: Secure token-based authentication
- **Role-Based Authorization**: Fine-grained access control using Spring Security
- **Input Validation**: Comprehensive data validation using Jakarta Validation
- **RESTful Design**: Clean, intuitive API endpoints following REST principles
- **API Documentation**: Integrated Spring REST Docs with AsciiDoc

## Technology Stack

- **Framework**: Spring Boot 3.5.15
- **Language**: Java 21
- **Database**: MySQL 8.x
- **ORM**: Spring Data JPA / Hibernate
- **Security**: Spring Security with JWT (JJWT 0.12.6)
- **Real-time**: Spring WebSocket (printer stations)
- **Invoicing**: Facturapi (CFDI 4.0 stamping)
- **Object Storage**: Cloudflare R2 (S3-compatible) via AWS SDK v2
- **Validation**: Jakarta Validation
- **Build Tool**: Maven 3.x
- **Containers**: Docker + Docker Compose (local testing stack)
- **Code Quality**: Qodana JVM Community linter
- **CI/CD**: GitHub Actions (deploy to DigitalOcean on `main` push, Qodana on PRs)
- **Additional Libraries**:
  - Lombok (reduce boilerplate code)
  - Spring Boot Actuator (monitoring)
  - Spring REST Docs (API documentation)
  - Spring DevTools (development productivity)

## Prerequisites

Before you begin, ensure you have the following installed:

- **Java Development Kit (JDK) 21** or higher
- **Maven 3.6+** (or use the included Maven Wrapper)
- **MySQL 8.x** or higher
- **Git** (for cloning the repository)
- **Docker + Docker Compose** (optional, for the local Docker stack)

## Installation

1. **Clone the repository**

```bash
git clone https://github.com/AlexMartGong/pasadita-api.git
cd pasadita-api
```

2. **Create the MySQL database**

```sql
CREATE DATABASE la_pasadita_database;
```

The canonical schema lives in `src/main/resources/scriptLP.sql` (see [Database Schema](#database-schema)).

3. **Configure database credentials**

Update the `src/main/resources/application.properties` file with your MySQL credentials:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/la_pasadita_database?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=your_username
spring.datasource.password=your_password
```

4. **Build the project**

```bash
./mvnw clean install
```

## Configuration

### Database Configuration

The application is configured to connect to a MySQL database. Default settings:

- **Database Name**: `la_pasadita_database`
- **Host**: `localhost:3306`
- **Username**: `root`
- **Password**: `Root1234`

Dates are stored in UTC; use the `DateTimeUtils` class for timezone conversions (Mexico time for API responses).

### JWT Configuration

JWT authentication is configured with the following defaults (can be modified in `application.properties`):

- **Token Expiration**: 24 hours (86400000 ms)
- **Secret Key**: Configurable via the `JWT_SECRET` environment variable — must be valid base64 (it is base64-decoded at startup)

**Important**: Change the JWT secret key in production environments.

### CORS Configuration

CORS is handled by the `CorsConfig` class (`security/`):

- `app.cors.allowed-origins` (env `CORS_ALLOWED_ORIGINS`) — exact origins; production defaults to `https://lapasadita.app`
- `app.cors.allowed-origin-patterns` — wildcard patterns
- If neither is set, development falls back to permissive patterns for `localhost`, `127.0.0.1`, and private LAN ranges (`192.168.*`, `10.*`)

### External Integrations

Production (`application-prod.properties`) reads all secrets from environment variables:

- **Database**: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- **JWT**: `JWT_SECRET`, `JWT_EXPIRATION`
- **CORS**: `CORS_ALLOWED_ORIGINS`
- **CFDI / Facturapi**: `CSD_CER_PATH`, `CSD_KEY_PATH`, `CSD_PASSWORD`, `FACTURAPI_KEY`
- **Cloudflare R2**: `R2_ACCESS_KEY`, `R2_SECRET_KEY`, `R2_ENDPOINT`, `R2_BUCKET`, `R2_PUBLIC_URL`

Dev `application.properties` ships dummy fallback defaults for the CSD, Facturapi, and R2 variables, so local runs and tests start without real secrets.

### Server Configuration

- **Port**: 8080 (default)
- **Context Path**: `/`

## Running the Application

### Using Maven Wrapper (Recommended)

```bash
# Run with default (dev) profile
./mvnw spring-boot:run

# Run with production profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

### Using Packaged JAR

```bash
# Build the JAR
./mvnw clean package

# Run the JAR
java -jar target/pasadita-api-0.0.1-SNAPSHOT.jar
```

The application will start on `http://localhost:8080`

## Local Docker Stack

A self-contained stack (MySQL + API with the prod profile) for local testing:

```bash
# Create the local env file (dummy values, stack boots as-is)
cp .env.example .env

# Build the prod image and start MySQL (host port 3307) + API (8080)
docker build -t pasadita-api:prod .
docker compose -f docker-compose.local.yml up -d

# Re-seed the database (destroys local data; scriptLP.sql runs on fresh volume)
docker compose -f docker-compose.local.yml down -v
```

Notes:

- The compose MySQL is exposed on host port **3307** so it can coexist with a host MySQL on 3306. `./mvnw spring-boot:run` uses the host DB; the dockerized API uses the container DB (`local-db:3306`).
- `scriptLP.sql` is mounted as a MySQL init script, so the prod profile's `ddl-auto=validate` passes on a fresh volume. It seeds an `admin`/`123456` user (local convenience only — change it in production).
- Secrets are interpolated from a gitignored `.env` auto-loaded by Docker Compose. `.env.example` is the committed template with local-only dummy values.

## API Endpoints

### Authentication

```
POST /login - Authenticate and receive JWT token
```

### Employees (ADMIN only)

```
GET    /api/employees/all - Get all employees
GET    /api/employees/{id} - Get employee by ID
GET    /api/employees/search?username={username} - Search employee by username
POST   /api/employees/save - Create new employee
PUT    /api/employees/{id} - Update employee
DELETE /api/employees/delete/{id} - Delete employee
PUT    /api/employees/change-password/{id} - Change employee password
PUT    /api/employees/change-status/{id} - Change employee status
```

### Products

```
GET    /api/products/all - Get all products
GET    /api/products/{id} - Get product by ID
POST   /api/products/save - Create new product
PUT    /api/products/update/{id} - Update product
PUT    /api/products/update-price/{id} - Update product price
PUT    /api/products/change-status/{id} - Change product status
POST   /api/products/{id}/image - Upload product image (multipart, stored in R2)
```

### Customers

```
GET    /api/customers/all - Get all customers
POST   /api/customers/save - Create new customer
PUT    /api/customers/update/{id} - Update customer
PUT    /api/customers/change-status/{id} - Change customer status
```

### Customer Types

```
GET    /api/customer-types/all - Get all customer types
POST   /api/customer-types/save - Create new customer type
PUT    /api/customer-types/update - Update customer type
```

### Customer Fiscal Data

```
GET    /api/customer-fiscal-data/all - Get all fiscal data records
GET    /api/customer-fiscal-data/{id} - Get fiscal data by ID
GET    /api/customer-fiscal-data/by-rfc/{rfc} - Get fiscal data by RFC
POST   /api/customer-fiscal-data/save - Create fiscal data
PUT    /api/customer-fiscal-data/update/{id} - Update fiscal data
```

### Sales

```
GET    /api/sales/all - Get all sales
POST   /api/sales/save - Create new sale (optionally prints ticket via WebSocket)
PUT    /api/sales/update/{id} - Update sale
PUT    /api/sales/change-status/{id} - Change sale status
GET    /api/sales/{saleId}/details - Get sale line items
GET    /api/sales/{saleId}/ticket - Get printable ticket data
POST   /api/sales/open-drawer?stationId={stationId} - Open cash drawer at a station
```

### Delivery Orders

```
GET    /api/delivery-orders/all - Get all delivery orders
POST   /api/delivery-orders/save - Create new delivery order
PUT    /api/delivery-orders/update/{id} - Update delivery order
PUT    /api/delivery-orders/change-status/{id} - Change delivery order status
```

### Invoices (CFDI)

```
POST   /api/invoices - Create a pending invoice request
GET    /api/invoices - List invoices (paginated)
POST   /api/invoices/timbrar - Stamp an invoice through Facturapi
DELETE /api/invoices/{invoiceId}?motive={motive} - Cancel an invoice (ADMIN only)
GET    /api/invoices/sale/{saleId} - Get invoice by sale
GET    /api/invoices/sale/{saleId}/pdf - Download stamped invoice PDF
GET    /api/invoices/sale/{saleId}/xml - Download stamped invoice XML
POST   /api/invoices/sale/{saleId}/email?email={email} - Email invoice to recipient
```

### Dashboard (ADMIN only)

```
GET    /api/dashboard?startDate=&endDate= - Aggregated business stats (defaults to current month, Mexico time)
```

### WebSocket

```
/ws/printer?stationId={stationId} - Printer station connection (tickets and OPEN_DRAWER commands)
```

Sessions are wrapped in Spring's `ConcurrentWebSocketSessionDecorator` (10 s send limit, 1 MB buffer), so concurrent
ticket/drawer sends from async threads are serialized safely instead of failing with `TEXT_FULL_WRITING`.

## Architecture

### Project Structure

```
com.pasadita.api
├── config/              # WebSocket, Facturapi, S3/R2, @ConfigurationProperties
├── controllers/         # REST API endpoints
├── services/            # Business logic layer
│   ├── customer/
│   ├── dashboard/
│   ├── deliveryorder/
│   ├── employee/
│   ├── fiscal/
│   ├── invoice/
│   ├── product/
│   ├── sale/
│   ├── saledetail/
│   └── storage/
├── repositories/        # Data access layer
├── entities/            # JPA entities
├── dto/                 # Data Transfer Objects (by domain, incl. dashboard,
│                        # fiscal, invoice, ticket)
├── enums/               # Enumerations (delivery, invoice, product, user)
├── exceptions/          # Custom exceptions + GlobalExceptionHandler
├── security/            # Security configuration, CORS, JWT filters
│   └── filter/
├── validation/          # Custom validators
└── utils/               # Utility classes (DateTimeUtils, ValidationUtils)
```

### Layered Architecture

The application follows a clean layered architecture:

1. **Controller Layer**: Handles HTTP requests and responses
2. **Service Layer**: Contains business logic and transaction management
3. **Repository Layer**: Manages data persistence with JPA
4. **DTO Layer**: Data transfer objects with dedicated mappers
5. **Entity Layer**: JPA entities representing database tables

### Design Patterns

- **Repository Pattern**: For data access abstraction
- **Service Pattern**: For business logic encapsulation
- **DTO Pattern**: For data transfer and API contracts
- **Mapper Pattern**: For entity-DTO conversions
- **Builder Pattern**: Using Lombok's @Builder annotation

## Security

### Authentication & Authorization

- **JWT Token Authentication**: Stateless authentication using JSON Web Tokens
- **BCrypt Password Encoding**: Secure password hashing
- **Role-Based Access Control**: Using Spring Security's `@PreAuthorize` annotations
- **CORS Configuration**: `CorsConfig` class; production restricted to `https://lapasadita.app`, development falls back to localhost/LAN patterns

### Security Filters

1. **JwtAuthenticationFilter**: Handles login and token generation
2. **JwtValidationFilter**: Validates JWT tokens on protected endpoints

### Roles

- **ROLE_ADMIN**: Full access, including employee management, dashboard, and invoice cancellation
- **ROLE_CAJERO**: Cashier — sales, products, customers, invoicing
- **ROLE_PEDIDOS**: Orders — delivery and order-related operations

## Testing

### Run All Tests

```bash
./mvnw test
```

### Run Specific Test

```bash
./mvnw test -Dtest=PasaditaApiApplicationTests
```

The suite includes `PasaditaApiApplicationTests` (context load) and `InvoiceServiceImplTest` (invoice business rules).

### Generate Test Documentation

```bash
./mvnw clean package
```

This will generate API documentation using Spring REST Docs in the `target/generated-docs` directory.

## Development

### Build Commands

```bash
# Clean and compile
./mvnw clean compile

# Package without tests
./mvnw clean package -DskipTests

# Run with live reload
./mvnw spring-boot:run
```

### Code Style

- Uses **Lombok** annotations to reduce boilerplate code
- Follows **Spring Boot best practices**
- Implements **service interfaces** for flexibility
- Uses **constructor injection** for dependencies

### Database Schema

`src/main/resources/scriptLP.sql` is the canonical MySQL DDL (production runs Hibernate with `ddl-auto=validate`). Main entities:

- **Employee**: System users with authentication
- **Product**: Product catalog with categories, SAT product codes, and image URLs
- **Customer**: Customer information
- **CustomerType**: Customer categorization
- **CustomerFiscalData**: Tax data for CFDI invoicing (RFC, régimen fiscal, uso CFDI)
- **Sale**: Sales transactions
- **SaleDetail**: Line items for sales
- **DeliveryOrder**: Delivery tracking
- **PaymentMethod**: Payment methods with SAT forma de pago codes
- **Invoice**: CFDI invoices tied to sales (status, SAT UUID, XML/PDF URLs)

## Common Development Tasks

For additional development commands and guidelines, refer to [CLAUDE.md](CLAUDE.md).

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Contact

Project Link: [https://github.com/AlexMartGong/pasadita-api](https://github.com/AlexMartGong/pasadita-api)

---

**Note**: Remember to change default passwords and JWT secret keys in production environments.
