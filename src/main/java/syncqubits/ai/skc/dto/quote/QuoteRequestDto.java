package syncqubits.ai.skc.dto.quote;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

@Data
public class QuoteRequestDto {
    
    @NotBlank(message = "Name is required")
    @Size(max = 120, message = "Name must not exceed 120 characters")
    private String name;
    
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;
    
    @NotBlank(message = "Phone is required")
    @Size(max = 20, message = "Phone must not exceed 20 characters")
    private String phone;
    
    @NotBlank(message = "Event type is required")
    @Size(max = 40, message = "Event type must not exceed 40 characters")
    private String eventType;
    
    @NotNull(message = "Event date is required")
    @Future(message = "Event date must be in the future")
    private LocalDate eventDate;
    
    @NotNull(message = "Number of guests is required")
    @Min(value = 1, message = "Number of guests must be at least 1")
    private Integer guests;
    
    @Size(max = 200, message = "Venue must not exceed 200 characters")
    private String venue;
    
    @Size(max = 40, message = "Budget must not exceed 40 characters")
    private String budget;
    
    private String message;
}
