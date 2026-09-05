package com.ekramul.ledger.web;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One error shape for the whole API, so clients never have to guess what a failure looks like.
 *
 * <p>{@code fieldErrors} is omitted from the JSON when empty.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
		Instant timestamp,
		int status,
		String error,
		String message,
		String path,
		List<FieldViolation> fieldErrors) {

	/** A single rejected field, so the client can highlight the right input. */
	public record FieldViolation(String field, String message) {
	}

	public static ApiError of(int status, String error, String message, String path) {
		return new ApiError(Instant.now(), status, error, message, path, List.of());
	}
}
