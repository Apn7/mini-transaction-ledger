package com.ekramul.ledger.web;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ekramul.ledger.account.AccountNotFoundException;
import com.ekramul.ledger.account.AccountNumberAlreadyExistsException;
import com.ekramul.ledger.account.InsufficientBalanceException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Turns exceptions into HTTP responses, in one place.
 *
 * <p>This is why service code never wraps its work in try-catch. Swallowing an exception inside
 * a {@code @Transactional} method would let the transaction commit a half-finished operation.
 * Errors are allowed to escape, and are translated here at the boundary.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/** A request that failed Bean Validation. Reports every rejected field, not just the first. */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
			HttpServletRequest request) {

		List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> new ApiError.FieldViolation(error.getField(), error.getDefaultMessage()))
				.toList();

		ApiError body = new ApiError(
				Instant.now(),
				HttpStatus.BAD_REQUEST.value(),
				"Validation failed",
				"One or more fields are invalid",
				request.getRequestURI(),
				violations);

		return ResponseEntity.badRequest().body(body);
	}

	/** The request named something that does not exist. */
	@ExceptionHandler(AccountNotFoundException.class)
	public ResponseEntity<ApiError> handleAccountNotFound(AccountNotFoundException ex,
			HttpServletRequest request) {

		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(
				HttpStatus.NOT_FOUND.value(), "Not Found", ex.getMessage(), request.getRequestURI()));
	}

	/** A business rule said no. 409 means "the request was understood but conflicts with state". */
	@ExceptionHandler(AccountNumberAlreadyExistsException.class)
	public ResponseEntity<ApiError> handleDuplicateAccountNumber(AccountNumberAlreadyExistsException ex,
			HttpServletRequest request) {

		return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
				HttpStatus.CONFLICT.value(), "Conflict", ex.getMessage(), request.getRequestURI()));
	}

	/**
	 * The request was valid and the account exists, but the money is not there.
	 *
	 * <p>422 rather than 400: nothing about the request was malformed. 409 would also be
	 * defensible, since it conflicts with current state.
	 */
	@ExceptionHandler(InsufficientBalanceException.class)
	public ResponseEntity<ApiError> handleInsufficientBalance(InsufficientBalanceException ex,
			HttpServletRequest request) {

		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError.of(
				HttpStatus.UNPROCESSABLE_ENTITY.value(), "Insufficient balance",
				ex.getMessage(), request.getRequestURI()));
	}

	/**
	 * The database rejected the write — a unique or check constraint.
	 *
	 * <p>The service already checks for duplicates, but two simultaneous requests can both pass
	 * that check before either commits. The constraint is the backstop, and this keeps the
	 * response sensible when it fires. The real cause is logged, never returned.
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex,
			HttpServletRequest request) {

		log.warn("Database constraint rejected a write on {}", request.getRequestURI(), ex);

		return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
				HttpStatus.CONFLICT.value(), "Conflict",
				"The request conflicts with existing data", request.getRequestURI()));
	}

	/** Anything unforeseen. Log the detail, tell the client nothing that could help an attacker. */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {

		log.error("Unhandled exception on {}", request.getRequestURI(), ex);

		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiError.of(
				HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error",
				"Something went wrong", request.getRequestURI()));
	}
}
