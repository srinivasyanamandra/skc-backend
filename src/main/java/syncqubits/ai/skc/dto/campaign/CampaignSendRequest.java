package syncqubits.ai.skc.dto.campaign;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Schedule-or-send-now request body for campaign dispatch.
 *
 * <h3>scheduleAt parsing</h3>
 * Bound to {@link Instant} via the auto-registered Jackson {@code JavaTimeModule}.
 * Accepts:
 *   - {@code "2026-05-08T15:30:00Z"} — UTC, the preferred wire format
 *   - {@code "2026-05-08T15:30:00+05:30"} — explicit zone offset
 *   - epoch millis as a JSON number
 *
 * It does <strong>not</strong> accept naive datetime strings like
 * {@code "2026-05-08T15:30:00"} (no zone) — Jackson's InstantDeserializer
 * rejects them with a 400. This was the production scheduling bug we hit:
 * the frontend was concatenating {@code `${date}T${time}:00`}, producing a
 * naive string that the backend silently mishandled. The frontend
 * (`AdminScheduleField`) now always sends a UTC ISO via
 * {@code new Date(...).toISOString()}, and the bounds check in
 * {@code CampaignService.send()} additionally rejects timestamps in the
 * past or more than a year out.
 *
 * <h3>timezone field</h3>
 * Optional, audit-only. The dispatch loop logs it to system_logs so a
 * post-mortem can correlate "campaign was 6 hours late" with "admin
 * scheduled it from Asia/Kolkata at 18:00 local". Not used for any
 * scheduling math — the canonical schedule is the UTC `scheduleAt`.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignSendRequest {

    /** When to dispatch. ISO-8601 with timezone (Z or offset). null = send now. */
    private Instant scheduleAt;

    /** Optional throttle override: { "perMinute": 120 } */
    private Map<String, Object> throttle;

    /**
     * IANA timezone the admin scheduled from (e.g. "Asia/Kolkata"). Optional,
     * audit-only — see class Javadoc.
     */
    @JsonProperty("timezone")
    private String timezone;
}
