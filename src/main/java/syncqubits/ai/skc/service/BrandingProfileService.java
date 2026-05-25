package syncqubits.ai.skc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import syncqubits.ai.skc.dto.branding.BrandingProfileResponse;
import syncqubits.ai.skc.dto.branding.BrandingProfileUpdateRequest;
import syncqubits.ai.skc.entity.BrandingProfile;
import syncqubits.ai.skc.repository.BrandingProfileRepository;

/**
 * Owns the singleton branding profile.
 *
 * <p>First-call semantics: {@link #get()} eagerly creates the row with
 * sensible defaults (lifted from the existing frontend brand constants)
 * the very first time the endpoint is hit. Admins always have a row to
 * edit; they never see a "no profile exists" empty state.
 *
 * <p>Update semantics: {@link #update(BrandingProfileUpdateRequest)} applies
 * only non-null fields, so the form can save partial sections without
 * clobbering the rest. Empty strings are treated as "clear this field" —
 * intentional, so admins can blank out values they previously set.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BrandingProfileService {

    private final BrandingProfileRepository repository;

    @Transactional
    public BrandingProfileResponse get() {
        return toResponse(getOrCreate());
    }

    @Transactional
    public BrandingProfileResponse update(BrandingProfileUpdateRequest req) {
        BrandingProfile p = getOrCreate();
        apply(p, req);
        BrandingProfile saved = repository.save(p);
        log.info("BrandingProfile updated id={}", saved.getId());
        return toResponse(saved);
    }

    /* ─────────────────────────── internals ─────────────────────────────── */

    private BrandingProfile getOrCreate() {
        return repository.findFirstByOrderByUpdatedAtDesc()
                .orElseGet(() -> repository.save(seedDefaults()));
    }

    /** Seed a brand-new row with values that match the existing public
     *  site so the first letterhead render isn't blank. Admins override
     *  any of these by editing the form. */
    private BrandingProfile seedDefaults() {
        return BrandingProfile.builder()
                .brandName("Sri Karthikeya Caterers")
                .tagline("Authentic Taste · Premium Experience")
                .establishedYear(2009)
                .phonePrimary("+91 81258 20110")
                .email("info@srikarthikeyacaterers.in")
                .website("https://www.srikarthikeyacaterers.in")
                .addressLine1("H.No 2-437, Sai Baba Temple Road")
                .addressLine2("PS Rao Nagar, Dammaiguda")
                .city("Hyderabad")
                .state("Telangana")
                .pincode("500083")
                .primaryColor("#7a1d1d")
                .accentColor("#c89b3c")
                .inkColor("#1a1a1a")
                .displayFont("Playfair Display")
                .bodyFont("Inter")
                .brandPromise("Pure Vegetarian Catering Crafted with Tradition & Excellence")
                .socialInstagram("https://www.instagram.com/srikarthikeya.caterers")
                .build();
    }

    private void apply(BrandingProfile p, BrandingProfileUpdateRequest r) {
        if (r.getBrandName()        != null) p.setBrandName(blankToNull(r.getBrandName()));
        if (r.getTagline()          != null) p.setTagline(blankToNull(r.getTagline()));
        if (r.getEstablishedYear()  != null) p.setEstablishedYear(r.getEstablishedYear());

        if (r.getPhonePrimary()     != null) p.setPhonePrimary(blankToNull(r.getPhonePrimary()));
        if (r.getPhoneSecondary()   != null) p.setPhoneSecondary(blankToNull(r.getPhoneSecondary()));
        if (r.getEmail()            != null) p.setEmail(blankToNull(r.getEmail()));
        if (r.getWebsite()          != null) p.setWebsite(blankToNull(r.getWebsite()));

        if (r.getAddressLine1()     != null) p.setAddressLine1(blankToNull(r.getAddressLine1()));
        if (r.getAddressLine2()     != null) p.setAddressLine2(blankToNull(r.getAddressLine2()));
        if (r.getCity()             != null) p.setCity(blankToNull(r.getCity()));
        if (r.getState()            != null) p.setState(blankToNull(r.getState()));
        if (r.getPincode()          != null) p.setPincode(blankToNull(r.getPincode()));

        if (r.getPrimaryColor()     != null) p.setPrimaryColor(blankToNull(r.getPrimaryColor()));
        if (r.getSecondaryColor()   != null) p.setSecondaryColor(blankToNull(r.getSecondaryColor()));
        if (r.getAccentColor()      != null) p.setAccentColor(blankToNull(r.getAccentColor()));
        if (r.getInkColor()         != null) p.setInkColor(blankToNull(r.getInkColor()));

        if (r.getDisplayFont()      != null) p.setDisplayFont(blankToNull(r.getDisplayFont()));
        if (r.getBodyFont()         != null) p.setBodyFont(blankToNull(r.getBodyFont()));

        if (r.getGstin()            != null) p.setGstin(blankToNull(r.getGstin()));
        if (r.getFssaiLicense()     != null) p.setFssaiLicense(blankToNull(r.getFssaiLicense()));
        if (r.getCin()              != null) p.setCin(blankToNull(r.getCin()));
        if (r.getPanNumber()        != null) p.setPanNumber(blankToNull(r.getPanNumber()));

        if (r.getBrandPromise()     != null) p.setBrandPromise(blankToNull(r.getBrandPromise()));
        if (r.getLegalDisclaimer()  != null) p.setLegalDisclaimer(blankToNull(r.getLegalDisclaimer()));

        if (r.getSocialInstagram()  != null) p.setSocialInstagram(blankToNull(r.getSocialInstagram()));
        if (r.getSocialFacebook()   != null) p.setSocialFacebook(blankToNull(r.getSocialFacebook()));
        if (r.getSocialYoutube()    != null) p.setSocialYoutube(blankToNull(r.getSocialYoutube()));
    }

    /** Empty-string from the form means "clear this field"; store NULL so
     *  downstream renderers can fall back to defaults via COALESCE. */
    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private BrandingProfileResponse toResponse(BrandingProfile p) {
        return BrandingProfileResponse.builder()
                .id(p.getId())
                .brandName(p.getBrandName())
                .tagline(p.getTagline())
                .establishedYear(p.getEstablishedYear())
                .phonePrimary(p.getPhonePrimary())
                .phoneSecondary(p.getPhoneSecondary())
                .email(p.getEmail())
                .website(p.getWebsite())
                .addressLine1(p.getAddressLine1())
                .addressLine2(p.getAddressLine2())
                .city(p.getCity())
                .state(p.getState())
                .pincode(p.getPincode())
                .primaryColor(p.getPrimaryColor())
                .secondaryColor(p.getSecondaryColor())
                .accentColor(p.getAccentColor())
                .inkColor(p.getInkColor())
                .displayFont(p.getDisplayFont())
                .bodyFont(p.getBodyFont())
                .gstin(p.getGstin())
                .fssaiLicense(p.getFssaiLicense())
                .cin(p.getCin())
                .panNumber(p.getPanNumber())
                .brandPromise(p.getBrandPromise())
                .legalDisclaimer(p.getLegalDisclaimer())
                .socialInstagram(p.getSocialInstagram())
                .socialFacebook(p.getSocialFacebook())
                .socialYoutube(p.getSocialYoutube())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
