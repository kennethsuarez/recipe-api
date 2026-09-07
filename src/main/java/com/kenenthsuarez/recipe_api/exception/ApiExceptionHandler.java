package com.kenenthsuarez.recipe_api.exception;

import jakarta.persistence.OptimisticLockException;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.transaction.CannotCreateTransactionException;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(RecipeNotFoundException.class)
    public ProblemDetail notFound(RecipeNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(InvalidRecipeException.class)
    public ProblemDetail invalid(InvalidRecipeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    public ProblemDetail conflict(Exception exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Recipe was changed concurrently; reload before retrying");
    }

    @ExceptionHandler({TransientDataAccessException.class, DataAccessResourceFailureException.class,
            CannotCreateTransactionException.class})
    public ResponseEntity<ProblemDetail> unavailable(Exception exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "1")
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Database temporarily unavailable or busy"));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setProperty("errors", exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage()).toList());
        return handleExceptionInternal(exception, problem, headers, status, request);
    }
}
