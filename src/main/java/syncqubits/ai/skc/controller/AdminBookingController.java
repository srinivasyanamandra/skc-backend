package syncqubits.ai.skc.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import syncqubits.ai.skc.dto.PageResponse;
import syncqubits.ai.skc.dto.booking.AdminBookingDetail;
import syncqubits.ai.skc.dto.booking.AdminBookingSummary;
import syncqubits.ai.skc.dto.booking.BookingCreateRequest;
import syncqubits.ai.skc.dto.booking.BookingTaskRequest;
import syncqubits.ai.skc.dto.booking.BookingUpdateRequest;
import syncqubits.ai.skc.dto.booking.ConvertQuoteRequest;
import syncqubits.ai.skc.service.BookingService;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/bookings")
@RequiredArgsConstructor
@Slf4j
public class AdminBookingController {

    private final BookingService bookingService;

    @GetMapping
    public ResponseEntity<PageResponse<AdminBookingSummary>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "createdAt") String sortField,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Sort.Direction dir = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return ResponseEntity.ok(bookingService.list(q, status, eventType, fromDate, toDate, page, size, sortField, dir));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminBookingDetail> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(bookingService.detail(id));
    }

    @PostMapping
    public ResponseEntity<AdminBookingDetail> create(@Valid @RequestBody BookingCreateRequest body) {
        return ResponseEntity.ok(bookingService.create(body));
    }

    /** Convert a quote into a confirmed booking. The quote's id is the path
     *  param (not the booking's), since callers come from the quotes UI. */
    @PostMapping("/from-quote/{quoteId}")
    public ResponseEntity<AdminBookingDetail> convert(@PathVariable UUID quoteId,
                                                      @Valid @RequestBody ConvertQuoteRequest body) {
        return ResponseEntity.ok(bookingService.convertFromQuote(quoteId, body));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminBookingDetail> update(@PathVariable UUID id,
                                                     @Valid @RequestBody BookingUpdateRequest body) {
        return ResponseEntity.ok(bookingService.update(id, body));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        bookingService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /* ─────────────────────────── tasks (event checklist) ────────────────────────── */

    @PostMapping("/{id}/tasks")
    public ResponseEntity<AdminBookingDetail.Task> addTask(@PathVariable UUID id,
                                                           @Valid @RequestBody BookingTaskRequest body) {
        return ResponseEntity.ok(bookingService.addTask(id, body));
    }

    @PutMapping("/{id}/tasks/{taskId}")
    public ResponseEntity<AdminBookingDetail.Task> updateTask(@PathVariable UUID id,
                                                              @PathVariable UUID taskId,
                                                              @Valid @RequestBody BookingTaskRequest body) {
        return ResponseEntity.ok(bookingService.updateTask(id, taskId, body));
    }

    @DeleteMapping("/{id}/tasks/{taskId}")
    public ResponseEntity<Void> deleteTask(@PathVariable UUID id, @PathVariable UUID taskId) {
        bookingService.deleteTask(id, taskId);
        return ResponseEntity.noContent().build();
    }
}
