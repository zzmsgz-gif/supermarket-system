# Supermarket Backend

Spring Boot backend API for the supermarket shopping system.

## Requirements

- JDK 17 or later.
- Maven 3.9 or later.
- MySQL database initialized by `../deploy/init.sql`.

## Database Configuration

The default database is:

```text
jdbc:mysql://localhost:3306/supermarket_system
```

Set database credentials with environment variables:

```powershell
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your_mysql_password"
```

If your MySQL username or password is different, change either the environment variables above or `src/main/resources/application.yml`.

## Run

```powershell
cd backend
mvn spring-boot:run
```

This project includes `backend/.mvn/maven.config`, so Maven will use `../.mvn/local-settings.xml` automatically when you run commands from the `backend` directory.

The backend runs at:

```text
http://localhost:8080/api
```

## Public Test URLs

```text
GET http://localhost:8080/api/health
GET http://localhost:8080/api/categories
GET http://localhost:8080/api/products
GET http://localhost:8080/api/products/1
```

## Current Implemented APIs

- `GET /api/health`
- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/auth/me`
- `GET /api/categories`
- `GET /api/products`
- `GET /api/products/{id}`

Cart, orders, and admin write APIs are planned in `../docs/api-design.md`.
