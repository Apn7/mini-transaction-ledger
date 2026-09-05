package com.ekramul.ledger.entry;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ekramul.ledger.entry.dto.DepositRequest;
import com.ekramul.ledger.entry.dto.EntryResponse;
import com.ekramul.ledger.entry.dto.WithdrawalRequest;

import jakarta.validation.Valid;

/** Money movements on a single account. */
@RestController
@RequestMapping("/api/accounts/{accountId}")
public class PostingController {

	private final PostingService postingService;

	public PostingController(PostingService postingService) {
		this.postingService = postingService;
	}

	@PostMapping("/deposits")
	@ResponseStatus(HttpStatus.CREATED)
	public EntryResponse deposit(@PathVariable Long accountId, @Valid @RequestBody DepositRequest request) {
		return postingService.deposit(accountId, request);
	}

	@PostMapping("/withdrawals")
	@ResponseStatus(HttpStatus.CREATED)
	public EntryResponse withdraw(@PathVariable Long accountId, @Valid @RequestBody WithdrawalRequest request) {
		return postingService.withdraw(accountId, request);
	}

	/** The account's statement: every entry, oldest first. */
	@GetMapping("/entries")
	public List<EntryResponse> entries(@PathVariable Long accountId) {
		return postingService.statementFor(accountId);
	}
}
