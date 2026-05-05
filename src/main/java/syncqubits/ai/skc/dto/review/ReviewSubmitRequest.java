package syncqubits.ai.skc.dto.review;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ReviewSubmitRequest {
    
    @NotBlank(message = "Reviewer name is required")
    @Size(max = 120, message = "Reviewer name must not exceed 120 characters")
    private String reviewerName;
    
    @NotNull(message = "Overall rating is required")
    @Min(value = 1, message = "Overall rating must be between 1 and 5")
    @Max(value = 5, message = "Overall rating must be between 1 and 5")
    private Short overallRating;
    
    @NotNull(message = "Food quality rating is required")
    @Min(value = 1, message = "Food quality rating must be between 1 and 5")
    @Max(value = 5, message = "Food quality rating must be between 1 and 5")
    private Short foodQualityRating;
    
    @NotNull(message = "Taste rating is required")
    @Min(value = 1, message = "Taste rating must be between 1 and 5")
    @Max(value = 5, message = "Taste rating must be between 1 and 5")
    private Short tasteRating;
    
    @NotNull(message = "Presentation rating is required")
    @Min(value = 1, message = "Presentation rating must be between 1 and 5")
    @Max(value = 5, message = "Presentation rating must be between 1 and 5")
    private Short presentationRating;
    
    @NotNull(message = "Staff behavior rating is required")
    @Min(value = 1, message = "Staff behavior rating must be between 1 and 5")
    @Max(value = 5, message = "Staff behavior rating must be between 1 and 5")
    private Short staffBehaviorRating;
    
    @NotNull(message = "Timeliness rating is required")
    @Min(value = 1, message = "Timeliness rating must be between 1 and 5")
    @Max(value = 5, message = "Timeliness rating must be between 1 and 5")
    private Short timelinessRating;
    
    @NotNull(message = "Service quality rating is required")
    @Min(value = 1, message = "Service quality rating must be between 1 and 5")
    @Max(value = 5, message = "Service quality rating must be between 1 and 5")
    private Short serviceQualityRating;
    
    private String comments;
    
    private String suggestions;
    
    @Size(max = 10, message = "Recommend must not exceed 10 characters")
    private String recommend;
}
