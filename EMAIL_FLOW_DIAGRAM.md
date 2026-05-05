# Email System - Visual Flow Diagrams

## 1. Campaign Creation to Sending

```
┌─────────────────────────────────────────────────────────────────────┐
│                         ADMIN CREATES CAMPAIGN                       │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
                    ┌────────────────┐
                    │  EmailCampaign │
                    │  status: DRAFT │
                    │  recipients: []│
                    └────────┬───────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         ADD RECIPIENTS                               │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐         │
│  │   Clients    │    │ Subscribers  │    │   Filters    │         │
│  │ (from quotes)│───▶│ (newsletter) │───▶│  & Segments  │         │
│  └──────────────┘    └──────────────┘    └──────┬───────┘         │
│                                                   │                  │
│                                                   ▼                  │
│                                    ┌──────────────────────┐         │
│                                    │ RecipientResolver    │         │
│                                    │ - Fetch from DB      │         │
│                                    │ - Apply filters      │         │
│                                    │ - Deduplicate        │         │
│                                    │ - Remove inactive    │         │
│                                    └──────────┬───────────┘         │
│                                               │                      │
│                                               ▼                      │
│                                    ┌──────────────────────┐         │
│                                    │ Materialised List    │         │
│                                    │ (JSONB snapshot)     │         │
│                                    │ [{kind, id, email,   │         │
│                                    │   name, status}]     │         │
│                                    └──────────┬───────────┘         │
└───────────────────────────────────────────────┼─────────────────────┘
                                                │
                                                ▼
                                    ┌──────────────────────┐
                                    │  campaign.recipients │
                                    │  = [1832 recipients] │
                                    └──────────┬───────────┘
                                               │
                                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                       ASSIGN TEMPLATES                               │
│                                                                      │
│  ┌────────────────────┐                                             │
│  │ Default Template   │ ◀─── Used for most recipients              │
│  └────────────────────┘                                             │
│                                                                      │
│  ┌────────────────────┐                                             │
│  │ By-Kind Templates  │ ◀─── Different for clients vs subscribers  │
│  │ - client: tpl-A    │                                             │
│  │ - subscriber: tpl-B│                                             │
│  └────────────────────┘                                             │
│                                                                      │
│  ┌────────────────────┐                                             │
│  │ Per-Recipient      │ ◀─── VIP clients, special offers           │
│  │ Overrides          │                                             │
│  │ - client-123: tpl-C│                                             │
│  └────────────────────┘                                             │
└───────────────────────────────────┬─────────────────────────────────┘
                                    │
                                    ▼
                        ┌───────────────────────┐
                        │   PREVIEW (Optional)  │
                        │   - Render samples    │
                        │   - Check variables   │
                        └───────────┬───────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                            SEND / SCHEDULE                           │
│                                                                      │
│  POST /api/admin/campaigns/{id}/send                                │
│  {                                                                   │
│    "scheduleAt": null,  // or ISO-8601 timestamp                    │
│    "throttle": { "perMinute": 120 }                                 │
│  }                                                                   │
│                                                                      │
│  ┌─────────────────┐                                                │
│  │ Status: QUEUED  │                                                │
│  │ scheduledAt: now│                                                │
│  └────────┬────────┘                                                │
└───────────┼─────────────────────────────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      DISPATCH (Async Background)                     │
│                                                                      │
│  Status: SENDING                                                     │
│  startedAt: now                                                      │
│                                                                      │
│  FOR EACH recipient IN recipients:                                  │
│    │                                                                 │
│    ├─▶ 1. Resolve Template                                          │
│    │     ├─ Check per-recipient override                            │
│    │     ├─ Check by-kind template                                  │
│    │     └─ Use default template                                    │
│    │                                                                 │
│    ├─▶ 2. Merge Variables                                           │
│    │     ├─ Global variables                                        │
│    │     ├─ Per-recipient variables                                 │
│    │     └─ Auto-injected (name, email, etc.)                       │
│    │                                                                 │
│    ├─▶ 3. Render Email                                              │
│    │     ├─ Substitute {{variables}}                                │
│    │     ├─ Generate HTML                                           │
│    │     └─ Generate plain text                                     │
│    │                                                                 │
│    ├─▶ 4. Send via SMTP                                             │
│    │     ├─ Attempt 1                                               │
│    │     ├─ Retry on failure (with backoff)                         │
│    │     └─ Attempt 2                                               │
│    │                                                                 │
│    ├─▶ 5. Update Status                                             │
│    │     ├─ deliveryStatus: "sent" or "failed"                      │
│    │     ├─ sentAt: timestamp                                       │
│    │     ├─ messageId: SMTP message ID                              │
│    │     └─ error: error message (if failed)                        │
│    │                                                                 │
│    ├─▶ 6. Log to system_logs                                        │
│    │     └─ type: "email", action: "sent", status: "success"        │
│    │                                                                 │
│    ├─▶ 7. Throttle                                                  │
│    │     └─ Sleep 500ms (for 120/min)                               │
│    │                                                                 │
│    └─▶ 8. Save Progress (every 50 recipients)                       │
│          ├─ Update campaign.sentCount                               │
│          ├─ Update campaign.failedCount                             │
│          └─ Save recipients JSONB                                   │
│                                                                      │
│  Status: SENT                                                        │
│  completedAt: now                                                    │
│  sentCount: 1828                                                     │
│  failedCount: 4                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 2. Template Resolution Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│                    RECIPIENT: Priya Sharma                           │
│                    KIND: client                                      │
│                    ID: abc-123                                       │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
                    ┌────────────────────┐
                    │ STEP 1: Check      │
                    │ Per-Recipient      │
                    │ Override           │
                    └────────┬───────────┘
                             │
                    ┌────────▼───────────┐
                    │ recipient.         │
                    │ templateId?        │
                    └────────┬───────────┘
                             │
                    ┌────────▼───────────┐
                    │ YES: Use this      │
                    │ template           │
                    │ ✓ DONE             │
                    └────────────────────┘
                             │
                    ┌────────▼───────────┐
                    │ NO: Continue       │
                    └────────┬───────────┘
                             │
                             ▼
                    ┌────────────────────┐
                    │ STEP 2: Check      │
                    │ By-Kind Template   │
                    └────────┬───────────┘
                             │
                    ┌────────▼───────────┐
                    │ config.            │
                    │ byKindTemplate     │
                    │ ["client"]?        │
                    └────────┬───────────┘
                             │
                    ┌────────▼───────────┐
                    │ YES: Use this      │
                    │ template           │
                    │ ✓ DONE             │
                    └────────────────────┘
                             │
                    ┌────────▼───────────┐
                    │ NO: Continue       │
                    └────────┬───────────┘
                             │
                             ▼
                    ┌────────────────────┐
                    │ STEP 3: Use        │
                    │ Default Template   │
                    │ ✓ DONE             │
                    └────────────────────┘
```

---

## 3. Variable Merging Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│                      VARIABLE MERGING                                │
└─────────────────────────────┬───────────────────────────────────────┘
                              │
                              ▼
                    ┌──────────────────┐
                    │ START: Empty Map │
                    │ vars = {}        │
                    └────────┬─────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│ STEP 1: Add Global Variables (from campaign)                        │
│                                                                      │
│  campaign.globalVariables = {                                       │
│    "month": "May 2026",                                             │
│    "companyName": "Sri Karthikeya Caterers"                         │
│  }                                                                   │
│                                                                      │
│  vars = {                                                            │
│    "month": "May 2026",                                             │
│    "companyName": "Sri Karthikeya Caterers"                         │
│  }                                                                   │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│ STEP 2: Add Per-Recipient Variables (overrides global)              │
│                                                                      │
│  recipient.variables = {                                            │
│    "freebie": "complimentary mocktail counter"                      │
│  }                                                                   │
│                                                                      │
│  vars = {                                                            │
│    "month": "May 2026",                                             │
│    "companyName": "Sri Karthikeya Caterers",                        │
│    "freebie": "complimentary mocktail counter"                      │
│  }                                                                   │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│ STEP 3: Add Auto-Injected Context (always available)                │
│                                                                      │
│  From recipient data:                                               │
│    - clientName = recipient.name                                    │
│    - name = recipient.name                                          │
│    - email = recipient.email                                        │
│    - kind = recipient.kind                                          │
│                                                                      │
│  From system:                                                        │
│    - reviewLink = reviewBaseUrl                                     │
│    - campaignId = campaign.id                                       │
│    - campaignName = campaign.name                                   │
│                                                                      │
│  vars = {                                                            │
│    "month": "May 2026",                                             │
│    "companyName": "Sri Karthikeya Caterers",                        │
│    "freebie": "complimentary mocktail counter",                     │
│    "clientName": "Priya Sharma",                                    │
│    "name": "Priya Sharma",                                          │
│    "email": "priya@example.com",                                    │
│    "kind": "client",                                                │
│    "reviewLink": "http://localhost:3000",                           │
│    "campaignId": "uuid-123",                                        │
│    "campaignName": "May 2026 Newsletter"                            │
│  }                                                                   │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
                    ┌──────────────────┐
                    │ FINAL VARIABLES  │
                    │ Ready for render │
                    └──────────────────┘
```

---

## 4. Retry Logic Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│                      SEND EMAIL TO: priya@example.com                │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
                    ┌────────────────────┐
                    │ ATTEMPT 1          │
                    │ Try to send        │
                    └────────┬───────────┘
                             │
                    ┌────────▼───────────┐
                    │ Success?           │
                    └────────┬───────────┘
                             │
                    ┌────────▼───────────┐
                    │ YES: Log success   │
                    │ Return messageId   │
                    │ ✓ DONE             │
                    └────────────────────┘
                             │
                    ┌────────▼───────────┐
                    │ NO: Catch error    │
                    │ Log: "Attempt 1    │
                    │ failed: timeout"   │
                    └────────┬───────────┘
                             │
                             ▼
                    ┌────────────────────┐
                    │ BACKOFF            │
                    │ Sleep 1000ms       │
                    │ (1s × attempt)     │
                    └────────┬───────────┘
                             │
                             ▼
                    ┌────────────────────┐
                    │ ATTEMPT 2          │
                    │ Try to send again  │
                    └────────┬───────────┘
                             │
                    ┌────────▼───────────┐
                    │ Success?           │
                    └────────┬───────────┘
                             │
                    ┌────────▼───────────┐
                    │ YES: Log success   │
                    │ Return messageId   │
                    │ ✓ DONE             │
                    └────────────────────┘
                             │
                    ┌────────▼───────────┐
                    │ NO: Max attempts   │
                    │ reached            │
                    └────────┬───────────┘
                             │
                             ▼
                    ┌────────────────────┐
                    │ PERMANENT FAILURE  │
                    │ Log to system_logs │
                    │ status: "failed"   │
                    │ error: "timeout"   │
                    │ attempts: 2        │
                    └────────────────────┘
```

---

## 5. Data Flow: Client/Subscriber to Email

```
┌─────────────────────────────────────────────────────────────────────┐
│                         DATABASE TABLES                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────────┐              ┌──────────────┐                    │
│  │   clients    │              │ subscribers  │                    │
│  ├──────────────┤              ├──────────────┤                    │
│  │ id           │              │ id           │                    │
│  │ name         │              │ email        │                    │
│  │ email        │              │ name         │                    │
│  │ phone        │              │ is_active    │                    │
│  │ status       │              │ source       │                    │
│  └──────┬───────┘              └──────┬───────┘                    │
│         │                             │                             │
│         └─────────────┬───────────────┘                             │
│                       │                                             │
│                       ▼                                             │
│            ┌──────────────────────┐                                │
│            │ RecipientResolver    │                                │
│            │ - Fetch both         │                                │
│            │ - Apply filters      │                                │
│            │ - Deduplicate        │                                │
│            └──────────┬───────────┘                                │
│                       │                                             │
│                       ▼                                             │
│            ┌──────────────────────┐                                │
│            │ email_campaigns      │                                │
│            ├──────────────────────┤                                │
│            │ id                   │                                │
│            │ name                 │                                │
│            │ status               │                                │
│            │ recipients (JSONB)   │◀─── Snapshot stored here       │
│            │ [                    │                                │
│            │   {                  │                                │
│            │     kind: "client",  │                                │
│            │     id: "uuid",      │                                │
│            │     email: "...",    │                                │
│            │     name: "...",     │                                │
│            │     deliveryStatus   │                                │
│            │   },                 │                                │
│            │   {                  │                                │
│            │     kind: "subscriber│                                │
│            │     ...              │                                │
│            │   }                  │                                │
│            │ ]                    │                                │
│            │ default_template_id  │                                │
│            │ sent_count           │                                │
│            │ failed_count         │                                │
│            └──────────┬───────────┘                                │
│                       │                                             │
│                       ▼                                             │
│            ┌──────────────────────┐                                │
│            │ CampaignService      │                                │
│            │ .dispatch()          │                                │
│            └──────────┬───────────┘                                │
│                       │                                             │
│                       ▼                                             │
│            ┌──────────────────────┐                                │
│            │ EmailService         │                                │
│            │ .sendRendered()      │                                │
│            └──────────┬───────────┘                                │
│                       │                                             │
│                       ▼                                             │
│            ┌──────────────────────┐                                │
│            │ SMTP Server          │                                │
│            │ (Gmail / SES)        │                                │
│            └──────────┬───────────┘                                │
│                       │                                             │
│                       ▼                                             │
│            ┌──────────────────────┐                                │
│            │ Recipient Inbox      │                                │
│            │ priya@example.com    │                                │
│            └──────────────────────┘                                │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Summary

These diagrams show:

1. **Campaign Flow**: From creation to completion
2. **Template Resolution**: 3-tier priority system
3. **Variable Merging**: How variables are combined
4. **Retry Logic**: How failed sends are retried
5. **Data Flow**: From database to recipient inbox

The system is designed to be:
- **Flexible**: Multiple template options
- **Reliable**: Retry logic + resume safety
- **Auditable**: Every action logged
- **Scalable**: Batch processing + throttling
- **User-friendly**: Preview before send
