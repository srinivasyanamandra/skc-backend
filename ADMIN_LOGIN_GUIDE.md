# Admin Login Guide

## Overview

This guide explains how admin authentication works in the Sri Karthikeya Caterers backend and how to troubleshoot login issues.

## How Admin Login Works

### 1. Hardcoded Admin Credentials

Admin credentials are configured in `application.yml` using environment variables:

```yaml
admin:
  emails:
    - info@srikarthikeyacaterers.in
    - srinivas.yanamandra04@gmail.com
  passwords:
    - ${ADMIN_PASSWORD:changeme}
    - ${ADMIN_PASSWORD_2:changeme2}
```

**Important**: The emails and passwords lists MUST have the same number of entries. Each email at index `i` is matched with the password at index `i`.

### 2. JWT Token Response

When you successfully login, the JWT token is **returned in the HTTP response body**, NOT sent via email.

**Login Request:**
```bash
POST /api/admin/auth/login
Content-Type: application/json

{
  "email": "srinivas.yanamandra04@gmail.com",
  "password": "Srinu19#"
}
```

**Login Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresAt": "2026-05-05T19:38:00Z",
  "user": {
    "email": "srinivas.yanamandra04@gmail.com",
    "role": "ADMIN"
  }
}
```

**Use the token in subsequent requests:**
```bash
GET /api/admin/dashboard
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### 3. Email Tokens vs JWT Tokens

**Don't confuse these two different types of tokens:**

| Feature | JWT Token | Review Invitation Token |
|---------|-----------|------------------------|
| Purpose | Admin authentication | Customer review submission |
| How obtained | Login API response | Sent via email to customer |
| Validity | 24 hours | 14 days (configurable) |
| Used for | Admin API access | Submitting reviews |
| Endpoint | `/api/admin/auth/login` | `/api/public/reviews/submit` |

## Troubleshooting Login Issues

### Issue 1: "Admin email has no matching password at index X"

**Symptom:**
```
WARN: Admin email 'srinivas.yanamandra04@gmail.com' has no matching password at index 1 (1 passwords)
```

**Cause:** The `admin.passwords` list has fewer entries than `admin.emails` list.

**Solution:**

1. **Check your `.env` file** - ensure both passwords are set:
   ```bash
   ADMIN_PASSWORD=admin123
   ADMIN_PASSWORD_2=Srinu19#
   ```

2. **Verify environment variables are loaded:**
   ```bash
   # In your terminal before running the app
   echo $ADMIN_PASSWORD
   echo $ADMIN_PASSWORD_2
   ```

3. **Check the debug endpoint** (only works in local/dev):
   ```bash
   curl http://localhost:8080/api/debug/config
   ```
   
   Expected response:
   ```json
   {
     "adminEmailsCount": 2,
     "adminPasswordsCount": 2,
     "adminEmails": [
       "info@srikarthikeyacaterers.in",
       "srinivas.yanamandra04@gmail.com"
     ],
     "adminPasswordsSet": ["***SET***", "***SET***"],
     "listsMatch": true
   }
   ```

4. **Restart the application** after changing `.env` file:
   - Stop the running application
   - Reload environment variables
   - Start the application again

### Issue 2: "Invalid credentials" error

**Cause:** Password doesn't match the configured value.

**Solution:**

1. **Verify the password in `.env`:**
   ```bash
   cat .env | grep ADMIN_PASSWORD
   ```

2. **Check for special characters** - ensure your password doesn't have characters that need escaping in YAML or shell.

3. **Try the first admin account** to verify the system works:
   ```json
   {
     "email": "info@srikarthikeyacaterers.in",
     "password": "admin123"
   }
   ```

4. **Check application logs** for detailed error messages:
   ```
   INFO: Login attempt for email: srinivas.yanamandra04@gmail.com
   DEBUG: Validating password for admin email 'srinivas.yanamandra04@gmail.com' at index 1
   ```

### Issue 3: "Not getting token through mail"

**This is a misunderstanding!**

JWT authentication tokens are **NOT sent via email**. They are returned in the HTTP response body when you call the login API.

**What you should do:**

1. Call the login API using Postman, curl, or your frontend
2. Extract the `token` field from the response JSON
3. Use that token in the `Authorization: Bearer <token>` header for subsequent API calls

**Email is only used for:**
- Sending review invitation links to customers
- Sending email campaigns to clients and subscribers

## Configuration Files

### Local Development (`.env`)

```bash
# Admin Credentials
ADMIN_PASSWORD=admin123
ADMIN_PASSWORD_2=Srinu19#

# JWT Secret
JWT_SECRET=sri-karthikeya-caterers-jwt-secret-key-2026-production-grade

# Database
DB_USERNAME=postgres
DB_PASSWORD=postgres

# Active Profile
SPRING_PROFILES_ACTIVE=local
```

### Application Configuration (`application.yml`)

```yaml
admin:
  emails:
    - info@srikarthikeyacaterers.in
    - srinivas.yanamandra04@gmail.com
  passwords:
    - ${ADMIN_PASSWORD:changeme}
    - ${ADMIN_PASSWORD_2:changeme2}

jwt:
  secret: ${JWT_SECRET:your-secret-key-must-be-at-least-32-bytes-long-for-hs256}
  expiration: 86400000  # 24 hours
```

## Testing Login with Postman

### Step 1: Import the Postman Collection

The project includes `postman_collection.json` with pre-configured requests.

### Step 2: Test Login

1. Open the "Admin Login" request
2. Update the request body:
   ```json
   {
     "email": "srinivas.yanamandra04@gmail.com",
     "password": "Srinu19#"
   }
   ```
3. Click "Send"
4. Copy the `token` from the response

### Step 3: Test Authenticated Request

1. Open the "Get Dashboard" request
2. Go to the "Authorization" tab
3. Select "Bearer Token"
4. Paste the token from Step 2
5. Click "Send"

## Security Notes

### Password Storage

- Passwords can be stored as **plain text** or **BCrypt hashed**
- The system auto-detects BCrypt hashes (start with `$2a$`, `$2b$`, or `$2y$`)
- For production, use BCrypt hashed passwords:
  ```bash
  # Generate BCrypt hash (you can use online tools or Spring Boot CLI)
  # Example: $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
  ```

### Environment Variables in Production

Never commit `.env` file to version control. In production:

1. Set environment variables in your deployment platform (Heroku, AWS, etc.)
2. Or use a secrets management service (AWS Secrets Manager, HashiCorp Vault, etc.)

## Common Mistakes

1. ❌ **Expecting JWT token via email** - Tokens are in HTTP response
2. ❌ **Mismatched list sizes** - Emails and passwords must have same count
3. ❌ **Not restarting after .env changes** - Environment variables are loaded at startup
4. ❌ **Wrong password** - Check for typos, special characters, or copy-paste errors
5. ❌ **Using review tokens for admin APIs** - Review tokens are different from JWT tokens

## Need Help?

1. Check the debug endpoint: `GET /api/debug/config`
2. Review application logs for detailed error messages
3. Verify `.env` file has both `ADMIN_PASSWORD` and `ADMIN_PASSWORD_2`
4. Ensure the application was restarted after configuration changes
5. Test with the first admin account to verify the system works
