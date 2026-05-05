# Email Sending & Scheduling - Sequence Diagrams

## 1. Complete Campaign Flow (Immediate Send)

```mermaid
sequenceDiagram
    participant Admin
    participant Controller
    participant CampaignService
    participant RecipientResolver
    participant ClientRepo
    participant SubscriberRepo
    participant EmailService
    participant SMTP
    participant SystemLog
    participant Database

    Note over Admin,Database: PHASE 1: CREATE CAMPAIGN
    Admin->>Controller: POST /api/admin/campaigns
    Controller->>CampaignService: create(request)
    CampaignService->>Database: save(campaign, status=DRAFT)
    CampaignService->>SystemLog: log("created", "success")
    CampaignService-->>Admin: 201 Created {campaignId}

    Note over Admin,Database: PHASE 2: ADD RECIPIENTS
    Admin->>Controller: POST /api/admin/campaigns/{id}/recipients
    Controller->>CampaignService: setRecipients(id, request)
    CampaignService->>RecipientResolver: materialise(segments, include, exclude)
    
    alt Segment: Clients
        RecipientResolver->>ClientRepo: resolveClients(filters)
        ClientRepo-->>RecipientResolver: List<Client>
    end
    
    alt Segment: Subscribers
        RecipientResolver->>SubscriberRepo: resolveSubscribers(filters)
        SubscriberRepo-->>RecipientResolver: List<Subscriber>
    end
    
    RecipientResolver->>RecipientResolver: deduplicate by email
    RecipientResolver->>RecipientResolver: filter inactive subscribers
    RecipientResolver-->>CampaignService: Materialised(items, stats)
    
    CampaignService->>Database: save(campaign.recipients = snapshot)
    CampaignService->>SystemLog: log("recipients_set", "success")
    CampaignService-->>Admin: 200 OK {totalRecipients, byKind}

    Note over Admin,Database: PHASE 3: ASSIGN TEMPLATES
    Admin->>Controller: PUT /api/admin/campaigns/{id}/templates
    Controller->>CampaignService: assignTemplates(id, request)
    CampaignService->>Database: update(defaultTemplate, byKindTemplate, perRecipient)
    CampaignService->>SystemLog: log("templates_assigned", "success")
    CampaignService-->>Admin: 200 OK {templatesValidated}

    Note over Admin,Database: PHASE 4: PREVIEW (Optional)
    Admin->>Controller: POST /api/admin/campaigns/{id}/preview
    Controller->>CampaignService: preview(id, sample=3)
    loop For each sample recipient
        CampaignService->>CampaignService: resolveTemplate(3-tier)
        CampaignService->>CampaignService: mergeVariables(global+recipient)
        CampaignService->>EmailService: render(template, variables)
        EmailService->>EmailService: substitute {{variables}}
        EmailService-->>CampaignService: RenderedEmail(html, text, subject)
    end
    CampaignService-->>Admin: 200 OK {renders[]}

    Note over Admin,Database: PHASE 5: SEND (Immediate)
    Admin->>Controller: POST /api/admin/campaigns/{id}/send
    Controller->>CampaignService: send(id, scheduleAt=null)
    CampaignService->>Database: update(status=QUEUED, scheduledAt=now)
    CampaignService->>SystemLog: log("queued", "success")
    CampaignService->>CampaignService: dispatchAsync(id) [background]
    CampaignService-->>Admin: 202 Accepted {status=queued}

    Note over Admin,Database: PHASE 6: DISPATCH (Background Thread)
    CampaignService->>Database: update(status=SENDING, startedAt=now)
    
    loop For each recipient (1832 recipients)
        alt Already sent (resume-safe)
            CampaignService->>CampaignService: skip if deliveryStatus=sent
        else Not sent yet
            CampaignService->>CampaignService: resolveTemplate(recipient)
            Note right of CampaignService: 1. Per-recipient override?<br/>2. By-kind template?<br/>3. Default template
            
            CampaignService->>CampaignService: mergeVariables(recipient)
            Note right of CampaignService: Priority:<br/>1. Per-recipient vars<br/>2. Global vars<br/>3. Auto-injected
            
            CampaignService->>EmailService: render(template, variables)
            EmailService->>EmailService: substitute {{clientName}}, {{eventDate}}, etc.
            EmailService-->>CampaignService: RenderedEmail
            
            CampaignService->>EmailService: sendRendered(email, name, rendered)
            
            loop Retry (max 2 attempts)
                EmailService->>SMTP: send(from, to, subject, html, text)
                alt Success
                    SMTP-->>EmailService: messageId
                    EmailService->>SystemLog: log("sent", "success", {messageId, attempts})
                    EmailService-->>CampaignService: SUCCESS {messageId}
                    CampaignService->>CampaignService: recipient.deliveryStatus=sent
                    CampaignService->>CampaignService: sentCount++
                else Failure (retry)
                    SMTP-->>EmailService: error
                    EmailService->>EmailService: sleep(1000ms * attempt)
                end
            end
            
            alt All retries failed
                EmailService->>SystemLog: log("failed", "failed", {error, attempts})
                EmailService-->>CampaignService: FAILED {error}
                CampaignService->>CampaignService: recipient.deliveryStatus=failed
                CampaignService->>CampaignService: failedCount++
            end
            
            CampaignService->>CampaignService: sleep(500ms) [throttle]
        end
        
        alt Every 50 recipients (batch save)
            CampaignService->>Database: save(recipients, sentCount, failedCount)
        end
        
        alt Every 50 recipients (cancellation check)
            CampaignService->>Database: load(campaign)
            alt Campaign cancelled
                Database-->>CampaignService: status=CANCELLED
                CampaignService->>CampaignService: STOP dispatch
            end
        end
    end
    
    CampaignService->>Database: update(status=SENT, completedAt=now)
    CampaignService->>SystemLog: log("completed", "success", {sentCount, failedCount})
```

---

## 2. Scheduled Campaign Flow

```mermaid
sequenceDiagram
    participant Admin
    participant Controller
    participant CampaignService
    participant Database
    participant SystemLog
    participant Scheduler
    participant EmailService
    participant SMTP

    Note over Admin,Scheduler: SCHEDULE FOR FUTURE
    Admin->>Controller: POST /api/admin/campaigns/{id}/send
    Note right of Admin: {"scheduleAt": "2026-05-10T09:00:00Z"}
    
    Controller->>CampaignService: send(id, scheduleAt=future)
    CampaignService->>Database: update(status=QUEUED, scheduledAt=future)
    CampaignService->>SystemLog: log("queued", "success", {scheduledAt})
    CampaignService-->>Admin: 202 Accepted {status=queued, scheduledFor}
    
    Note over Admin,SMTP: ... TIME PASSES ...
    
    Note over Scheduler,SMTP: SCHEDULED TIME REACHED
    Scheduler->>Database: findCampaigns(status=QUEUED, scheduledAt <= now)
    Database-->>Scheduler: List<Campaign>
    
    loop For each due campaign
        Scheduler->>CampaignService: dispatchAsync(campaignId)
        CampaignService->>Database: update(status=SENDING, startedAt=now)
        
        loop For each recipient
            CampaignService->>CampaignService: resolveTemplate + mergeVariables
            CampaignService->>EmailService: render + sendRendered
            EmailService->>SMTP: send email
            SMTP-->>EmailService: result
            EmailService-->>CampaignService: result
            CampaignService->>CampaignService: update recipient status
            CampaignService->>CampaignService: sleep(throttle delay)
        end
        
        CampaignService->>Database: update(status=SENT, completedAt=now)
        CampaignService->>SystemLog: log("completed", "success")
    end
```

---

## 3. Recipient Resolution (Clients + Subscribers)

```mermaid
sequenceDiagram
    participant Admin
    participant CampaignService
    participant RecipientResolver
    participant ClientRepo
    participant SubscriberRepo
    participant Database

    Admin->>CampaignService: setRecipients(campaignId, request)
    Note right of Admin: Request:<br/>{<br/>  segments: [{kind: "clients", filter: {...}}, {kind: "subscribers", filter: {...}}],<br/>  include: [{kind: "client", id: "uuid"}],<br/>  exclude: [{kind: "subscriber", id: "uuid"}]<br/>}
    
    CampaignService->>RecipientResolver: materialise(request)
    
    Note over RecipientResolver: STEP 1: Process Segments
    
    loop For each segment
        alt Segment kind = "clients"
            RecipientResolver->>ClientRepo: resolveClients(q, status, since, until, eventType, minRating)
            ClientRepo->>Database: SELECT * FROM clients WHERE ...
            Database-->>ClientRepo: List<Client>
            ClientRepo-->>RecipientResolver: List<Client>
            
            loop For each client
                RecipientResolver->>RecipientResolver: toItem(client)
                RecipientResolver->>RecipientResolver: deduplicate by email
                Note right of RecipientResolver: Map<email, RecipientItem>
            end
        end
        
        alt Segment kind = "subscribers"
            RecipientResolver->>SubscriberRepo: resolveSubscribers(q, active, since, until)
            SubscriberRepo->>Database: SELECT * FROM subscribers WHERE ...
            Database-->>SubscriberRepo: List<Subscriber>
            SubscriberRepo-->>RecipientResolver: List<Subscriber>
            
            loop For each subscriber
                alt Subscriber is inactive
                    RecipientResolver->>RecipientResolver: skip (skippedInactive++)
                else Subscriber is active
                    RecipientResolver->>RecipientResolver: toItem(subscriber)
                    RecipientResolver->>RecipientResolver: deduplicate by email
                    Note right of RecipientResolver: If email exists as client,<br/>prefer client (richer data)
                end
            end
        end
    end
    
    Note over RecipientResolver: STEP 2: Explicit Includes
    
    alt Include clients
        RecipientResolver->>ClientRepo: findAllByIds(clientIds)
        ClientRepo-->>RecipientResolver: List<Client>
        RecipientResolver->>RecipientResolver: add to map (may overwrite segment matches)
    end
    
    alt Include subscribers
        RecipientResolver->>SubscriberRepo: findAllByIds(subscriberIds)
        SubscriberRepo-->>RecipientResolver: List<Subscriber>
        RecipientResolver->>RecipientResolver: filter inactive, add to map
    end
    
    Note over RecipientResolver: STEP 3: Excludes
    
    RecipientResolver->>RecipientResolver: remove by ID (across both kinds)
    
    Note over RecipientResolver: STEP 4: Finalize
    
    RecipientResolver->>RecipientResolver: count by kind (clients, subscribers)
    RecipientResolver-->>CampaignService: Materialised(items, deduplicated, skippedInactive, clientCount, subscriberCount)
    
    CampaignService->>Database: save(campaign.recipients = snapshot JSONB)
    Note right of CampaignService: Snapshot format:<br/>[<br/>  {kind: "client", id: "uuid", email: "...", name: "...", deliveryStatus: "pending"},<br/>  {kind: "subscriber", id: "uuid", email: "...", name: "...", deliveryStatus: "pending"}<br/>]
    
    CampaignService-->>Admin: {totalRecipients, byKind: {clients: 1200, subscribers: 632}}
```

---

## 4. Template Resolution (3-Tier System)

```mermaid
sequenceDiagram
    participant CampaignService
    participant EmailTemplateRepo
    participant Database

    Note over CampaignService: For recipient: {kind: "client", id: "abc", email: "priya@example.com"}
    
    CampaignService->>CampaignService: resolveTemplateForEntry(campaign, recipient)
    
    Note over CampaignService: TIER 1: Per-Recipient Override (Highest Priority)
    
    alt recipient.templateId exists?
        CampaignService->>CampaignService: extract recipient.templateId
        CampaignService->>EmailTemplateRepo: findById(templateId)
        EmailTemplateRepo->>Database: SELECT * FROM email_templates WHERE id = ?
        Database-->>EmailTemplateRepo: EmailTemplate
        EmailTemplateRepo-->>CampaignService: EmailTemplate
        
        alt Template is inactive
            CampaignService->>CampaignService: throw ConflictException
        else Template is active
            CampaignService-->>CampaignService: RETURN template ✓
        end
    end
    
    Note over CampaignService: TIER 2: By-Kind Template
    
    alt campaign.config.byKindTemplate[recipient.kind] exists?
        CampaignService->>CampaignService: extract byKindTemplate["client"]
        CampaignService->>EmailTemplateRepo: findById(templateId)
        EmailTemplateRepo->>Database: SELECT * FROM email_templates WHERE id = ?
        Database-->>EmailTemplateRepo: EmailTemplate
        EmailTemplateRepo-->>CampaignService: EmailTemplate
        
        alt Template is inactive
            CampaignService->>CampaignService: throw ConflictException
        else Template is active
            CampaignService-->>CampaignService: RETURN template ✓
        end
    end
    
    Note over CampaignService: TIER 3: Default Template (Fallback)
    
    alt campaign.defaultTemplate exists?
        CampaignService-->>CampaignService: RETURN campaign.defaultTemplate ✓
    else No default template
        CampaignService->>CampaignService: throw ConflictException("No default template")
    end
```

---

## 5. Variable Merging & Email Rendering

```mermaid
sequenceDiagram
    participant CampaignService
    participant EmailService

    Note over CampaignService: For recipient: {kind: "client", email: "priya@example.com", name: "Priya Sharma"}
    
    CampaignService->>CampaignService: mergeVariables(campaign, recipient)
    
    Note over CampaignService: STEP 1: Start with Global Variables
    CampaignService->>CampaignService: vars = campaign.globalVariables
    Note right of CampaignService: {<br/>  "month": "May 2026",<br/>  "companyName": "Sri Karthikeya Caterers"<br/>}
    
    Note over CampaignService: STEP 2: Merge Per-Recipient Overrides
    alt recipient.variables exists?
        CampaignService->>CampaignService: vars.putAll(recipient.variables)
        Note right of CampaignService: {<br/>  "month": "May 2026",<br/>  "companyName": "Sri Karthikeya Caterers",<br/>  "freebie": "complimentary mocktail counter"<br/>}
    end
    
    Note over CampaignService: STEP 3: Auto-Inject Context (Always)
    CampaignService->>CampaignService: vars.put("clientName", recipient.name)
    CampaignService->>CampaignService: vars.put("name", recipient.name)
    CampaignService->>CampaignService: vars.put("email", recipient.email)
    CampaignService->>CampaignService: vars.put("kind", recipient.kind)
    CampaignService->>CampaignService: vars.put("reviewLink", reviewBaseUrl)
    CampaignService->>CampaignService: vars.put("campaignId", campaign.id)
    CampaignService->>CampaignService: vars.put("campaignName", campaign.name)
    
    Note right of CampaignService: Final merged variables:<br/>{<br/>  "month": "May 2026",<br/>  "companyName": "Sri Karthikeya Caterers",<br/>  "freebie": "complimentary mocktail counter",<br/>  "clientName": "Priya Sharma",<br/>  "name": "Priya Sharma",<br/>  "email": "priya@example.com",<br/>  "kind": "client",<br/>  "reviewLink": "http://localhost:3000",<br/>  "campaignId": "uuid",<br/>  "campaignName": "May 2026 Newsletter"<br/>}
    
    CampaignService->>EmailService: render(template, variables)
    
    Note over EmailService: RENDERING
    EmailService->>EmailService: substitute(template.subject, variables)
    Note right of EmailService: "Thank you, {{clientName}}!"<br/>→ "Thank you, Priya Sharma!"
    
    EmailService->>EmailService: substitute(template.content.html, variables)
    Note right of EmailService: "<h1>Hello {{clientName}}</h1>"<br/>→ "<h1>Hello Priya Sharma</h1>"
    
    EmailService->>EmailService: substitute(template.content.text, variables)
    Note right of EmailService: "Hello {{clientName}}"<br/>→ "Hello Priya Sharma"
    
    EmailService-->>CampaignService: RenderedEmail {subject, html, text, preheader, variablesResolved}
```

---

## 6. Email Sending with Retry Logic

```mermaid
sequenceDiagram
    participant CampaignService
    participant EmailService
    participant SMTP
    participant SystemLog

    CampaignService->>EmailService: sendRendered(rendered, email, name, templateId, entityType, entityId)
    
    Note over EmailService: Retry Loop (max 2 attempts)
    
    loop Attempt 1 to 2
        EmailService->>EmailService: createMimeMessage()
        EmailService->>EmailService: setFrom("info@srikarthikeyacaterers.in", "Sri Karthikeya Caterers")
        EmailService->>EmailService: setTo(email, name)
        EmailService->>EmailService: setSubject(rendered.subject)
        EmailService->>EmailService: setText(rendered.text, rendered.html)
        
        EmailService->>SMTP: send(mimeMessage)
        
        alt Success
            SMTP-->>EmailService: messageId
            
            EmailService->>SystemLog: logEmail("sent", "success", entityId, entityType, details)
            Note right of EmailService: details = {<br/>  recipientEmail: "priya@example.com",<br/>  recipientName: "Priya Sharma",<br/>  subject: "Thank you!",<br/>  messageId: "<abc@smtp.gmail.com>",<br/>  attempts: 1,<br/>  templateId: "uuid"<br/>}
            
            EmailService-->>CampaignService: EmailSendResult {success: true, messageId, attempts: 1}
            
        else Failure (attempt < maxAttempts)
            SMTP-->>EmailService: MessagingException
            
            Note over EmailService: Exponential Backoff
            EmailService->>EmailService: sleep(1000ms * attempt)
            Note right of EmailService: Attempt 1: 1s<br/>Attempt 2: 2s
            
        else Failure (all attempts exhausted)
            SMTP-->>EmailService: MessagingException
            
            EmailService->>SystemLog: logEmail("failed", "failed", entityId, entityType, details)
            Note right of EmailService: details = {<br/>  recipientEmail: "priya@example.com",<br/>  recipientName: "Priya Sharma",<br/>  subject: "Thank you!",<br/>  attempts: 2,<br/>  error: "Connection timeout",<br/>  templateId: "uuid"<br/>}
            
            EmailService-->>CampaignService: EmailSendResult {success: false, error, attempts: 2}
        end
    end
```

---

## 7. Campaign Cancellation

```mermaid
sequenceDiagram
    participant Admin
    participant Controller
    participant CampaignService
    participant Database
    participant SystemLog
    participant DispatchThread

    Note over DispatchThread: Campaign is SENDING (background thread)
    
    loop Sending emails
        DispatchThread->>DispatchThread: send email to recipient 1
        DispatchThread->>DispatchThread: send email to recipient 2
        DispatchThread->>DispatchThread: ...
    end
    
    Note over Admin,SystemLog: Admin decides to cancel
    
    Admin->>Controller: POST /api/admin/campaigns/{id}/cancel
    Controller->>CampaignService: cancel(id)
    
    CampaignService->>Database: load(campaign)
    
    alt Campaign is QUEUED or SENDING
        CampaignService->>Database: update(status=CANCELLED, completedAt=now)
        CampaignService->>SystemLog: log("cancelled", "success", {sentCount, failedCount})
        CampaignService-->>Admin: 200 OK {status: "cancelled", sentCount, failedCount}
    else Campaign is DRAFT, SENT, FAILED, or CANCELLED
        CampaignService-->>Admin: 409 Conflict "Only queued or sending campaigns can be cancelled"
    end
    
    Note over DispatchThread: Dispatch thread continues...
    
    loop Continue sending
        DispatchThread->>DispatchThread: send email to recipient 50
        
        Note over DispatchThread: Every 50 recipients: check for cancellation
        DispatchThread->>Database: load(campaign)
        Database-->>DispatchThread: campaign {status: CANCELLED}
        
        alt Status is CANCELLED
            DispatchThread->>DispatchThread: STOP dispatch immediately
            Note right of DispatchThread: Already-sent emails remain sent<br/>Pending emails are not sent
        end
    end
```

---

## 8. Resume Safety (Crash Recovery)

```mermaid
sequenceDiagram
    participant Scheduler
    participant CampaignService
    participant Database
    participant EmailService
    participant SMTP

    Note over Scheduler,SMTP: Campaign is SENDING, then application CRASHES
    
    Note over Scheduler,SMTP: ... APPLICATION RESTARTS ...
    
    Scheduler->>Database: findCampaigns(status=SENDING)
    Database-->>Scheduler: List<Campaign> (incomplete campaigns)
    
    loop For each incomplete campaign
        Scheduler->>CampaignService: dispatchAsync(campaignId)
        CampaignService->>Database: load(campaign)
        
        Note over CampaignService: Campaign status is still SENDING<br/>Recipients have deliveryStatus
        
        loop For each recipient
            alt recipient.deliveryStatus == "sent"
                CampaignService->>CampaignService: SKIP (already sent before crash)
                Note right of CampaignService: Resume-safe!<br/>Don't send duplicate emails
            else recipient.deliveryStatus == "pending" or "failed"
                CampaignService->>CampaignService: resolveTemplate + mergeVariables
                CampaignService->>EmailService: render + sendRendered
                EmailService->>SMTP: send email
                SMTP-->>EmailService: result
                EmailService-->>CampaignService: result
                
                alt Success
                    CampaignService->>CampaignService: recipient.deliveryStatus = "sent"
                    CampaignService->>CampaignService: sentCount++
                else Failure
                    CampaignService->>CampaignService: recipient.deliveryStatus = "failed"
                    CampaignService->>CampaignService: failedCount++
                end
                
                alt Every 50 recipients
                    CampaignService->>Database: save(recipients, sentCount, failedCount)
                    Note right of CampaignService: Batch save for performance<br/>At most 50 emails lost on next crash
                end
            end
        end
        
        CampaignService->>Database: update(status=SENT, completedAt=now)
    end
```

---

## Key Insights from Diagrams

### **1. Asynchronous Design**
- Admin gets **202 Accepted** immediately
- Actual sending happens in **background thread**
- Non-blocking API for better UX

### **2. Recipient Snapshot**
- Recipients are **materialized** at campaign creation
- Stored as **JSONB** in `email_campaigns.recipients`
- Preserves **audit trail** even if subscriber unsubscribes later

### **3. 3-Tier Template Resolution**
- **Per-recipient** > **By-kind** > **Default**
- Allows **VIP treatment** without complexity
- Fallback ensures **everyone gets an email**

### **4. Variable Merging Priority**
- **Per-recipient** > **Global** > **Auto-injected**
- Enables **personalization** at scale
- Auto-injected vars always available (name, email, etc.)

### **5. Retry Logic**
- **2 attempts** with exponential backoff
- Logs **every attempt** to `system_logs`
- Graceful failure handling

### **6. Throttling**
- **500ms delay** between emails (120/min default)
- Prevents **SMTP rate limits**
- Configurable per campaign

### **7. Batch Saves**
- Save every **50 recipients**
- **Performance**: 37 DB writes instead of 1832
- **Resume safety**: At most 50 emails lost on crash

### **8. Cancellation**
- Check status every **50 recipients**
- **Immediate stop** when cancelled
- Already-sent emails **remain sent**

### **9. Resume Safety**
- Skip recipients with `deliveryStatus = "sent"`
- **No duplicate emails** after crash
- Scheduler picks up incomplete campaigns

### **10. Deduplication**
- By **email address** (case-insensitive)
- **Prefer client** over subscriber (richer data)
- Tracks `deduplicated` count for transparency

---

## Summary

These sequence diagrams show:

✅ **Complete campaign lifecycle** (create → recipients → templates → preview → send → dispatch)  
✅ **Scheduled vs immediate** sending  
✅ **Recipient resolution** (clients + subscribers with deduplication)  
✅ **3-tier template resolution** (per-recipient > by-kind > default)  
✅ **Variable merging** (per-recipient > global > auto-injected)  
✅ **Email rendering** ({{variable}} substitution)  
✅ **SMTP sending with retry** (2 attempts, exponential backoff)  
✅ **Cancellation** (mid-flight stop)  
✅ **Resume safety** (crash recovery without duplicates)  

The system is **production-ready**, **scalable**, and **resilient**! 🚀
