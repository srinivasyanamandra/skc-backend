# Quick Start Guide

Get the Sri Karthikeya Caterers backend running in 5 minutes.

## Prerequisites

- Java 21 installed
- PostgreSQL 14+ installed and running
- Maven 3.8+ (or use included Maven wrapper)

## Step 1: Create Database

```bash
# Connect to PostgreSQL
psql -U postgres

# Create database
CREATE DATABASE sri_karthikeya_caterers;

# Exit
\q
```

## Step 2: Configure Environment

```bash
# Copy environment template
cp .env.example .env

# Edit .env with your settings
nano .env
```

Minimum required changes in `.env`:
```properties
DB_PASSWORD=your_postgres_password
ADMIN_PASSWORD=your_admin_password
JWT_SECRET=your-secret-key-must-be-at-least-32-bytes-long-for-hs256
```

## Step 3: Run the Application

### Option A: Using Maven (Recommended for first run)

```bash
# Make Maven wrapper executable (Linux/Mac)
chmod +x mvnw

# Run with local profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Or on Windows:
```cmd
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

### Option B: Using IntelliJ IDEA

The application now includes `application-default.yml` which will work without any configuration.

**Just click the Run button!** ▶️

If you want to use the local profile explicitly:

1. Open **Run → Edit Configurations**
2. In **VM options**, add: `-Dspring.profiles.active=local`
3. Click **Apply** and **OK**
4. Run the application

See [RUN_CONFIGURATION.md](RUN_CONFIGURATION.md) for more IntelliJ setup options.

### Option C: Using JAR

```bash
# Build
./mvnw clean package -DskipTests

# Run
java -jar target/skc-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

## Step 4: Test the API

The API is now running at `http://localhost:8080`

### Test Public Endpoint

```bash
curl -X POST http://localhost:8080/api/public/quotes \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test User",
    "email": "test@example.com",
    "phone": "+91 9876543210",
    "eventType": "wedding",
    "eventDate": "2026-12-31",
    "guests": 100,
    "venue": "Test Venue",
    "budget": "₹1,00,000",
    "message": "Test quote request"
  }'
```

### Test Admin Login

```bash
curl -X POST http://localhost:8080/api/admin/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "info@srikarthikeyacaterers.in",
    "password": "admin123"
  }'
```

Save the returned token for authenticated requests.

### Test Admin Dashboard

```bash
curl -X GET http://localhost:8080/api/admin/dashboard \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

## Step 5: Import Postman Collection

1. Open Postman
2. Click Import
3. Select `postman_collection.json` from the project root
4. Update the `baseUrl` variable if needed
5. Run "Admin Login" to automatically set the token
6. Test other endpoints

## Troubleshooting

### Database Connection Error

```
Error: Connection refused
```

**Solution**: Ensure PostgreSQL is running and credentials in `.env` are correct.

```bash
# Check PostgreSQL status (Linux)
sudo systemctl status postgresql

# Start PostgreSQL (Linux)
sudo systemctl start postgresql

# macOS (Homebrew)
brew services start postgresql@14
```

### Port Already in Use

```
Error: Port 8080 is already in use
```

**Solution**: Change the port in `application-local.yml`:

```yaml
server:
  port: 8081
```

### JWT Secret Too Short

```
Error: The specified key byte array is 256 bits which is not secure enough
```

**Solution**: Ensure `JWT_SECRET` in `.env` is at least 32 characters long.

### Email Sending Fails

```
Error: Authentication failed
```

**Solution**: 
1. Enable 2FA on Gmail
2. Generate an App Password
3. Use the App Password in `SMTP_PASSWORD`

## Next Steps

1. Review the [README.md](README.md) for complete documentation
2. Check the [API Documentation](#api-documentation) section
3. Explore the codebase structure
4. Customize email templates
5. Set up production environment

## Development Tips

### Hot Reload

The application uses Spring Boot DevTools for automatic restart on code changes.

### View Logs

Logs are printed to console. Adjust log levels in `application-local.yml`:

```yaml
logging:
  level:
    syncqubits.ai.skc: DEBUG
```

### Database Schema

The application auto-creates tables on first run (local profile).

To manually create schema:
```bash
psql -U postgres -d sri_karthikeya_caterers -f src/main/resources/schema.sql
```

### Build Without Running

```bash
./mvnw clean package -DskipTests
```

JAR file will be in `target/skc-0.0.1-SNAPSHOT.jar`

## Support

For issues:
1. Check logs in console
2. Verify database connection
3. Ensure all environment variables are set
4. Review the [README.md](README.md) troubleshooting section

Contact: info@srikarthikeyacaterers.in
