# Email System - Complete Explanation

## Overview

The email system sends emails to **clients** (customers who requested quotes) and **subscribers** (newsletter subscribers) through a sophisticated campaign management system. It supports:

- **Template-based emails** with variable substitution
- **Mixed-audience campaigns** (clients + subscribers in one campaign)
- **Per-recipient template customization**
- **Async sending with retry logic**
- **Throttling** to avoid SMTP rate limits
- **Delivery tracking** and audit logging

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Campaign Flow                            │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  1. CREATE CAMPAIGN                                          │
│     └─> Draft campaign with name + default template         │
│                                                              │
│  2. ADD RECIPIENTS                                           │
│     ├─> Select clients (from quote_requests)                │
│     ├─> Select subscribers (from subscribers table)         │
│     └─> Materialise into JSONB snapshot                     │
│                                                              │
│  3. ASSIGN TEMPLATES                                         │
│     ├─> Default template (for all)                          │
│     ├─> Per-kind template (clients vs subscribers)          │
│     └─> Per-recipient override (specific individuals)       │
│                                                              │
│  4. PREVIEW                                                  │
│     └─> Render sample emails with actual variables          │
│                                                              │
│  5. SEND / SCHEDULE                                          │
│     ├─> Immediate: dispatch now                             │
│     └─> Scheduled: queue for later                          │
│                                                              │
│  6. DISPATCH (Async)                                         │
│     ├─> Iterate recipients                                  │
│     ├─> Resolve template per recipient                      │
│     ├─> Merge variables                                     │
│     ├─> Render email (HTML + text)                          │
│     ├─> Send via SMTP (with retry)                          │
│     ├─> Update delivery status                              │
│     └─> Log to system_logs                                  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## Core Components

### 1. EmailService (Low-Level Email Sending)

**Location**: `src/main/java/syncqubits/ai/skc/service/EmailService.java`

**Responsibilities**:
- Template rendering with `{{variable}}` substitution
- SMTP email sending
- Retry logic with exponential backoff
- Delivery tracking and logging

**Key Methods**:

#### `render(EmailTemplate template, Map<String, Object> variables)`
Renders an email template by substituting variables.

```java
// Example:
EmailTemplate template = ...;
Map<String, Object> vars = Map.of(
    "clientName", "Priya Sharma",
    "eventDate", "2026-09-14",
    "reviewLink", "https://example.com/review/abc123"
);

RenderedEmail rendered = emailService.render(template, vars);
// Result:
// - subject: "Thank you, Priya Sharma!"
// - html: "<h1>Thank you, Priya Sharma!</h1>..."
// - text: "Thank you, Priya Sharma!..."
```

**Variable Substitution**:
- Pattern: `{{variableName}}` or `{{ variableName }}`
- Supports nested: `{{user.name}}`, `{{order.items.0.price}}`
- Missing variables → empty string

#### `sendNow(template, toEmail, toName, variables, entityType, entityId)`
Sends an email synchronously with retry logic.

```java
EmailSendResult result = emailService.sendNow(
    template,
    "priya@example.com",
    "Priya Sharma",
    variables,
    "client",
    clientId
);

if (result.isSuccess()) {
    log.info("Email sent! Message ID: {}", result.getMessageId());
} else {
    log.error("Failed after {} attempts: {}", result.getAttempts(), result.getError());
}
```

**Retry Logic**:
- Default: 2 attempts
- Backoff: 1 second × attempt number
- Logs each attempt to `system_logs`

#### `sendAsync(...)` 
Same as `sendNow()` but returns `CompletableFuture` for async execution.

---

### 2. CampaignService (High-Level Campaign Management)

**Location**: `src/main/java/syncqubits/ai/skc/service/CampaignService.java`

**Responsibilities**:
- Campaign CRUD operations
- Recipient management (clients + subscribers)
- Template assignment (3-tier resolution)
- Campaign dispatch orchestration
- Throttling and batch processing

---

## Campaign Workflow (Step by Step)

### Step 1: Create Campaign

```http
POST /api/admin/campaigns
{
  "name": "May 2026 Newsletter",
  "defaultTemplateId": "tpl-uuid-123",
  "globalVariables": {
    "month": "May 2026",
    "companyName": "Sri Karthikeya Caterers"
  }
}
```

**What happens**:
1. Creates `EmailCampaign` entity with status `DRAFT`
2. Sets default template
3. Initializes empty recipient list
4. Logs to `system_logs`

---

### Step 2: Add Recipients

```http
POST /api/admin/campaigns/{id}/recipients
{
  "mode": "replace",  // or "append"
  "include": [
    { "kind": "client", "id": "client-uuid-1" },
    { "kind": "subscriber", "id": "sub-uuid-2" }
  ],
  "segments": [
    {
      "kind": "subscribers",
      "filter": { "active": true }
    },
    {
      "kind": "clients",
      "filter": { "status": "booked", "since": "2026-01-01" }
    }
  ],
  "exclude": [
    { "kind": "subscriber", "id": "unsubscribed-uuid" }
  ]
}
```

**What happens**:

1. **RecipientResolver** materialises the recipient list:
   - Fetches clients from `clients` table
   - Fetches subscribers from `subscribers` table
   - Applies filters (status, date range, etc.)
   - Deduplicates by email
   - Removes inactive subscribers

2. **Snapshot Creation**:
   Each recipient becomes a JSON object stored in `email_campaigns.recipients`:
   ```json
   {
     "kind": "client",
     "id": "uuid",
     "email": "priya@example.com",
     "name": "Priya Sharma",
     "phone": "+91 9876543210",
     "tags": ["wedding", "booked"],
     "deliveryStatus": "pending"
   }
   ```

3. **Why Snapshot?**
   - Campaign shows who **actually** received the email
   - Even if subscriber unsubscribes later, history is preserved
   - Audit trail for compliance

---

### Step 3: Assign Templates

```http
PUT /api/admin/campaigns/{id}/templates
{
  "defaultTemplateId": "tpl-newsletter",
  "byKindTemplate": {
    "client": "tpl-client-followup",
    "subscriber": "tpl-newsletter"
  },
  "perRecipient": [
    {
      "kind": "client",
      "id": "vip-client-uuid",
      "templateId": "tpl-vip-thankyou",
      "variables": {
        "freebie": "complimentary mocktail counter"
      }
    }
  ]
}
```

**Template Resolution (3-Tier Priority)**:

When sending to a recipient, the system resolves the template in this order:

1. **Per-Recipient Override** (highest priority)
   - Check if recipient has `templateId` in their JSON entry
   - Use: VIP clients, special offers, personalized messages

2. **By-Kind Template**
   - Check `config.byKindTemplate[recipient.kind]`
   - Use: Different messages for clients vs subscribers

3. **Default Template** (fallback)
   - Use `campaign.defaultTemplate`
   - Use: Standard newsletter for everyone

**Example**:
```
Recipient: { kind: "client", id: "abc", email: "priya@example.com" }

Resolution:
1. Check perRecipient[kind=client, id=abc] → NOT FOUND
2. Check byKindTemplate["client"] → FOUND: "tpl-client-followup"
3. Use: "tpl-client-followup" ✓
```

---

### Step 4: Preview

```http
POST /api/admin/campaigns/{id}/preview
{
  "recipient": { "kind": "client", "id": "abc" }
  // OR
  "sample": 3  // random 3 recipients
}
```

**What happens**:
1. Resolves template for the recipient(s)
2. Merges variables (global + per-recipient)
3. Renders the email (HTML + text)
4. Returns preview without sending

**Response**:
```json
{
  "renders": [
    {
      "recipient": {
        "kind": "client",
        "id": "abc",
        "email": "priya@example.com",
        "name": "Priya Sharma"
      },
      "template": {
        "id": "tpl-client-followup",
        "name": "Client Follow-up"
      },
      "subject": "Thank you for choosing us, Priya!",
      "html": "<h1>Thank you, Priya Sharma!</h1>...",
      "text": "Thank you, Priya Sharma!...",
      "variablesResolved": {
        "clientName": "Priya Sharma",
        "month": "May 2026",
        "reviewLink": "https://..."
      }
    }
  ]
}
```

---

### Step 5: Send / Schedule

```http
POST /api/admin/campaigns/{id}/send
{
  "scheduleAt": null,  // null = send now, ISO-8601 = schedule
  "throttle": {
    "perMinute": 120
  }
}
```

**What happens**:

1. **Validation**:
   - Campaign has recipients? ✓
   - Campaign has default template? ✓
   - Campaign not already sent? ✓

2. **Status Update**:
   - Set status to `QUEUED`
   - Set `scheduledAt` timestamp
   - Reset `sentCount` and `failedCount` to 0

3. **Dispatch**:
   - If immediate: call `dispatchAsync()` in background
   - If scheduled: CampaignScheduler picks it up later

4. **Response** (202 Accepted):
   ```json
   {
     "campaignId": "uuid",
     "status": "queued",
     "totalRecipients": 1832,
     "scheduledFor": "2026-05-03T10:35:00Z"
   }
   ```

---

### Step 6: Dispatch (The Actual Sending)

**Method**: `CampaignService.dispatch(UUID campaignId)`

**Flow**:

```java
public void dispatch(UUID id) {
    // 1. Load campaign
    EmailCampaign campaign = load(id);
    
    // 2. Set status to SENDING
    campaign.setStatus(SENDING);
    campaign.setStartedAt(Instant.now());
    save(campaign);
    
    // 3. Get throttle settings
    int throttlePerMinute = 120;  // from config or campaign
    long delayMs = 60_000 / throttlePerMinute;  // 500ms between emails
    
    // 4. Iterate recipients
    List<Map<String, Object>> recipients = campaign.getRecipients();
    int sent = 0, failed = 0;
    
    for (int i = 0; i < recipients.size(); i++) {
        Map<String, Object> recipient = recipients.get(i);
        
        // Skip if already sent (resume-safe)
        if ("sent".equals(recipient.get("deliveryStatus"))) {
            continue;
        }
        
        // Check for cancellation every batch
        if (i % 50 == 0) {
            EmailCampaign refreshed = load(id);
            if (refreshed.getStatus() == CANCELLED) {
                log.info("Campaign cancelled, stopping");
                return;
            }
        }
        
        try {
            // 5. Resolve template for this recipient
            EmailTemplate template = resolveTemplateForEntry(campaign, recipient);
            
            // 6. Merge variables
            Map<String, Object> vars = mergeVariables(campaign, recipient);
            
            // 7. Render email
            RenderedEmail rendered = emailService.render(template, vars);
            
            // 8. Send email
            String email = (String) recipient.get("email");
            String name = (String) recipient.get("name");
            
            EmailSendResult result = emailService.sendRendered(
                rendered, email, name, template.getId(), 
                "email_campaign", campaign.getId()
            );
            
            // 9. Update recipient status
            if (result.isSuccess()) {
                recipient.put("deliveryStatus", "sent");
                recipient.put("sentAt", Instant.now().toString());
                recipient.put("messageId", result.getMessageId());
                sent++;
            } else {
                recipient.put("deliveryStatus", "failed");
                recipient.put("error", result.getError());
                failed++;
            }
            
        } catch (Exception e) {
            recipient.put("deliveryStatus", "failed");
            recipient.put("error", e.getMessage());
            failed++;
        }
        
        // 10. Save progress every 50 recipients
        if (i % 50 == 0 || i == recipients.size() - 1) {
            campaign.setRecipients(recipients);
            campaign.setSentCount(sent);
            campaign.setFailedCount(failed);
            save(campaign);
        }
        
        // 11. Throttle
        if (delayMs > 0) {
            Thread.sleep(delayMs);
        }
    }
    
    // 12. Mark complete
    campaign.setStatus(sent > 0 ? SENT : FAILED);
    campaign.setCompletedAt(Instant.now());
    save(campaign);
    
    // 13. Log completion
    systemLogService.logCampaign("completed", "success", id, 
        Map.of("sentCount", sent, "failedCount", failed));
}
```

---

## Variable Merging

**Priority** (highest to lowest):

1. **Per-Recipient Variables** (from `perRecipient[].variables`)
2. **Global Variables** (from `campaign.globalVariables`)
3. **Auto-Injected Context**:
   ```java
   {
     "clientName": recipient.name,
     "name": recipient.name,
     "email": recipient.email,
     "kind": recipient.kind,
     "reviewLink": reviewBaseUrl,
     "campaignId": campaign.id,
     "campaignName": campaign.name
   }
   ```

**Example**:

```java
// Campaign global variables
{
  "month": "May 2026",
  "companyName": "Sri Karthikeya Caterers"
}

// Per-recipient override
{
  "freebie": "complimentary mocktail counter"
}

// Recipient data
{
  "kind": "client",
  "email": "priya@example.com",
  "name": "Priya Sharma"
}

// Final merged variables:
{
  "month": "May 2026",
  "companyName": "Sri Karthikeya Caterers",
  "freebie": "complimentary mocktail counter",
  "clientName": "Priya Sharma",
  "name": "Priya Sharma",
  "email": "priya@example.com",
  "kind": "client",
  "reviewLink": "http://localhost:3000",
  "campaignId": "uuid",
  "campaignName": "May 2026 Newsletter"
}
```

---

## Throttling

**Purpose**: Avoid SMTP rate limits (e.g., Gmail: 500 emails/day, 100/hour)

**Configuration**:
```yaml
campaign:
  throttle:
    per-minute: 120  # default
```

**Per-Campaign Override**:
```json
{
  "throttle": {
    "perMinute": 60  // slower for this campaign
  }
}
```

**Calculation**:
```java
int perMinute = 120;
long delayMs = 60_000 / perMinute;  // 500ms between emails

// In dispatch loop:
for (recipient : recipients) {
    sendEmail(recipient);
    Thread.sleep(delayMs);  // 500ms pause
}
```

**Example**:
- 120 emails/minute = 1 email every 500ms
- 60 emails/minute = 1 email every 1000ms
- 1832 recipients at 120/min = ~15 minutes

---

## Delivery Tracking

### Per-Recipient Status

Each recipient in `campaign.recipients` JSONB has:

```json
{
  "kind": "client",
  "id": "uuid",
  "email": "priya@example.com",
  "name": "Priya Sharma",
  "deliveryStatus": "sent",  // pending | sent | failed
  "sentAt": "2026-05-03T10:36:14Z",
  "messageId": "<abc@smtp.gmail.com>",
  "error": null  // or error message if failed
}
```

### System Logs

Every send attempt creates a `system_logs` entry:

```sql
INSERT INTO system_logs (
  type, entity_type, entity_id, action, status, details
) VALUES (
  'email',
  'email_campaign',
  'campaign-uuid',
  'sent',
  'success',
  '{
    "recipientEmail": "priya@example.com",
    "recipientName": "Priya Sharma",
    "subject": "Thank you!",
    "messageId": "<abc@smtp.gmail.com>",
    "attempts": 1,
    "templateId": "tpl-uuid"
  }'
);
```

### Campaign Aggregate Counts

```java
campaign.sentCount = 1828;
campaign.failedCount = 4;
campaign.totalRecipients = 1832;
```

---

## Resume Safety

If the application crashes mid-campaign:

1. **Status Check**: Campaign status is `SENDING`
2. **Recipient Check**: Each recipient has `deliveryStatus`
3. **Resume**: Scheduler picks up and continues from where it stopped
4. **Skip Sent**: Recipients with `deliveryStatus = "sent"` are skipped

```java
for (recipient : recipients) {
    if ("sent".equals(recipient.get("deliveryStatus"))) {
        continue;  // already sent, skip
    }
    sendEmail(recipient);
}
```

---

## Cancellation

```http
POST /api/admin/campaigns/{id}/cancel
```

**What happens**:
1. Set status to `CANCELLED`
2. Dispatch loop checks status every batch (50 recipients)
3. If cancelled, stops immediately
4. Already-sent emails remain sent
5. Pending emails are not sent

---

## Send One-Off Email

For sending a single email outside of campaigns:

```http
POST /api/admin/emails/send-one
{
  "to": {
    "kind": "client",
    "id": "client-uuid"
  },
  "templateId": "tpl-quote-followup",
  "variables": {
    "quoteId": "quote-uuid"
  }
}
```

**Use cases**:
- Quote confirmation
- Review invitation
- One-off thank you
- Custom message to specific client

---

## Key Design Decisions

### 1. Why Snapshot Recipients?

**Problem**: If a subscriber unsubscribes after campaign is sent, we lose history.

**Solution**: Materialise recipient list into JSONB at send time.

**Benefits**:
- Audit trail: "Who actually received this email?"
- Compliance: GDPR requires knowing who was contacted
- Resume safety: Can restart from exact state

### 2. Why 3-Tier Template Resolution?

**Problem**: Different audiences need different messages.

**Solution**: Per-recipient > By-kind > Default

**Benefits**:
- Flexibility: VIP clients get special template
- Simplicity: Most recipients use default
- Maintainability: Change default affects everyone

### 3. Why Async Dispatch?

**Problem**: Sending 1832 emails takes ~15 minutes. Can't block HTTP request.

**Solution**: Return 202 Accepted immediately, dispatch in background.

**Benefits**:
- Responsive API
- Cancellable mid-flight
- Resume-safe on crash

### 4. Why Batch Saves?

**Problem**: Saving after every email is slow (1832 DB writes).

**Solution**: Save every 50 recipients.

**Benefits**:
- Performance: 37 DB writes instead of 1832
- Resume safety: At most 50 emails lost on crash
- Progress tracking: UI can show real-time progress

---

## Summary

The email system is a **production-grade campaign management platform** that:

✅ Sends emails to **clients** and **subscribers**  
✅ Supports **mixed audiences** in one campaign  
✅ Allows **per-recipient customization**  
✅ Handles **thousands of recipients** efficiently  
✅ Provides **delivery tracking** and audit logs  
✅ Is **resume-safe** and **cancellable**  
✅ Respects **SMTP rate limits** with throttling  
✅ Logs **every action** for compliance  

**Key Files**:
- `EmailService.java` - Low-level sending + rendering
- `CampaignService.java` - High-level campaign orchestration
- `RecipientResolver.java` - Materialises client + subscriber lists
- `CampaignScheduler.java` - Picks up scheduled campaigns

**Database Tables**:
- `email_campaigns` - Campaign metadata + recipient snapshot
- `email_templates` - Reusable email templates
- `clients` - Quote request customers
- `subscribers` - Newsletter subscribers
- `system_logs` - Audit trail for every send
