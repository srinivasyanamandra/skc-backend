# Email Personalization Diagnostic Guide

## Issue
Emails sent to `srinivas.yanamandra04@gmail.com` show "Rajesh Kumar" instead of the correct name.

## Root Cause
The issue is **database data**, not code logic. The backend correctly uses `NameUtils` to resolve names with this priority:
1. Use stored `name` from database (if not blank)
2. Derive from email address
3. Fall back to "Valued Customer"

If you're seeing "Rajesh Kumar", it means there's a Client or Subscriber record in your database with:
- `email = 'srinivas.yanamandra04@gmail.com'`
- `name = 'Rajesh Kumar'` (incorrect data)

## How to Diagnose

### Step 1: Check Clients Table
```sql
SELECT id, name, email, phone, created_at 
FROM clients 
WHERE email ILIKE '%srinivas.yanamandra04%';
```

### Step 2: Check Subscribers Table
```sql
SELECT id, name, email, source, is_active, created_at 
FROM subscribers 
WHERE email ILIKE '%srinivas.yanamandra04%';
```

### Step 3: Check Campaign Recipients
If you already sent a campaign, check what data was stored:
```sql
SELECT 
    id, 
    name, 
    status,
    jsonb_pretty(recipients) as recipients_data
FROM email_campaigns 
WHERE recipients::text LIKE '%srinivas.yanamandra04%'
ORDER BY created_at DESC 
LIMIT 1;
```

## How to Fix

### Option 1: Update the Client Record
```sql
UPDATE clients 
SET name = 'Srinivas Yanamandra'  -- or NULL to auto-derive from email
WHERE email = 'srinivas.yanamandra04@gmail.com';
```

### Option 2: Update the Subscriber Record
```sql
UPDATE subscribers 
SET name = 'Srinivas Yanamandra'  -- or NULL to auto-derive from email
WHERE email = 'srinivas.yanamandra04@gmail.com';
```

### Option 3: Set Name to NULL (Auto-Derive)
If you want the system to automatically derive "Srinivas Yanamandra" from the email:
```sql
-- For clients
UPDATE clients 
SET name = NULL 
WHERE email = 'srinivas.yanamandra04@gmail.com';

-- For subscribers
UPDATE subscribers 
SET name = NULL 
WHERE email = 'srinivas.yanamandra04@gmail.com';
```

## How Name Derivation Works

When `name` is NULL or blank, `NameUtils.deriveFromEmail()` extracts the name:

```
srinivas.yanamandra04@gmail.com
→ Extract local part: "srinivas.yanamandra04"
→ Remove digits: "srinivas.yanamandra"
→ Split on separators: ["srinivas", "yanamandra"]
→ Title case each: ["Srinivas", "Yanamandra"]
→ Join with space: "Srinivas Yanamandra"
```

## Verify the Fix

### Test 1: Send a Test Email
1. Go to Email Builder in admin panel
2. Open any template
3. Click "Send test"
4. Enter `srinivas.yanamandra04@gmail.com`
5. Check the received email - should now show correct name

### Test 2: Check Campaign Preview
1. Go to Subscribers page
2. Select recipients including the email
3. Preview the campaign
4. Verify the name shows correctly

### Test 3: Check Backend Logs
When sending an email, check the application logs for:
```
Email sent to srinivas.yanamandra04@gmail.com (attempt 1, messageId ...)
```

## Prevention

To prevent this issue in the future:

1. **When importing clients**: Ensure name data is accurate
2. **When creating subscribers**: Validate name field or leave blank to auto-derive
3. **Data validation**: Add a check in your data import process

## Still Seeing the Issue?

If you've updated the database but still see "Rajesh Kumar":

1. **Clear browser cache**: The frontend might be caching old data
2. **Restart the backend**: Ensure changes are picked up
3. **Check all tables**: The email might exist in both `clients` AND `subscribers` tables
4. **Check campaign data**: If you're viewing an old campaign, it has snapshot data from when it was created

## Contact
If the issue persists after following these steps, check:
- Application logs: `logs/application.log`
- Database connection: Ensure you're connected to the correct database
- Environment: Verify `.env` file has correct database credentials
