package syncqubits.ai.skc.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import syncqubits.ai.skc.dto.ErrorResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedException(UnauthorizedException ex, WebRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.error("Unauthorized [{}]: {}", traceId, ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .error(ex.getErrorCode())
                .message(ex.getMessage())
                .traceId(traceId)
                .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex, WebRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.error("Resource not found [{}]: {}", traceId, ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .error("NOT_FOUND")
                .message(ex.getMessage())
                .traceId(traceId)
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflictException(ConflictException ex, WebRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.error("Conflict [{}]: {}", traceId, ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .error("CONFLICT")
                .message(ex.getMessage())
                .traceId(traceId)
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequestException(BadRequestException ex, WebRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.error("Bad request [{}]: {}", traceId, ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .error("BAD_REQUEST")
                .message(ex.getMessage())
                .traceId(traceId)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex, WebRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.error("Validation error [{}]: {}", traceId, ex.getMessage());

        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        ErrorResponse error = ErrorResponse.builder()
                .error("VALIDATION_ERROR")
                .message("Validation failed")
                .fields(fieldErrors)
                .traceId(traceId)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex, WebRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.warn("ResponseStatusException [{}]: {} {}", traceId, ex.getStatusCode(), ex.getReason());
        ErrorResponse error = ErrorResponse.builder()
                .error(ex.getReason() != null ? ex.getReason() : ex.getStatusCode().toString())
                .message(ex.getReason() != null ? ex.getReason() : "Request failed")
                .traceId(traceId)
                .build();
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex, WebRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.error("Internal error [{}]: ", traceId, ex);

        ErrorResponse error = ErrorResponse.builder()
                .error("INTERNAL_ERROR")
                .message("An unexpected error occurred")
                .traceId(traceId)
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
