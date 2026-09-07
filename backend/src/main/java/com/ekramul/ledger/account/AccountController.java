package com.ekramul.ledger.account;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ekramul.ledger.account.dto.AccountResponse;
import com.ekramul.ledger.account.dto.CreateAccountRequest;

import jakarta.validation.Valid;

/** HTTP entry point for accounts. Translates requests into service calls and back. */
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

	private final AccountService accountService;

	public AccountController(AccountService accountService) {
		this.accountService = accountService;
	}

	/**
	 * {@code @Valid} runs the constraints on the request before this method body executes.
	 * Returns 201 Created with a Location header pointing at the new resource.
	 */
	@PostMapping
	public ResponseEntity<AccountResponse> openAccount(@Valid @RequestBody CreateAccountRequest request) {
		AccountResponse created = accountService.openAccount(request);
		return ResponseEntity
				.created(URI.create("/api/accounts/" + created.id()))
				.body(created);
	}

	@GetMapping
	public List<AccountResponse> listAccounts() {
		return accountService.listAccounts();
	}

	/** Follows the {@code Location} header returned by {@link #openAccount}. */
	@GetMapping("/{accountId}")
	public AccountResponse getAccount(@PathVariable Long accountId) {
		return accountService.getAccount(accountId);
	}
}
