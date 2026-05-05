# SMTP Email Setup Guide

## Current Issue

You're seeing this error:
```
Authentication failed: 535 5.7.8 Error: authentication failed
```

This means the SMTP credentials are incorrect or not configured.

## Solution Options

### Option 1: Hostinger SMTP (Recommended for Production)

Since your domain is `srikarthikeyacaterers.in` and email is `info@srikarthikeyacaterers.in`, you should use Hostinger's SMTP.

**Steps:**

1. **Get your Hostinger email password**:
   - Log in to Hostinger control panel
   - Go to Email Accounts
   - Find `info@srikarthikeyacaterers.in`
   - Use the password for this email account

2. **Update `.env` file**:
   ```bash
   MAIL_HOST=smtp.hostinger.com
   MAIL_PORT=587
   MAIL_USERNAME=info@srikarthikeyacaterers.in
   MAIL_PASSWORD=your_actual_hostinger_password
   ```

3. **Restart the application**

**Hostinger SMTP Settings:**
- Host: `smtp.hostinger.com`
- Port: `587` (STARTTLS) or `465` (SSL)
- Authentication: Required
- Username: Full email address
- Password: Email account password

---

### Option 2: Gmail SMTP (For Testing Only)

If you want to test with Gmail before setting up Hostinger:

**Steps:**

1. **Enable 2-Factor Authentication**:
   - Go to: https://myaccount.google.com/security
   - Enable 2-Step Verification

2. **Generate App Password**:
   - Go to: https://myaccount.google.com/apppasswords
   - Select "Mail" and your device
   - Click "Generate"
   - Copy the 16-character password (e.g., `abcd efgh ijkl mnop`)

3. **Update `.env` file**:
   ```bash
   MAIL_HOST=smtp.gmail.com
   MAIL_PORT=587
   MAIL_USERNAME=srinivas.yanamandra04@gmail.com
   MAIL_PASSWORD=abcdefghijklmnop
   ```
   (Remove spaces from the app password)

4. **Restart the application**

**Gmail SMTP Settings:**
- Host: `smtp.gmail.com`
- Port: `587` (TLS) or `465` (SSL)
- Authentication: Required
- Username: Full Gmail address
- Password: App password (NOT your regular Gmail password)

---

### Option 3: Disable Email for Testing

If you just want to test the application without sending emails:

**Update `.env` file**:
```bash
MAIL_DISABLED=true
```

This will skip actual email sending but still create review invitations and campaigns.

---

## Current Configuration

Your `application-local.yml` is configured for Hostinger:

```yaml
spring:
  mail:
    host: smtp.hostinger.com
    port: 587
    username: ${MAIL_USERNAME:info@srikarthikeyacaterers.in}
    password: ${MAIL_PASSWORD:change-me-local}
```

The password `change-me-local` is a placeholder. You MUST replace it with the actual password.

---

## How to Update

### Step 1: Choose Your SMTP Provider

- **Hostinger** - For production use with your domain email
- **Gmail** - For testing only

### Step 2: Update `.env` File

Open `skc/.env` and update these lines:

```bash
# For Hostinger:
MAIL_HOST=smtp.hostinger.com
MAIL_PORT=587
MAIL_USERNAME=info@srikarthikeyacaterers.in
MAIL_PASSWORD=your_actual_password_here

# OR for Gmail:
# MAIL_HOST=smtp.gmail.com
# MAIL_PORT=587
# MAIL_USERNAME=srinivas.yanamandra04@gmail.com
# MAIL_PASSWORD=your_gmail_app_password
```

### Step 3: Restart Application

Stop and restart the Spring Boot application to load the new configuration.

### Step 4: Test Email Sending

Try sending a review invitation again. Check the logs for:

```
✅ SUCCESS: Email sent successfully to recipient@example.com
```

Instead of:

```
❌ ERROR: Authentication failed
```

---

## Troubleshooting

### Issue: Still getting "Authentication failed"

**Possible causes:**
1. Wrong password - Double-check you're using the correct password
2. Special characters - Some passwords with special characters need escaping
3. Wrong username - Must be the full email address
4. Wrong host/port - Verify SMTP server settings

**Solution:**
- Try copying the password directly from your email provider
- Remove any spaces or line breaks
- Verify the email account exists and is active

### Issue: "Connection timeout"

**Possible causes:**
1. Firewall blocking port 587
2. Wrong SMTP host
3. Network issues

**Solution:**
- Try port 465 instead of 587
- Check if your network allows SMTP connections
- Test with `telnet smtp.hostinger.com 587`

### Issue: "Must issue a STARTTLS command first"

**Possible causes:**
- Wrong port or SSL/TLS configuration

**Solution:**
- Ensure `starttls.enable: true` in configuration
- Use port 587 for STARTTLS or 465 for SSL

---

## Testing Email Configuration

You can test your SMTP settings using this curl command:

```bash
curl -v --url 'smtp://smtp.hostinger.com:587' \
  --mail-from 'info@srikarthikeyacaterers.in' \
  --mail-rcpt 'test@example.com' \
  --user 'info@srikarthikeyacaterers.in:your_password' \
  --upload-file - <<EOF
From: info@srikarthikeyacaterers.in
To: test@example.com
Subject: Test Email

This is a test email.
EOF
```

If this works, your SMTP credentials are correct.

---

## Security Notes

1. **Never commit `.env` file** - It's already in `.gitignore`
2. **Use app passwords** - For Gmail, always use app passwords, not your main password
3. **Rotate passwords** - Change SMTP passwords periodically
4. **Use environment variables** - In production, use your hosting platform's environment variable system

---

## Need Help?

If you're still having issues:

1. Check the application logs for detailed error messages
2. Verify your email account is active and not locked
3. Contact your email provider (Hostinger) for SMTP settings
4. Try the "Disable Email" option to test other features first
