package syncqubits.ai.skc.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import syncqubits.ai.skc.entity.Review;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Review r WHERE r.token = :token AND r.type = 'INVITATION' AND r.usedAt IS NULL AND r.expiresAt > :now")
    Optional<Review> findValidInvitationByTokenForUpdate(@Param("token") String token, @Param("now") Instant now);

    Optional<Review> findByToken(String token);

    @Query("SELECT r FROM Review r WHERE r.type = 'REVIEW' AND " +
           "(:status IS NULL OR r.status = :status) AND " +
           "(:minRating IS NULL OR r.overallRating >= :minRating)")
    Page<Review> searchReviews(
        @Param("status") Review.ReviewStatus status,
        @Param("minRating") Short minRating,
        Pageable pageable
    );

    @Query(value = "SELECT r FROM Review r WHERE r.type = 'REVIEW' AND " +
           "(:status IS NULL OR r.status = :status) AND " +
           "(:minRating IS NULL OR r.overallRating >= :minRating) AND " +
           "(:isPublic IS NULL OR r.isPublic = :isPublic) AND " +
           "(:isFeatured IS NULL OR r.isFeatured = :isFeatured) AND " +
           "(LOWER(COALESCE(r.reviewerName,'')) LIKE LOWER(CONCAT('%',:q,'%')) " +
           "OR LOWER(COALESCE(r.comments,'')) LIKE LOWER(CONCAT('%',:q,'%')) " +
           "OR LOWER(r.client.name) LIKE LOWER(CONCAT('%',:q,'%')) " +
           "OR LOWER(r.client.email) LIKE LOWER(CONCAT('%',:q,'%')))",
           countQuery = "SELECT COUNT(r) FROM Review r WHERE r.type = 'REVIEW' AND " +
           "(:status IS NULL OR r.status = :status) AND " +
           "(:minRating IS NULL OR r.overallRating >= :minRating) AND " +
           "(:isPublic IS NULL OR r.isPublic = :isPublic) AND " +
           "(:isFeatured IS NULL OR r.isFeatured = :isFeatured) AND " +
           "(LOWER(COALESCE(r.reviewerName,'')) LIKE LOWER(CONCAT('%',:q,'%')) " +
           "OR LOWER(COALESCE(r.comments,'')) LIKE LOWER(CONCAT('%',:q,'%')) " +
           "OR LOWER(r.client.name) LIKE LOWER(CONCAT('%',:q,'%')) " +
           "OR LOWER(r.client.email) LIKE LOWER(CONCAT('%',:q,'%')))")
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"client"})
    Page<Review> adminSearchReviews(
        @Param("status") Review.ReviewStatus status,
        @Param("minRating") Short minRating,
        @Param("isPublic") Boolean isPublic,
        @Param("isFeatured") Boolean isFeatured,
        @Param("q") String q,
        Pageable pageable
    );

    @Query(value = "SELECT r FROM Review r WHERE r.type = 'INVITATION' AND " +
           "(:used IS NULL OR (:used = TRUE AND r.usedAt IS NOT NULL) OR (:used = FALSE AND r.usedAt IS NULL)) AND " +
           "(LOWER(r.client.name) LIKE LOWER(CONCAT('%',:q,'%')) " +
           "OR LOWER(r.client.email) LIKE LOWER(CONCAT('%',:q,'%')))",
           countQuery = "SELECT COUNT(r) FROM Review r WHERE r.type = 'INVITATION' AND " +
           "(:used IS NULL OR (:used = TRUE AND r.usedAt IS NOT NULL) OR (:used = FALSE AND r.usedAt IS NULL)) AND " +
           "(LOWER(r.client.name) LIKE LOWER(CONCAT('%',:q,'%')) " +
           "OR LOWER(r.client.email) LIKE LOWER(CONCAT('%',:q,'%')))")
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"client"})
    Page<Review> adminSearchInvitations(
        @Param("used") Boolean used,
        @Param("q") String q,
        Pageable pageable
    );

    @Query("SELECT r FROM Review r WHERE r.type = 'REVIEW' AND r.isPublic = true AND " +
           "(:minRating IS NULL OR r.overallRating >= :minRating)")
    Page<Review> findPublicReviews(@Param("minRating") Short minRating, Pageable pageable);

    @Query("SELECT r FROM Review r WHERE r.type = 'REVIEW' AND r.isFeatured = true")
    Page<Review> findFeaturedReviews(Pageable pageable);

    @Query("SELECT r FROM Review r WHERE r.type = 'INVITATION'")
    Page<Review> findAllInvitations(Pageable pageable);

    long countByTypeAndStatus(Review.ReviewType type, Review.ReviewStatus status);

    @Query("SELECT r FROM Review r WHERE r.client.id = :clientId AND r.type = 'REVIEW' ORDER BY r.createdAt DESC")
    java.util.List<Review> findReviewsByClientIdOrderByCreatedAtDesc(@Param("clientId") UUID clientId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.client.id = :clientId AND r.type = 'REVIEW'")
    long countReviewsByClientId(@Param("clientId") UUID clientId);

    @Query("SELECT r.client.id, COUNT(r) FROM Review r WHERE r.client.id IN :ids AND r.type = 'REVIEW' GROUP BY r.client.id")
    java.util.List<Object[]> countReviewsByClientIds(@Param("ids") java.util.Collection<UUID> ids);
}
