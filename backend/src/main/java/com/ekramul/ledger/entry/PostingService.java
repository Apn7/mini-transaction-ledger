package com.ekramul.ledger.entry;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ekramul.ledger.account.Account;
import com.ekramul.ledger.account.AccountService;
import com.ekramul.ledger.entry.dto.DepositRequest;
import com.ekramul.ledger.entry.dto.EntryResponse;

/** Records money movements. Every method here is one all-or-nothing unit of work. */
@Service
public class PostingService {

	private final LedgerTransactionRepository transactionRepository;
	private final LedgerEntryRepository entryRepository;
	private final AccountService accountService;

	public PostingService(LedgerTransactionRepository transactionRepository,
			LedgerEntryRepository entryRepository,
			AccountService accountService) {
		this.transactionRepository = transactionRepository;
		this.entryRepository = entryRepository;
		this.accountService = accountService;
	}

	/**
	 * Puts money into an account.
	 *
	 * <p>Three things happen, and either all of them are saved or none are: the event is
	 * recorded, the balance goes up, and the statement line is written.
	 *
	 * <p>Nothing here catches exceptions. If any step fails the whole thing is rolled back, which
	 * is only possible because the failure is allowed to escape this method.
	 */
	@Transactional
	public EntryResponse deposit(Long accountId, DepositRequest request) {
		LedgerTransaction transaction = transactionRepository.save(new LedgerTransaction(
				request.reference(), TransactionType.DEPOSIT, request.description()));

		Account account = accountService.creditAccount(accountId, request.amount());

		LedgerEntry entry = entryRepository.save(new LedgerEntry(
				transaction, account, Direction.CREDIT, request.amount(), account.getBalance()));

		return EntryResponse.from(entry);
	}

	@Transactional(readOnly = true)
	public List<EntryResponse> statementFor(Long accountId) {
		return entryRepository.findByAccountIdOrderByIdAsc(accountId)
				.stream()
				.map(EntryResponse::from)
				.toList();
	}
}
