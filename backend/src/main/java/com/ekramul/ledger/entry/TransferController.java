package com.ekramul.ledger.entry;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ekramul.ledger.entry.dto.EntryResponse;
import com.ekramul.ledger.entry.dto.TransferRequest;

import jakarta.validation.Valid;

/**
 * Transfers between accounts.
 *
 * <p>Its own endpoint rather than one hanging off an account, because a transfer belongs to two
 * accounts equally — neither one owns it.
 */
@RestController
@RequestMapping("/api/transfers")
public class TransferController {

	private final PostingService postingService;

	public TransferController(PostingService postingService) {
		this.postingService = postingService;
	}

	/** @return the debit entry first, then the credit entry */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public List<EntryResponse> transfer(@Valid @RequestBody TransferRequest request) {
		return postingService.transfer(request);
	}
}
