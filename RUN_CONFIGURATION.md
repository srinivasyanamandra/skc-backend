# IntelliJ IDEA Run Configuration

## Option 1: Set Environment Variables in IntelliJ

1. Open **Run → Edit Configurations**
2. Select your Spring Boot application
3. In **Environment variables**, add:
   ```
   DB_USERNAME=postgres;DB_PASSWORD=postgres;ADMIN_PASSWORD=admin123;JWT_SECRET=sri-karthikeya-caterers-jwt-secret-key-2026-production-grade;SMTP_USERNAME=info@srikarthikeyacaterers.in;SMTP_PASSWORD=your_gmail_app_password;REVIEW_BASE_URL=http://localhost:3000;SPRING_PROFILES_ACTIVE=local
   ```
4. Click **Apply** and **OK**

## Option 2: Use EnvFile Plugin

1. Install **EnvFile** plugin from IntelliJ Marketplace
2. Open **Run → Edit Configurations**
3. Enable **EnvFile** tab
4. Add `.env` file
5. Click **Apply** and **OK**

## Option 3: Set Active Profile Only

If you don't want to set all environment variables:

1. Open **Run → Edit Configurations**
2. In **VM options**, add:
   ```
   -Dspring.profiles.active=local
   ```
3. Click **Apply** and **OK**

This will load `application-local.yml` which has default values.

## Option 4: Use Maven to Run

From terminal:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Option 5: Default Profile (No Configuration Needed)

The application now has `application-default.yml` which will be used when no profile is specified. Just run the application directly from IntelliJ.

**Note**: Make sure PostgreSQL is running and the database `sri_karthikeya_caterers` exists:

```bash
# Create database
createdb sri_karthikeya_caterers

# Or using psql
psql -U postgres
CREATE DATABASE sri_karthikeya_caterers;
\q
```
