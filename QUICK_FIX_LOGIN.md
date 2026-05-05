# Quick Fix: Admin Login Issue

## Problem
You're seeing this warning when trying to login with `srinivas.yanamandra04@gmail.com`:
```
WARN: Admin email 'srinivas.yanamandra04@gmail.com' has no matching password at index 1 (1 passwords)
```

## Root Cause
The application is only detecting 1 password in the configuration, but you have 2 admin emails.

## Solution Steps

### Step 1: Verify Your `.env` File

Open `skc/.env` and ensure BOTH passwords are set:

```bash
ADMIN_PASSWORD=admin123
ADMIN_PASSWORD_2=Srinu19#
```

### Step 2: Restart the Application

**Important:** Environment variables are loaded at startup. You MUST restart the application after changing `.env`.

1. Stop the running application (Ctrl+C or stop in IntelliJ)
2. Start it again

### Step 3: Verify Configuration Loaded Correctly

Call the debug endpoint:

```bash
curl http://localhost:8080/api/debug/config
```

You should see:
```json
{
  "adminEmailsCount": 2,
  "adminPasswordsCount": 2,
  "listsMatch": true
}
```

If `adminPasswordsCount` is still 1, the environment variable is not being loaded.

### Step 4: Test Login

```bash
curl -X POST http://localhost:8080/api/admin/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "srinivas.yanamandra04@gmail.com",
    "password": "Srinu19#"
  }'
```

Expected response:
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

## About "Not Getting Token Through Mail"

**Important Clarification:**

JWT authentication tokens are **NOT sent via email**. They are returned in the HTTP response body when you call the login API.

### What You Should Do:

1. ✅ Call the login API
2. ✅ Get the token from the response JSON
3. ✅ Use the token in the `Authorization: Bearer <token>` header

### What You Should NOT Expect:

1. ❌ Token sent to your email
2. ❌ Token in the database
3. ❌ Token in a file

### Email is Only Used For:

- Sending review invitation links to customers
- Sending email campaigns to clients/subscribers

## Changes Made

### 1. Fixed `AuthService.java`
- Removed confusing fallback logic
- Added strict validation: emails and passwords lists must have same size
- Added better error logging
- Application will now fail fast if configuration is wrong

### 2. Created Debug Endpoint
- `GET /api/debug/config` - Shows configuration status
- Only enabled in local/dev (disabled in production)
- Helps verify environment variables are loaded correctly

### 3. Updated Configuration
- Changed default for `ADMIN_PASSWORD_2` from `Srinu19#` to `changeme2`
- This ensures you're using the value from `.env`, not the default

## If Still Not Working

### Check Environment Variables in Terminal

Before running the app, verify in your terminal:

```bash
echo $ADMIN_PASSWORD
echo $ADMIN_PASSWORD_2
```

If these are empty, the variables aren't loaded. You may need to:

```bash
# Load .env file
export $(cat .env | xargs)

# Then run the application
./mvnw spring-boot:run
```

### Or Run from IntelliJ

IntelliJ automatically loads `.env` files if you have the EnvFile plugin, or you can:

1. Go to Run → Edit Configurations
2. Add environment variables manually:
   - `ADMIN_PASSWORD=admin123`
   - `ADMIN_PASSWORD_2=Srinu19#`

## Summary

1. ✅ Ensure `.env` has both `ADMIN_PASSWORD` and `ADMIN_PASSWORD_2`
2. ✅ Restart the application
3. ✅ Verify with debug endpoint
4. ✅ Test login - token is in HTTP response, NOT email
5. ✅ Use the token in Authorization header for subsequent requests

For detailed information, see `ADMIN_LOGIN_GUIDE.md`.
