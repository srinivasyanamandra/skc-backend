# Sri Karthikeya Caterers - Backend API

Production-ready Spring Boot backend for Sri Karthikeya Caterers catering management system.

## Tech Stack

- **Java 21**
- **Spring Boot 4.0.6**
- **PostgreSQL 14+**
- **Spring Security 6 + JWT (HS256)**
- **Spring Data JPA / Hibernate**
- **Spring Mail** (SMTP)
- **Maven**

## Features

### Public APIs
- Quote request submission
- Newsletter subscription
- Review invitation validation
- Review submission (one-time token)
- Public reviews listing
- Featured reviews

### Admin APIs (JWT Protected)
- Admin authentication
- Dashboard with metrics
- Quote management
- Review moderation
- Email template management
- Unified recipient search (clients + subscribers)
- Email campaign creation and sending
- Campaign history and tracking

## Project Structure

```
src/main/java/syncqubits/ai/skc/
├── config/              # Configuration classes
├── controller/          # REST controllers
├── dto/                 # Data Transfer Objects
├── entity/              # JPA entities
├── exception/           # Custom exceptions
├── repository/          # JPA repositories
├── security/            # Security & JWT
└── service/             # Business logic
```

## Setup Instructions

### 1. Prerequisites

- Java 21 or higher
- PostgreSQL 14 or higher
- Maven 3.8+

### 2. Database Setup

Create a PostgreSQL database:

```sql
CREATE DATABASE sri_karthikeya_caterers;
```

The application will auto-create tables on first run (using `spring.jpa.hibernate.ddl-auto=update` in local profile).

### 3. Environment Configuration

Copy `.env.example` to `.env` and configure:

```bash
cp .env.example .env
```

Edit `.env` with your values:

```properties
# Database
DB_USERNAME=postgres
DB_PASSWORD=your_db_password

# Admin Credentials (supports multiple admins)
ADMIN_PASSWORD=your_secure_admin_password
ADMIN_PASSWORD_2=second_admin_password

# JWT Secret (must be at least 32 bytes)
JWT_SECRET=your-secret-key-must-be-at-least-32-bytes-long

# SMTP (Gmail)
SMTP_USERNAME=info@srikarthikeyacaterers.in
SMTP_PASSWORD=your_gmail_app_password

# Review Link Base URL
REVIEW_BASE_URL=http://localhost:3000
```

### 4. Gmail SMTP Setup

To use Gmail for sending emails:

1. Enable 2-Factor Authentication on your Gmail account
2. Generate an App Password:
   - Go to Google Account → Security → 2-Step Verification → App passwords
   - Generate a new app password for "Mail"
3. Use the generated password in `SMTP_PASSWORD`

### 5. Run the Application

#### Development Mode (IntelliJ IDEA)

The application includes `application-default.yml` for easy development. Simply:

1. Ensure PostgreSQL is running with database `sri_karthikeya_caterers`
2. Click the **Run** button in IntelliJ IDEA ▶️

For more IntelliJ configuration options, see [RUN_CONFIGURATION.md](RUN_CONFIGURATION.md)

#### Development Mode (Command Line)

```bash
# Using Maven wrapper
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Or using Maven
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

#### Production Mode

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

The API will be available at `http://localhost:8080`

### 6. Build for Production

```bash
./mvnw clean package -DskipTests
```

The JAR file will be in `target/skc-0.0.1-SNAPSHOT.jar`

Run it:

```bash
java -jar target/skc-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

## API Documentation

### Public Endpoints

#### Submit Quote Request
```http
POST /api/public/quotes
Content-Type: application/json

{
  "name": "Priya Sharma",
  "email": "priya@example.com",
  "phone": "+91 98765 43211",
  "eventType": "wedding",
  "eventDate": "2026-09-14",
  "guests": 500,
  "venue": "Grand Palace, Hyderabad",
  "budget": "₹5,00,000",
  "message": "Pure-veg banquet, four live counters."
}
```

#### Subscribe to Newsletter
```http
POST /api/public/subscribe
Content-Type: application/json

{
  "email": "priya@example.com",
  "name": "Priya Sharma"
}
```

#### Get Review Invitation
```http
GET /api/public/reviews/{token}
```

#### Submit Review
```http
POST /api/public/reviews/{token}
Content-Type: application/json

{
  "reviewerName": "Priya Sharma",
  "overallRating": 5,
  "foodQualityRating": 5,
  "tasteRating": 5,
  "presentationRating": 4,
  "staffBehaviorRating": 5,
  "timelinessRating": 5,
  "serviceQualityRating": 5,
  "comments": "Genuinely the best veg catering we've used.",
  "suggestions": "More live mocktail variety.",
  "recommend": "yes"
}
```

#### Get Public Reviews
```http
GET /api/public/reviews?page=0&limit=24&minRating=4
```

#### Get Featured Reviews
```http
GET /api/public/reviews/featured?limit=6
```

### Admin Endpoints

#### Login
```http
POST /api/admin/auth/login
Content-Type: application/json

{
  "email": "info@srikarthikeyacaterers.in",
  "password": "your_password"
}
```

Response:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "expiresAt": "2026-05-05T10:00:00Z",
  "user": {
    "email": "info@srikarthikeyacaterers.in",
    "role": "ADMIN"
  }
}
```

#### Get Dashboard
```http
GET /api/admin/dashboard
Authorization: Bearer {token}
```

All admin endpoints require the `Authorization: Bearer {token}` header.

## Database Schema

The application uses 7 tables:

1. **clients** - Customer information
2. **quote_requests** - Quote requests from clients
3. **reviews** - Review invitations and submitted reviews
4. **subscribers** - Newsletter subscribers
5. **email_templates** - Email templates with JSON content
6. **email_campaigns** - Email campaign tracking
7. **system_logs** - Audit logs for all actions

See `src/main/resources/schema.sql` for the complete schema.

## Configuration Profiles

The application uses Spring profiles to manage different environments. Set the profile using the `SPRING_PROFILES_ACTIVE` environment variable.

### Local Profile (`application-local.yml`)
- **Profile Name**: `local` (default)
- Auto-creates database schema (`ddl-auto: update`)
- Verbose logging (DEBUG level)
- CORS enabled for localhost:3000 and localhost:5173
- Uses local PostgreSQL database
- Debug endpoints enabled
- Default values provided for easy development

**Activate**: Set `SPRING_PROFILES_ACTIVE=local` or run without setting (local is default)

### Production Profile (`application-prod.yml`)
- **Profile Name**: `prod`
- Schema validation only (`ddl-auto: validate`) - no auto-creation
- Minimal logging (INFO/WARN level)
- CORS restricted to production domain
- All secrets required via environment variables (no defaults)
- Debug endpoints disabled
- Optimized connection pooling

**Activate**: Set `SPRING_PROFILES_ACTIVE=prod`

## Security

- **Stateless JWT authentication** (no sessions)
- **BCrypt password hashing** (supports plain text for development)
- **CORS protection** with configurable origins
- **Input validation** on all endpoints
- **SQL injection protection** via JPA/Hibernate
- **Rate limiting ready** (Bucket4j included)

## Error Handling

All errors return a consistent format:

```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable message",
  "fields": {
    "fieldName": "Field-specific error"
  },
  "traceId": "uuid-for-tracking"
}
```

Error codes:
- `UNAUTHORIZED` - Invalid or missing JWT
- `TOKEN_EXPIRED` - JWT expired
- `VALIDATION_ERROR` - Request validation failed
- `NOT_FOUND` - Resource not found
- `CONFLICT` - Resource already exists
- `INTERNAL_ERROR` - Server error

## Logging

Logs are written to console with the following levels:

- **Local**: DEBUG for application, DEBUG for security
- **Production**: INFO for application, WARN for security

All important actions are logged to the `system_logs` table for audit purposes.

## Testing

Run tests:

```bash
./mvnw test
```

## Deployment

### Docker (Optional)

Create a `Dockerfile`:

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/skc-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Build and run:

```bash
docker build -t skc-backend .
docker run -p 8080:8080 --env-file .env skc-backend
```

### Cloud Deployment

The application is ready for deployment to:
- AWS Elastic Beanstalk
- Heroku
- Google Cloud Run
- Azure App Service

Set environment variables in your cloud platform's configuration.

## Support

For issues or questions, contact: info@srikarthikeyacaterers.in

## Troubleshooting

### Admin Login Issues

If you're experiencing login problems:

1. **Quick Fix Guide**: See [QUICK_FIX_LOGIN.md](QUICK_FIX_LOGIN.md) for immediate solutions
2. **Comprehensive Guide**: See [ADMIN_LOGIN_GUIDE.md](ADMIN_LOGIN_GUIDE.md) for detailed explanations

Common issues:
- "Admin email has no matching password" - Check `.env` has both `ADMIN_PASSWORD` and `ADMIN_PASSWORD_2`
- "Not getting token through mail" - JWT tokens are returned in HTTP response, NOT sent via email
- "Invalid credentials" - Verify password in `.env` matches what you're using

### Email System

For understanding how the email campaign system works, see:
- [EMAIL_SYSTEM_EXPLAINED.md](EMAIL_SYSTEM_EXPLAINED.md) - Comprehensive explanation
- [EMAIL_FLOW_DIAGRAM.md](EMAIL_FLOW_DIAGRAM.md) - Visual diagrams

### Other Guides

- [QUICKSTART.md](QUICKSTART.md) - Quick start guide
- [RUN_CONFIGURATION.md](RUN_CONFIGURATION.md) - IntelliJ IDEA setup
- [DEPLOYMENT.md](DEPLOYMENT.md) - Production deployment guide
- [IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md) - Technical implementation details
- [REVIEW_LINK_TESTING_GUIDE.md](REVIEW_LINK_TESTING_GUIDE.md) - Review invitation link testing
- [SMTP_SETUP_GUIDE.md](SMTP_SETUP_GUIDE.md) - Email configuration guide

## License

Proprietary - Sri Karthikeya Caterers
