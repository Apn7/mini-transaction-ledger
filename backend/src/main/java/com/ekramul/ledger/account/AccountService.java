package com.ekramul.ledger.account;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ekramul.ledger.account.dto.AccountResponse;
import com.ekramul.ledger.account.dto.CreateAccountRequest;

/** Business rules for accounts. Holds no HTTP concerns and no SQL. */
@Service
public class AccountService {

	private final AccountRepository accountRepository;

	public AccountService(AccountRepository accountRepository) {
		this.accountRepository = accountRepository;
	}

	@Transactional
	public AccountResponse openAccount(CreateAccountRequest request) {
		Account account = new Account(
				request.accountNumber(),
				request.ownerName(),
				request.currency());

		return AccountResponse.from(accountRepository.save(account));
	}

	/**
	 * {@code readOnly} lets Hibernate skip dirty-checking and lets the database optimise the
	 * transaction. It also documents that nothing here writes.
	 */
	@Transactional(readOnly = true)
	public List<AccountResponse> listAccounts() {
		return accountRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
				.stream()
				.map(AccountResponse::from)
				.toList();
	}
}
