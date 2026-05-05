# Review Link Testing Guide

## Overview

This guide explains how the review invitation link works and how to test it.

## Review Link Format

```
http://localhost:3000/#feedback?t=CuZB0QYs4sn_0ABnFv_NQQtQJhiNx22_3zDyzfj6zWk
```

**Components:**
- `http://localhost:3000` - Frontend URL
- `#feedback` - Frontend route (hash-based routing)
- `?t=TOKEN` - Review invitation token

## How It Works

### Step 1: Admin Creates Review Invitation

**Endpoint:** `POST /api/admin/reviews/invite`

**Request:**
```json
{
  "clientId": "uuid-of-client",
  "eventType": "wedding",
  "eventDate": "2026-05-01",
  "expiresInDays": 14
}
```

**Response:**
```json
{
  "id": "invitation-uuid",
  "token": "CuZB0QYs4sn_0ABnFv_NQQtQJhiNx22_3zDyzfj6zWk",
  "link": "http://localhost:3000/#feedback?t=CuZB0QYs4sn_0ABnFv_NQQtQJhiNx22_3zDyzfj6zWk",
  "expiresAt": "2026-05-15T00:00:00Z",
  "sentAt": "2026-05-01T10:30:00Z"
}
```

### Step 2: Customer Receives Email

The customer receives an email with the review link. When they click it, the frontend loads.

### Step 3: Frontend Validates Token

**Endpoint:** `GET /api/public/reviews/{token}`

**Example:**
```bash
curl http://localhost:8080/api/public/reviews/CuZB0QYs4sn_0ABnFv_NQQtQJhiNx22_3zDyzfj6zWk
```

**Success Response (200 OK):**
```json
{
  "valid": true,
  "client": {
    "name": "Srinivas Yanamandra"
  },
  "eventType": "wedding",
  "eventDate": "2026-05-01",
  "expiresAt": "2026-05-15T00:00:00Z"
}
```

**Error Responses:**

**410 Gone - Token expired or already used:**
```json
{
  "error": "EXPIRED_OR_USED",
  "message": "This review link has expired or has already been used.",
  "traceId": "uuid"
}
```

**404 Not Found - Token doesn't exist:**
```json
{
  "error": "NOT_FOUND",
  "message": "Review invitation not found.",
  "traceId": "uuid"
}
```

### Step 4: Customer Submits Review

**Endpoint:** `POST /api/public/reviews/{token}`

**Request:**
```json
{
  "reviewerName": "Srinivas Yanamandra",
  "overallRating": 5,
  "foodQualityRating": 5,
  "tasteRating": 5,
  "presentationRating": 4,
  "staffBehaviorRating": 5,
  "timelinessRating": 5,
  "serviceQualityRating": 5,
  "comments": "Excellent service and delicious food!",
  "suggestions": "More vegetarian options would be great.",
  "recommend": "yes"
}
```

**Success Response (201 Created):**
```json
{
  "id": "review-uuid",
  "submittedAt": "2026-05-01T15:30:00Z",
  "moderation": "pending",
  "message": "Thank you. Your review will be published after moderation."
}
```

**Error Responses:**

**410 Gone - Token expired or already used:**
```json
{
  "error": "EXPIRED_OR_USED",
  "message": "This review link has expired or has already been used.",
  "traceId": "uuid"
}
```

**400 Bad Request - Validation errors:**
```json
{
  "error": "VALIDATION_ERROR",
  "message": "Validation failed",
  "fields": {
    "reviewerName": "Reviewer name is required",
    "overallRating": "Overall rating must be between 1 and 5"
  },
  "traceId": "uuid"
}
```

## Testing the Review Link

### Test 1: Validate Token (GET)

```bash
curl -X GET http://localhost:8080/api/public/reviews/CuZB0QYs4sn_0ABnFv_NQQtQJhiNx22_3zDyzfj6zWk
```

**Expected:** 200 OK with invitation details

### Test 2: Submit Review (POST)

```bash
curl -X POST http://localhost:8080/api/public/reviews/CuZB0QYs4sn_0ABnFv_NQQtQJhiNx22_3zDyzfj6zWk \
  -H "Content-Type: application/json" \
  -d '{
    "reviewerName": "Srinivas Yanamandra",
    "overallRating": 5,
    "foodQualityRating": 5,
    "tasteRating": 5,
    "presentationRating": 5,
    "staffBehaviorRating": 5,
    "timelinessRating": 5,
    "serviceQualityRating": 5,
    "comments": "Excellent service!",
    "suggestions": "Keep up the good work!",
    "recommend": "yes"
  }'
```

**Expected:** 201 Created with review confirmation

### Test 3: Try Using Token Again (Should Fail)

```bash
curl -X GET http://localhost:8080/api/public/reviews/CuZB0QYs4sn_0ABnFv_NQQtQJhiNx22_3zDyzfj6zWk
```

**Expected:** 410 Gone - Token already used

## Security Features

### 1. One-Time Use Token
- Each token can only be used once
- After submission, `usedAt` timestamp is set
- Subsequent attempts return 410 Gone

### 2. Expiration
- Tokens expire after configured days (default: 14 days)
- Expired tokens return 410 Gone
- Expiration is checked on every request

### 3. Token Format
- URL-safe Base64 encoded
- 44 characters long
- Cryptographically secure random generation

### 4. Pessimistic Locking
- Uses database row locking during submission
- Prevents race conditions
- Ensures only one review per invitation

### 5. Validation
- All ratings must be 1-5
- Reviewer name is required (max 120 chars)
- Comments and suggestions are optional
- Recommend field is optional (max 10 chars)

## Frontend Integration

### React/Vue/Angular Example

```javascript
// 1. Extract token from URL
const urlParams = new URLSearchParams(window.location.hash.split('?')[1]);
const token = urlParams.get('t');

// 2. Validate token
const validateToken = async (token) => {
  try {
    const response = await fetch(`http://localhost:8080/api/public/reviews/${token}`);
    
    if (response.status === 410) {
      // Token expired or used
      showError('This review link has expired or has already been used.');
      return null;
    }
    
    if (!response.ok) {
      showError('Invalid review link.');
      return null;
    }
    
    const data = await response.json();
    return data;
  } catch (error) {
    showError('Failed to validate review link.');
    return null;
  }
};

// 3. Submit review
const submitReview = async (token, reviewData) => {
  try {
    const response = await fetch(`http://localhost:8080/api/public/reviews/${token}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(reviewData),
    });
    
    if (response.status === 410) {
      showError('This review link has expired or has already been used.');
      return null;
    }
    
    if (response.status === 400) {
      const error = await response.json();
      showValidationErrors(error.fields);
      return null;
    }
    
    if (!response.ok) {
      showError('Failed to submit review.');
      return null;
    }
    
    const data = await response.json();
    showSuccess('Thank you! Your review has been submitted.');
    return data;
  } catch (error) {
    showError('Failed to submit review.');
    return null;
  }
};

// Usage
const token = extractTokenFromURL();
const invitation = await validateToken(token);

if (invitation) {
  // Show review form with pre-filled data
  showReviewForm({
    clientName: invitation.client.name,
    eventType: invitation.eventType,
    eventDate: invitation.eventDate,
  });
}
```

## Database Schema

### reviews table

```sql
CREATE TABLE reviews (
    id UUID PRIMARY KEY,
    client_id UUID NOT NULL REFERENCES clients(id),
    type VARCHAR(20) NOT NULL, -- 'INVITATION' or 'REVIEW'
    event_type VARCHAR(40) NOT NULL,
    event_date DATE NOT NULL,
    
    -- Invitation fields
    token VARCHAR(64) UNIQUE,
    expires_at TIMESTAMP,
    sent_at TIMESTAMP,
    used_at TIMESTAMP,
    
    -- Review fields
    invitation_id UUID REFERENCES reviews(id),
    reviewer_name VARCHAR(120),
    overall_rating SMALLINT,
    food_quality_rating SMALLINT,
    taste_rating SMALLINT,
    presentation_rating SMALLINT,
    staff_behavior_rating SMALLINT,
    timeliness_rating SMALLINT,
    service_quality_rating SMALLINT,
    comments TEXT,
    suggestions TEXT,
    recommend VARCHAR(10),
    status VARCHAR(20) DEFAULT 'PENDING',
    is_featured BOOLEAN DEFAULT FALSE,
    is_public BOOLEAN DEFAULT FALSE,
    moderated_at TIMESTAMP,
    submitted_at TIMESTAMP,
    
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

## Common Issues

### Issue 1: Token Not Found (404)

**Cause:** Token doesn't exist in database

**Solution:**
- Verify the token is correct
- Check if invitation was created successfully
- Look for typos in the token

### Issue 2: Token Expired (410)

**Cause:** Token has passed expiration date

**Solution:**
- Admin needs to create a new invitation
- Check `expiresAt` timestamp in database

### Issue 3: Token Already Used (410)

**Cause:** Review has already been submitted with this token

**Solution:**
- Customer can only submit one review per invitation
- Admin can create a new invitation if needed

### Issue 4: CORS Error

**Cause:** Frontend domain not allowed

**Solution:**
- Add frontend URL to `cors.allowed-origins` in `application-local.yml`
- Current allowed origins: `http://localhost:3000`, `http://localhost:5173`

### Issue 5: Validation Errors (400)

**Cause:** Invalid data in review submission

**Solution:**
- Check all required fields are provided
- Ensure ratings are between 1-5
- Verify field lengths don't exceed limits

## Best Practices

### 1. Token Generation
- Use cryptographically secure random generation
- Minimum 32 bytes of entropy
- URL-safe Base64 encoding

### 2. Expiration
- Default: 14 days
- Adjust based on business needs
- Consider event date when setting expiration

### 3. Email Delivery
- Include clear call-to-action button
- Show expiration date in email
- Provide support contact if link doesn't work

### 4. User Experience
- Show clear error messages
- Pre-fill customer name if available
- Confirm submission with thank you message
- Explain moderation process

### 5. Security
- Always validate token server-side
- Use HTTPS in production
- Implement rate limiting
- Log all review submissions

## Production Checklist

- [ ] HTTPS enabled
- [ ] CORS configured for production domain
- [ ] Email SMTP configured correctly
- [ ] Database backups enabled
- [ ] Monitoring and alerting set up
- [ ] Rate limiting configured
- [ ] Error tracking enabled
- [ ] Review moderation workflow defined
- [ ] Customer support process documented
- [ ] Token expiration policy defined

## API Endpoints Summary

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/public/reviews/{token}` | None | Validate review invitation |
| POST | `/api/public/reviews/{token}` | None | Submit review |
| GET | `/api/public/reviews/public` | None | Get public reviews |
| GET | `/api/public/reviews/featured` | None | Get featured reviews |
| POST | `/api/admin/reviews/invite` | JWT | Create review invitation |
| GET | `/api/admin/reviews` | JWT | List all reviews |
| PUT | `/api/admin/reviews/{id}/moderate` | JWT | Moderate review |

## Support

For issues or questions:
- Check application logs for detailed error messages
- Verify database records in `reviews` table
- Test API endpoints directly with curl/Postman
- Contact: info@srikarthikeyacaterers.in
