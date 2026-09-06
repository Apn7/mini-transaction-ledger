import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { Account, AccountService } from './account.service';
import { HealthService } from './health.service';
import { Entry, PostingService } from './posting.service';

/** What the backend's `ApiError` looks like when a request is rejected. */
interface ApiError {
  message?: string;
  fieldErrors?: { field: string; message: string }[];
}

/**
 * The whole ledger screen.
 *
 * <p>One page, one component, no router — the app has a single screen and adding routing would
 * be ceremony for one view. Each method maps to exactly one backend endpoint, so the class reads
 * as a list of the things the API can do.
 */
@Component({
  imports: [DatePipe, DecimalPipe],
  selector: 'app-root',
  templateUrl: './app.html',
})
export class App implements OnInit {
  private readonly accountService = inject(AccountService);
  private readonly postingService = inject(PostingService);
  private readonly healthService = inject(HealthService);

  protected readonly backendStatus = signal('checking');
  protected readonly accounts = signal<Account[]>([]);
  protected readonly loading = signal(true);

  /** The account whose panel is open. Null means nothing is selected yet. */
  protected readonly selected = signal<Account | null>(null);
  protected readonly entries = signal<Entry[]>([]);

  /** One message bar for the whole page: the last thing that succeeded or failed. */
  protected readonly notice = signal<string | null>(null);
  protected readonly noticeIsError = signal(false);

  /**
   * True while a posting is in flight, which disables every button that starts one.
   *
   * <p>Without this, a double-clicked button sends two requests — and the idempotency key does
   * not save you, because each call mints a fresh reference. The key protects against a *retry*
   * of one request; it says nothing about two requests the user never meant to make.
   */
  protected readonly busy = signal(false);

  ngOnInit(): void {
    this.healthService.getHealth().subscribe({
      next: (response) => this.backendStatus.set(response.status),
      error: () => this.backendStatus.set('unreachable'),
    });

    this.reloadAccounts();
  }

  // --- accounts -------------------------------------------------------------------------

  protected openAccount(number: HTMLInputElement, owner: HTMLInputElement, currency: HTMLSelectElement): void {
    // The currency comes from a fixed list, so it needs no cleaning — unlike the two free-text
    // fields, which are trimmed before they are sent.
    this.busy.set(true);
    this.accountService.openAccount(number.value.trim(), owner.value.trim(), currency.value)
      .subscribe({
        next: (account) => {
          this.succeed(`Opened account ${account.accountNumber}`);
          number.value = '';
          owner.value = '';
          currency.value = 'BDT';
          this.reloadAccounts();
        },
        error: (error) => this.fail(error),
      });
  }

  protected select(account: Account): void {
    this.selected.set(account);
    this.postingService.statement(account.id).subscribe({
      next: (entries) => this.entries.set(entries),
      error: (error) => this.fail(error),
    });
  }

  // --- money ----------------------------------------------------------------------------

  protected deposit(amount: HTMLInputElement, description: HTMLInputElement): void {
    const account = this.selected();
    if (!account) {
      return;
    }

    this.busy.set(true);
    this.postingService.deposit(account.id, amount.value, description.value).subscribe({
      next: () => this.afterPosting(`Deposited ${amount.value}`, amount, description),
      error: (error) => this.fail(error),
    });
  }

  protected withdraw(amount: HTMLInputElement, description: HTMLInputElement): void {
    const account = this.selected();
    if (!account) {
      return;
    }

    this.busy.set(true);
    this.postingService.withdraw(account.id, amount.value, description.value).subscribe({
      next: () => this.afterPosting(`Withdrew ${amount.value}`, amount, description),
      error: (error) => this.fail(error),
    });
  }

  protected transfer(to: HTMLSelectElement, amount: HTMLInputElement, description: HTMLInputElement): void {
    const account = this.selected();
    if (!account) {
      return;
    }

    this.busy.set(true);
    this.postingService.transfer(account.id, Number(to.value), amount.value, description.value).subscribe({
      next: () => this.afterPosting(`Transferred ${amount.value}`, amount, description),
      error: (error) => this.fail(error),
    });
  }

  /** Everything the three posting methods do once the server has accepted the request. */
  private afterPosting(message: string, ...toClear: HTMLInputElement[]): void {
    this.succeed(message);
    toClear.forEach((input) => (input.value = ''));
    this.reloadAccounts();
  }

  // --- shared ---------------------------------------------------------------------------

  /**
   * Reloads the list, and re-reads the open account from that same response.
   *
   * <p>The balance shown always comes from the server. Nothing is added up in the browser.
   */
  private reloadAccounts(): void {
    this.accountService.listAccounts().subscribe({
      next: (accounts) => {
        this.accounts.set(accounts);
        this.loading.set(false);

        const open = this.selected();
        if (open) {
          const fresh = accounts.find((account) => account.id === open.id);
          if (fresh) {
            this.select(fresh);
          }
        }
      },
      error: (error) => {
        this.loading.set(false);
        this.fail(error);
      },
    });
  }

  private succeed(message: string): void {
    this.notice.set(message);
    this.noticeIsError.set(false);
    this.busy.set(false);
  }

  /**
   * Turns a failed request into one sentence.
   *
   * <p>The backend's `@RestControllerAdvice` already returns a useful body — a message, and a
   * list of rejected fields for a 400. This reads that body rather than inventing its own text,
   * so the user sees the real reason: "insufficient balance", "account number already exists".
   */
  private fail(error: HttpErrorResponse): void {
    const body = error.error as ApiError | null;

    // The server's messages already name their field — "amount is required" — so prefixing the
    // field name again would read "amount: amount is required".
    const fields = body?.fieldErrors?.map((violation) => violation.message);
    const message = fields?.length
      ? fields.join(' · ')
      : (body?.message ?? 'The server could not be reached.');

    this.notice.set(message);
    this.noticeIsError.set(true);
    this.busy.set(false);
  }

  /** Accounts other than the open one — the possible destinations for a transfer. */
  protected otherAccounts(): Account[] {
    const open = this.selected();
    return this.accounts().filter((account) => account.id !== open?.id);
  }
}
