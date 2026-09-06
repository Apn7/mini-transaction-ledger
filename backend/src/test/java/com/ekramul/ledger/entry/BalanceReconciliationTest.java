package com.ekramul.ledger.entry;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.ekramul.ledger.account.AccountService;
import com.ekramul.ledger.account.dto.AccountResponse;
import com.ekramul.ledger.account.dto.CreateAccountRequest;
import com.ekramul.ledger.entry.dto.DepositRequest;
import com.ekramul.ledger.entry.dto.TransferRequest;
import com.ekramul.ledger.entry.dto.WithdrawalRequest;

/**
 * The stored balance must equal the sum of the entries that produced it.
 *
 * <p>{@code accounts.balance} is a materialized derived aggregate — a cached total kept so that
 * reading a balance does not have to scan an account's whole history. The entries are canonical.
 * A cache is only trustworthy if you can prove it still agrees with the source, and this is that
 * proof.
 *
 * <p>Each account is put through a realistic mix — a deposit, a withdrawal, a transfer out and a
 * transfer in — with amounts that do not divide evenly, so a rounding mistake would show up.
 *
 * <p>Deliberately not {@code @Transactional}. Inside a test transaction the entities would still
 * be in Hibernate's first-level cache, and the test would be reconciling objects in memory
 * against themselves. Committing means every read below comes back from PostgreSQL.
 */
@SpringBootTest
class BalanceReconciliationTest {

	private static final BigDecimal DEPOSIT = new BigDecimal("500.00");
	private static final BigDecimal WITHDRAWAL = new BigDecimal("120.50");
	private static final BigDecimal SENT = new BigDecimal("75.25");
	private static final BigDecimal RECEIVED = new BigDecimal("30.00");

	@Autowired
	private AccountService accountService;

	@Autowired
	private PostingService postingService;

	@Autowired
	private LedgerEntryRepository entryRepository;

	@Test
	void storedBalanceEqualsTheSumOfItsEntries() {
		long stamp = System.currentTimeMillis();
		Long a = openAccount("RECA-" + stamp);
		Long b = openAccount("RECB-" + stamp);

		postingService.deposit(a, new DepositRequest(UUID.randomUUID(), DEPOSIT, "salary"));
		postingService.withdraw(a, new WithdrawalRequest(UUID.randomUUID(), WITHDRAWAL, "rent"));
		postingService.transfer(new TransferRequest(UUID.randomUUID(), a, b, SENT, "to savings"));
		postingService.transfer(new TransferRequest(UUID.randomUUID(), b, a, RECEIVED, "refund"));

		// 500.00 - 120.50 - 75.25 + 30.00
		assertThat(balanceOf(a)).as("balance of A").isEqualByComparingTo("334.25");
		assertThat(balanceOf(b)).as("balance of B").isEqualByComparingTo("45.25");

		// The reconciliation itself: replay the history and see if you land on the same number.
		assertThat(sumOfEntries(a)).as("A: entries replayed").isEqualByComparingTo(balanceOf(a));
		assertThat(sumOfEntries(b)).as("B: entries replayed").isEqualByComparingTo(balanceOf(b));

		// The running snapshot on the newest entry must agree too. This catches a balance_after
		// written from a value read before the change rather than after it.
		assertThat(lastBalanceAfter(a)).as("A: balance_after on the newest entry")
				.isEqualByComparingTo(balanceOf(a));
	}

	/**
	 * Replays an account's history: credits add, debits subtract.
	 *
	 * <p>Amounts are always stored positive, so the direction is what carries the sign.
	 */
	private BigDecimal sumOfEntries(Long accountId) {
		return entryRepository.findByAccountIdOrderByIdAsc(accountId).stream()
				.map(entry -> entry.getDirection() == Direction.CREDIT
						? entry.getAmount()
						: entry.getAmount().negate())
				.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private BigDecimal lastBalanceAfter(Long accountId) {
		List<LedgerEntry> entries = entryRepository.findByAccountIdOrderByIdAsc(accountId);
		return entries.get(entries.size() - 1).getBalanceAfter();
	}

	private Long openAccount(String accountNumber) {
		AccountResponse account = accountService.openAccount(
				new CreateAccountRequest(accountNumber, "Reconciliation Test", "BDT"));
		return account.id();
	}

	private BigDecimal balanceOf(Long accountId) {
		return accountService.listAccounts().stream()
				.filter(account -> account.id().equals(accountId))
				.map(AccountResponse::balance)
				.findFirst()
				.orElseThrow();
	}
}
