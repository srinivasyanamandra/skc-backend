package syncqubits.ai.skc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import syncqubits.ai.skc.dto.recipient.RecipientItem;
import syncqubits.ai.skc.dto.recipient.RecipientSearchResponse;
import syncqubits.ai.skc.dto.recipient.ResolveRequest;
import syncqubits.ai.skc.dto.recipient.ResolveResponse;
import syncqubits.ai.skc.entity.Client;
import syncqubits.ai.skc.entity.Subscriber;
import syncqubits.ai.skc.exception.BadRequestException;
import syncqubits.ai.skc.repository.ClientRepository;
import syncqubits.ai.skc.repository.SubscriberRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Unified recipient resolver. Two responsibilities:
 *   1) paged search across (clients ∪ subscribers) for the recipient picker UI
 *   2) materialisation: include + segments + exclude → deduped final list
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecipientResolver {

    private final ClientRepository clientRepository;
    private final SubscriberRepository subscriberRepository;

    /* ============================================================== unified search */

    public RecipientSearchResponse search(String q,
                                          String kindParam,
                                          String clientStatus,
                                          Boolean subscribed,
                                          Short minRating,
                                          String eventType,
                                          Instant since,
                                          Instant until,
                                          int page,
                                          int size,
                                          String sortField,
                                          Sort.Direction direction) {

        Kind kind = parseKind(kindParam);
        Client.ClientStatus statusEnum = parseClientStatus(clientStatus);

        // 1) Pull EVERYTHING that matches (cap to a sane upper bound for in-memory sort)
        List<RecipientItem> pool = new ArrayList<>();
        long clientCount = 0, subscriberCount = 0;

        if (kind != Kind.SUBSCRIBERS_ONLY) {
            List<Client> clients = clientRepository.resolveClients(
                    emptyIfBlank(q), statusEnum, since, until, emptyIfBlank(eventType), minRating);
            clientCount = clients.size();
            for (Client c : clients) pool.add(toItem(c));
        }
        if (kind != Kind.CLIENTS_ONLY) {
            // minRating/eventType don't apply to subscribers — they have neither quotes nor reviews
            // If caller filtered by either, drop subscribers entirely (intent: "people we have a relationship with")
            if (minRating == null && (eventType == null || eventType.isBlank())) {
                List<Subscriber> subs = subscriberRepository.resolveSubscribers(
                        emptyIfBlank(q), subscribed, since, until);
                subscriberCount = subs.size();
                for (Subscriber s : subs) pool.add(toItem(s));
            }
        }

        // 2) Sort
        Comparator<RecipientItem> cmp = comparator(sortField, direction);
        pool.sort(cmp);

        long total = pool.size();
        int from = Math.min(page * size, pool.size());
        int to = Math.min(from + size, pool.size());
        List<RecipientItem> items = pool.subList(from, to);

        Map<String, Long> kindCounts = new LinkedHashMap<>();
        kindCounts.put("clients", clientCount);
        kindCounts.put("subscribers", subscriberCount);

        return RecipientSearchResponse.builder()
                .page(page).size(size).total(total)
                .kindCounts(kindCounts)
                .items(new ArrayList<>(items))
                .build();
    }

    /* =============================================================== materialisation */

    /** Public API used by ResolveResponse + the campaign recipients endpoint. */
    public Materialised materialise(ResolveRequest req) {
        Map<String, RecipientItem> byEmail = new LinkedHashMap<>();
        long deduplicated = 0;
        long skippedInactive = 0;

        // 1) Segments
        if (req.getSegments() != null) {
            for (ResolveRequest.RecipientSegment seg : req.getSegments()) {
                if (seg == null || seg.getKind() == null) continue;
                Map<String, Object> f = seg.getFilter() == null ? Map.of() : seg.getFilter();
                String segKind = seg.getKind().toLowerCase();
                switch (segKind) {
                    case "clients", "client" -> {
                        List<Client> clients = clientRepository.resolveClients(
                                emptyIfBlank(strFilter(f, "q")),
                                clientStatusFilter(f),
                                instantFilter(f, "since"),
                                instantFilter(f, "until"),
                                emptyIfBlank(strFilter(f, "eventType")),
                                shortFilter(f, "minRating"));
                        for (Client c : clients) {
                            DedupResult r = put(byEmail, toItem(c));
                            if (r == DedupResult.DUPLICATE) deduplicated++;
                        }
                    }
                    case "subscribers", "subscriber" -> {
                        Boolean active = boolFilter(f, "active");
                        List<Subscriber> subs = subscriberRepository.resolveSubscribers(
                                emptyIfBlank(strFilter(f, "q")),
                                active,
                                instantFilter(f, "since"),
                                instantFilter(f, "until"));
                        for (Subscriber s : subs) {
                            if (Boolean.FALSE.equals(s.getIsActive())) {
                                skippedInactive++;
                                continue;
                            }
                            DedupResult r = put(byEmail, toItem(s));
                            if (r == DedupResult.DUPLICATE) deduplicated++;
                        }
                    }
                    default -> throw new BadRequestException("Unknown segment kind: " + seg.getKind());
                }
            }
        }

        // 2) Explicit includes — load by IDs, may overwrite segment matches
        if (req.getInclude() != null && !req.getInclude().isEmpty()) {
            List<UUID> clientIds = collectIds(req.getInclude(), "client");
            List<UUID> subIds    = collectIds(req.getInclude(), "subscriber");

            if (!clientIds.isEmpty()) {
                for (Client c : clientRepository.findAllByIds(clientIds)) {
                    DedupResult r = put(byEmail, toItem(c));
                    if (r == DedupResult.DUPLICATE) deduplicated++;
                }
            }
            if (!subIds.isEmpty()) {
                for (Subscriber s : subscriberRepository.findAllByIds(subIds)) {
                    if (Boolean.FALSE.equals(s.getIsActive())) {
                        skippedInactive++;
                        continue;
                    }
                    DedupResult r = put(byEmail, toItem(s));
                    if (r == DedupResult.DUPLICATE) deduplicated++;
                }
            }
        }

        // 3) Excludes — strip by id (across both kinds)
        if (req.getExclude() != null && !req.getExclude().isEmpty()) {
            Set<UUID> excludeIds = new HashSet<>();
            for (ResolveRequest.RecipientRef ref : req.getExclude()) {
                if (ref != null && ref.getId() != null) excludeIds.add(ref.getId());
            }
            if (!excludeIds.isEmpty()) {
                byEmail.values().removeIf(item -> excludeIds.contains(item.getId()));
            }
        }

        long clients = byEmail.values().stream().filter(i -> "client".equals(i.getKind())).count();
        long subs = byEmail.values().stream().filter(i -> "subscriber".equals(i.getKind())).count();

        return new Materialised(new ArrayList<>(byEmail.values()), deduplicated, skippedInactive, clients, subs);
    }

    public ResolveResponse toResponse(Materialised m) {
        Map<String, Long> byKind = new LinkedHashMap<>();
        byKind.put("clients", m.clientCount);
        byKind.put("subscribers", m.subscriberCount);

        List<RecipientItem> preview = m.items.size() <= 10 ? m.items : m.items.subList(0, 10);

        return ResolveResponse.builder()
                .totalUnique(m.items.size())
                .byKind(byKind)
                .deduplicated(m.deduplicated)
                .skippedInactive(m.skippedInactive)
                .preview(new ArrayList<>(preview))
                .build();
    }

    /* =============================================================== conversion */

    public RecipientItem toItem(Client c) {
        return RecipientItem.builder()
                .kind("client")
                .id(c.getId())
                .name(c.getName())
                .email(c.getEmail())
                .phone(c.getPhone())
                .tags(buildClientTags(c))
                .createdAt(c.getCreatedAt())
                .build();
    }

    public RecipientItem toItem(Subscriber s) {
        List<String> tags = new ArrayList<>();
        if (s.getSource() != null) tags.add(s.getSource());
        tags.add(Boolean.TRUE.equals(s.getIsActive()) ? "active" : "inactive");
        return RecipientItem.builder()
                .kind("subscriber")
                .id(s.getId())
                .name(s.getName())
                .email(s.getEmail())
                .phone(null)
                .tags(tags)
                .createdAt(s.getCreatedAt())
                .build();
    }

    /* =============================================================== private util */

    private enum Kind { ALL, CLIENTS_ONLY, SUBSCRIBERS_ONLY }
    private enum DedupResult { ADDED, DUPLICATE, REPLACED }

    private DedupResult put(Map<String, RecipientItem> map, RecipientItem item) {
        if (item.getEmail() == null || item.getEmail().isBlank()) return DedupResult.DUPLICATE;
        String key = item.getEmail().trim().toLowerCase();
        RecipientItem prev = map.get(key);
        if (prev == null) {
            map.put(key, item);
            return DedupResult.ADDED;
        }
        // Prefer client over subscriber on collision (gives the recipient richer context)
        if ("subscriber".equals(prev.getKind()) && "client".equals(item.getKind())) {
            map.put(key, item);
            return DedupResult.REPLACED;
        }
        return DedupResult.DUPLICATE;
    }

    private static List<UUID> collectIds(List<ResolveRequest.RecipientRef> refs, String kindLower) {
        List<UUID> out = new ArrayList<>();
        for (ResolveRequest.RecipientRef ref : refs) {
            if (ref == null || ref.getKind() == null || ref.getId() == null) continue;
            if (kindLower.equalsIgnoreCase(ref.getKind())) out.add(ref.getId());
        }
        return out;
    }

    private static String nullIfBlank(String s) { return (s == null || s.isBlank()) ? null : s.trim(); }

    /** Always-non-null normalization for free-text LIKE params; "" matches everything. */
    private static String emptyIfBlank(String s) { return (s == null) ? "" : s.trim(); }

    private static String strFilter(Map<String, Object> f, String k) {
        Object v = f.get(k);
        return v == null ? null : v.toString();
    }

    private static Boolean boolFilter(Map<String, Object> f, String k) {
        Object v = f.get(k);
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(v.toString());
    }

    private static Short shortFilter(Map<String, Object> f, String k) {
        Object v = f.get(k);
        if (v == null) return null;
        if (v instanceof Number n) return n.shortValue();
        try { return Short.parseShort(v.toString()); }
        catch (NumberFormatException e) { return null; }
    }

    private static Instant instantFilter(Map<String, Object> f, String k) {
        Object v = f.get(k);
        if (v == null) return null;
        String s = v.toString();
        try {
            // bare ISO-DATE → start-of-day UTC
            if (s.length() == 10) return LocalDate.parse(s).atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
            return Instant.parse(s);
        } catch (Exception e) {
            throw new BadRequestException("Invalid date '" + s + "' for filter '" + k + "'");
        }
    }

    private Client.ClientStatus clientStatusFilter(Map<String, Object> f) {
        Object v = f.get("status");
        if (v == null) return null;
        try { return Client.ClientStatus.valueOf(v.toString().trim().toUpperCase()); }
        catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid client status '" + v + "'");
        }
    }

    private Kind parseKind(String raw) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("all")) return Kind.ALL;
        if (raw.equalsIgnoreCase("clients") || raw.equalsIgnoreCase("client")) return Kind.CLIENTS_ONLY;
        if (raw.equalsIgnoreCase("subscribers") || raw.equalsIgnoreCase("subscriber")) return Kind.SUBSCRIBERS_ONLY;
        throw new BadRequestException("Invalid kind '" + raw + "'. Allowed: all, clients, subscribers.");
    }

    private Client.ClientStatus parseClientStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return Client.ClientStatus.valueOf(raw.trim().toUpperCase()); }
        catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid clientStatus '" + raw + "'");
        }
    }

    private Comparator<RecipientItem> comparator(String field, Sort.Direction direction) {
        Comparator<RecipientItem> base = switch (field == null ? "createdAt" : field) {
            case "name"      -> Comparator.comparing(i -> i.getName() == null ? "" : i.getName(), String.CASE_INSENSITIVE_ORDER);
            case "email"     -> Comparator.comparing(i -> i.getEmail() == null ? "" : i.getEmail(), String.CASE_INSENSITIVE_ORDER);
            default          -> Comparator.comparing(RecipientItem::getCreatedAt,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return direction == Sort.Direction.ASC ? base : base.reversed();
    }

    private List<String> buildClientTags(Client c) {
        List<String> tags = new ArrayList<>();
        if (c.getStatus() != null) tags.add(c.getStatus().name().toLowerCase());
        if (c.getSource() != null) tags.add(c.getSource());
        return tags;
    }

    /* result tuple */
    public static final class Materialised {
        public final List<RecipientItem> items;
        public final long deduplicated;
        public final long skippedInactive;
        public final long clientCount;
        public final long subscriberCount;
        Materialised(List<RecipientItem> items, long dedup, long skipped, long clients, long subs) {
            this.items = items; this.deduplicated = dedup; this.skippedInactive = skipped;
            this.clientCount = clients; this.subscriberCount = subs;
        }
    }
}
