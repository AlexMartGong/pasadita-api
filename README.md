# Pasadita API

A comprehensive RESTful API for managing a small business, built with Spring Boot. This API provides complete functionality for managing employees, products, customers, sales, and delivery orders with JWT-based authentication and role-based authorization.

## Table of Contents

- [Features](#features)
- [Technology Stack](#technology-stack)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Configuration](#configuration)
- [Running the Application](#running-the-application)
- [API Endpoints](#api-endpoints)
- [Architecture](#architecture)
- [Security](#security)
- [Testing](#testing)
- [Development](#development)
- [License](#license)

## Features

- **Employee Management**: Full CRUD operations with role-based access control
- **Product Management**: Manage product catalog with categories and pricing
- **Customer Management**: Customer and customer type administration
- **Sales Management**: Complete sales tracking with detailed line items
- **Delivery Orders**: Track and manage delivery orders with status updates
- **JWT Authentication**: Secure token-based authentication
- **Role-Based Authorization**: Fine-grained access control using Spring Security
- **Input Validation**: Comprehensive data validation using Jakarta Validation
- **RESTful Design**: Clean, intuitive API endpoints following REST principles
- **API Documentation**: Integrated Spring REST Docs with AsciiDoc

## Technology Stack

- **Framework**: Spring Boot 3.5.5
- **Language**: Java 17
- **Database**: MySQL 8.x
- **ORM**: Spring Data JPA / Hibernate
- **Security**: Spring Security with JWT (JJWT 0.12.6)
- **Validation**: Jakarta Validation
- **Build Tool**: Maven 3.x
- **Additional Libraries**:
  - Lombok (reduce boilerplate code)
  - Spring Boot Actuator (monitoring)
  - Spring REST Docs (API documentation)
  - Spring DevTools (development productivity)

## Prerequisites

Before you begin, ensure you have the following installed:

- **Java Development Kit (JDK) 17** or higher
- **Maven 3.6+** (or use the included Maven Wrapper)
- **MySQL 8.x** or higher
- **Git** (for cloning the repository)

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

3. **Configure database credentials**

Update the `src/main/resources/application.properties` file with your MySQL credentials:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/la_pasadita_database?useSSL=false&serverTimezone=GMT-6&allowPublicKeyRetrieval=true
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
- **Password**: `root`

### JWT Configuration

JWT authentication is configured with the following defaults (can be modified in `application.properties`):

- **Token Expiration**: 24 hours (86400000 ms)
- **Secret Key**: Configurable in `jwt.secret` property

**Important**: Change the JWT secret key in production environments.

### Server Configuration

- **Port**: 8080 (default)
- **Context Path**: `/`

## Running the Application

### Using Maven Wrapper (Recommended)

```bash
# Run with default profile
./mvnw spring-boot:run

# Run with development profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### Using Packaged JAR

```bash
# Build the JAR
./mvnw clean package

# Run the JAR
java -jar target/pasadita-api-0.0.1-SNAPSHOT.jar
```

The application will start on `http://localhost:8080`

## API Endpoints

### Authentication

```
POST /login - Authenticate and receive JWT token
```

### Employees

```
GET    /api/employees/all - Get all employees (ADMIN only)
GET    /api/employees/{id} - Get employee by ID (ADMIN only)
GET    /api/employees/search?username={username} - Search employee by username (ADMIN only)
POST   /api/employees/save - Create new employee (ADMIN only)
PUT    /api/employees/{id} - Update employee (ADMIN only)
DELETE /api/employees/delete/{id} - Delete employee (ADMIN only)
PUT    /api/employees/change-password/{id} - Change employee password (ADMIN only)
PUT    /api/employees/change-status/{id} - Change employee status (ADMIN only)
```

### Products

```
GET    /api/products/all - Get all products
GET    /api/products/{id} - Get product by ID
POST   /api/products/save - Create new product
PUT    /api/products/{id} - Update product
DELETE /api/products/delete/{id} - Delete product
PUT    /api/products/change-price/{id} - Update product price
PUT    /api/products/change-status/{id} - Change product status
```

### Customers

```
GET    /api/customers/all - Get all customers
GET    /api/customers/{id} - Get customer by ID
POST   /api/customers/save - Create new customer
PUT    /api/customers/{id} - Update customer
DELETE /api/customers/delete/{id} - Delete customer
PUT    /api/customers/change-status/{id} - Change customer status
```

### Customer Types

```
GET    /api/customer-types/all - Get all customer types
GET    /api/customer-types/{id} - Get customer type by ID
POST   /api/customer-types/save - Create new customer type
PUT    /api/customer-types/{id} - Update customer type
DELETE /api/customer-types/delete/{id} - Delete customer type
```

### Sales

```
GET    /api/sales/all - Get all sales
GET    /api/sales/{id} - Get sale by ID
POST   /api/sales/save - Create new sale
PUT    /api/sales/{id} - Update sale
DELETE /api/sales/delete/{id} - Delete sale
```

### Delivery Orders

```
GET    /api/delivery-orders/all - Get all delivery orders
GET    /api/delivery-orders/{id} - Get delivery order by ID
POST   /api/delivery-orders/save - Create new delivery order
PUT    /api/delivery-orders/{id} - Update delivery order
DELETE /api/delivery-orders/delete/{id} - Delete delivery order
PUT    /api/delivery-orders/change-status/{id} - Change delivery order status
```

## Architecture

### Project Structure

```
com.pasadita.api
├── controllers/          # REST API endpoints
├── services/            # Business logic layer
│   ├── customer/
│   ├── deliveryorder/
│   ├── employee/
│   ├── product/
│   ├── sale/
│   └── saledetail/
├── repositories/        # Data access layer
├── entities/           # JPA entities
├── dto/               # Data Transfer Objects
│   ├── customer/
│   ├── deliveryorder/
│   ├── employee/
│   ├── product/
│   ├── sale/
│   └── saledetail/
├── enums/             # Enumerations
│   ├── delivery/
│   ├── product/
│   └── user/
├── security/          # Security configuration
│   └── filter/
├── validation/        # Custom validators
└── utils/            # Utility classes
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
- **CORS Configuration**: Configured for cross-origin requests

### Security Filters

1. **JwtAuthenticationFilter**: Handles login and token generation
2. **JwtValidationFilter**: Validates JWT tokens on protected endpoints

### Roles

- **ADMIN**: Full access to employee management and system administration
- **USER**: Access to regular business operations

## Testing

### Run All Tests

```bash
./mvnw test
```

### Run Specific Test

```bash
./mvnw test -Dtest=PasaditaApiApplicationTests
```

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

The application uses JPA/Hibernate for ORM with the following main entities:

- **Employee**: System users with authentication
- **Product**: Product catalog with categories
- **Customer**: Customer information
- **CustomerType**: Customer categorization
- **Sale**: Sales transactions
- **SaleDetail**: Line items for sales
- **DeliveryOrder**: Delivery tracking
- **PaymentMethod**: Payment methods

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
