package syncqubits.ai.skc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import syncqubits.ai.skc.entity.Booking;
import syncqubits.ai.skc.entity.PurchaseOrder;
import syncqubits.ai.skc.entity.QuoteRequest;
import syncqubits.ai.skc.entity.Review;
import syncqubits.ai.skc.entity.Transaction;
import syncqubits.ai.skc.entity.Vendor;
import syncqubits.ai.skc.repository.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private final ClientRepository clientRepository;
    private final QuoteRequestRepository quoteRequestRepository;
    private final ReviewRepository reviewRepository;
    private final SubscriberRepository subscriberRepository;
    private final EmailCampaignRepository emailCampaignRepository;
    private final BookingRepository bookingRepository;
    private final VendorRepository vendorRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final InvoiceRepository invoiceRepository;
    private final TransactionRepository transactionRepository;
    private final SystemLogRepository systemLogRepository;

    public Map<String, Object> getDashboardData() {
        log.info("Fetching dashboard data");

        Instant monthStart = Instant.now().minus(30, ChronoUnit.DAYS);

        // Bookings: derive an "upcoming this month" count from the calendar
        // month so the number aligns with what an admin sees on their wall
        // calendar (vs. a rolling 30-day window).
        YearMonth currentMonth = YearMonth.now(BUSINESS_ZONE);
        LocalDate monthFirst = currentMonth.atDay(1);
        LocalDate monthLast = currentMonth.atEndOfMonth();
        long bookingsUpcomingThisMonth = bookingRepository.findUpcoming(
                LocalDate.now(BUSINESS_ZONE), monthLast).stream()
                .filter(b -> b.getStatus() != Booking.BookingStatus.CANCELLED).count();
        long bookingsConfirmed = bookingRepository.countByStatus(Booking.BookingStatus.CONFIRMED);
        long bookingsInProgress = bookingRepository.countByStatus(Booking.BookingStatus.IN_PROGRESS);
        long bookingsCompleted = bookingRepository.countByStatus(Booking.BookingStatus.COMPLETED);

        // LinkedHashMap so the iteration order in the JSON response is stable
        // (Map.of has no ordering guarantee and the dashboard renders cards
        // from this object directly in some places).
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("clients",            clientRepository.count());
        totals.put("quotesPending",      quoteRequestRepository.countByStatus(QuoteRequest.QuoteStatus.PENDING));
        totals.put("quotesBooked",       quoteRequestRepository.countByStatus(QuoteRequest.QuoteStatus.BOOKED));
        totals.put("reviewsPending",     reviewRepository.countByTypeAndStatus(Review.ReviewType.REVIEW, Review.ReviewStatus.PENDING));
        totals.put("reviewsApproved",    reviewRepository.countByTypeAndStatus(Review.ReviewType.REVIEW, Review.ReviewStatus.APPROVED));
        totals.put("subscribersActive",  subscriberRepository.countByIsActive(true));
        totals.put("campaignsThisMonth", emailCampaignRepository.countCreatedSince(monthStart));
        totals.put("bookingsConfirmed",  bookingsConfirmed);
        totals.put("bookingsInProgress", bookingsInProgress);
        totals.put("bookingsCompleted",  bookingsCompleted);
        totals.put("bookingsUpcomingThisMonth", bookingsUpcomingThisMonth);

        // Vendors + Purchase orders (Phase 2)
        totals.put("vendorsActive",      vendorRepository.countByStatus(Vendor.VendorStatus.ACTIVE));
        totals.put("posDraft",           purchaseOrderRepository.countByStatus(PurchaseOrder.PoStatus.DRAFT));
        totals.put("posIssued",          purchaseOrderRepository.countByStatus(PurchaseOrder.PoStatus.ISSUED));
        totals.put("posOutstandingCents", purchaseOrderRepository.sumOutstandingCents());

        // Phase 3 — finance KPIs.
        totals.put("invoicesOutstandingCents", invoiceRepository.sumOutstandingCents());
        long revenueThisMonth = transactionRepository.sumByDirectionSince(
                Transaction.Direction.INCOMING, monthStart);
        long expensesThisMonth = transactionRepository.sumByDirectionSince(
                Transaction.Direction.OUTGOING, monthStart);
        totals.put("revenueThisMonthCents",  revenueThisMonth);
        totals.put("expensesThisMonthCents", expensesThisMonth);
        totals.put("netCashFlowThisMonthCents", revenueThisMonth - expensesThisMonth);

        Map<String, Object> trends = new LinkedHashMap<>();
        trends.put("newClientsThisMonth",       clientRepository.searchClients("", null, monthStart, null, PageRequest.of(0, 1)).getTotalElements());
        trends.put("newQuotesThisMonth",        quoteRequestRepository.countCreatedSince(monthStart));
        trends.put("campaignsSentThisMonth",    emailCampaignRepository.countCreatedSince(monthStart));
        trends.put("emailsDeliveredThisMonth",  emailCampaignRepository.sumSentCountSince(monthStart));
        trends.put("bookingsUpcoming",          bookingsUpcomingThisMonth);
        trends.put("revenueThisMonthCents",     revenueThisMonth);
        trends.put("expensesThisMonthCents",    expensesThisMonth);

        // Recent activity
        var recentLogs = systemLogRepository.findRecentActivity(PageRequest.of(0, 10));
        List<Map<String, Object>> recentActivity = recentLogs.getContent().stream()
                .map(l -> {
                    Map<String, Object> activity = new HashMap<>();
                    activity.put("id", l.getId().toString());
                    activity.put("type", l.getType());
                    activity.put("action", l.getAction());
                    activity.put("entityType", l.getEntityType());
                    activity.put("entityId", l.getEntityId() == null ? null : l.getEntityId().toString());
                    activity.put("message", buildLogMessage(l.getType(), l.getAction(), l.getDetails()));
                    activity.put("at", l.getCreatedAt().toString());
                    return activity;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totals", totals);
        response.put("trends", trends);
        response.put("recentActivity", recentActivity);
        return response;
    }

    private String buildLogMessage(String type, String action, Map<String, Object> details) {
        if (details == null) {
            return String.format("%s %s", type, action);
        }

        // Vendor + PO actions are emitted via SystemLogService.logEmail with
        // a synthetic entityType, so they land here under type="email" with
        // an action prefix. Pretty-print before the generic fallback runs.
        if (action != null && action.startsWith("vendor_")) {
            String pretty = action.substring("vendor_".length()).replace('_', ' ');
            String name = String.valueOf(details.getOrDefault("name", "Vendor"));
            return String.format("Vendor '%s' %s", name, pretty);
        }
        if (action != null && action.startsWith("po_")) {
            String pretty = action.substring("po_".length()).replace('_', ' ');
            Object ref = details.get("reference");
            return ref != null
                    ? String.format("Purchase order %s %s", ref, pretty)
                    : String.format("Purchase order %s", pretty);
        }
        if (action != null && action.startsWith("invoice_")) {
            String pretty = action.substring("invoice_".length()).replace('_', ' ');
            Object ref = details.get("reference");
            return ref != null
                    ? String.format("Invoice %s %s", ref, pretty)
                    : String.format("Invoice %s", pretty);
        }
        if (action != null && (action.equals("payment_recorded") || action.equals("payment_refunded")
                            || action.equals("expense_recorded") || action.equals("transaction_updated")
                            || action.equals("transaction_deleted"))) {
            Object ref    = details.get("reference");
            Object amount = details.get("amount");
            String verb = switch (action) {
                case "payment_recorded"     -> "Payment recorded";
                case "payment_refunded"     -> "Payment refunded";
                case "expense_recorded"     -> "Expense recorded";
                case "transaction_updated"  -> "Transaction updated";
                case "transaction_deleted"  -> "Transaction deleted";
                default -> action;
            };
            if (ref != null) return String.format("%s · %s", verb, ref);
            if (amount != null) return String.format("%s (%s)", verb, amount);
            return verb;
        }

        return switch (type) {
            case "campaign" -> String.format("Campaign '%s' %s",
                    details.getOrDefault("campaignName", "Unknown"), action);
            case "email" -> String.format("Email %s to %s",
                    action, details.getOrDefault("recipientEmail", "recipient"));
            case "review" -> String.format("Review %s by %s",
                    action, details.getOrDefault("reviewerName", "user"));
            case "quote" -> {
                // Quote-typed log entries also cover booking lifecycle events
                // (booking_created, booking_converted, booking_updated). When
                // the action carries a reference, surface it directly so the
                // activity feed reads "Booking SKC-2026-000042 created" rather
                // than "quote booking_created".
                Object ref = details.get("reference");
                if (action != null && action.startsWith("booking_")) {
                    String pretty = action.substring("booking_".length()).replace('_', ' ');
                    yield ref != null
                            ? String.format("Booking %s %s", ref, pretty)
                            : String.format("Booking %s", pretty);
                }
                yield String.format("Quote %s from %s",
                        action, details.getOrDefault("clientEmail", "client"));
            }
            default -> String.format("%s %s", type, action);
        };
    }
}
