package com.example.state_machine.exception;

import com.example.state_machine.service.advance.PreconditionsNotMetException;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PreconditionsNotMetException.class)
    public ResponseEntity<?> handlePreconditions(PreconditionsNotMetException ex) {
        log.warn("Preconditions not met at state {}: {}", ex.getState(), ex.getErrors());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Err.builder()
                .error("PRECONDITIONS_NOT_MET")
                .state(String.valueOf(ex.getState()))
                .message("One or more preconditions failed")
                .errors(ex.getErrors()) // список PreconditionError
                .build());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<?> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Err.builder()
                .error("NOT_FOUND")
                .message(ex.getMessage())
                .build());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<?> handleBadRequest(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Err.builder()
                .error("BAD_REQUEST")
                .message(ex.getMessage())
                .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex) {
        var details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(), "message", fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Err.builder()
                .error("VALIDATION_ERROR")
                .message("Request validation failed")
                .errors(details)
                .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleOthers(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Err.builder()
                .error("INTERNAL_ERROR")
                .message("Unexpected error")
                .build());
    }

    @Value
    @Builder
    public static class Err {
        String error;                 // код ошибки (ожидается в Postman)
        String state;                 // текущее состояние процесса (если применимо)
        String message;               // человекочитаемое сообщение
        Object errors;                // список деталей (PreconditionError[] | List<Map<...>>)
    }
}
