import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { Account, AccountService } from './account.service';
import { HealthService } from './health.service';

@Component({
  imports: [DecimalPipe],
  selector: 'app-root',
  templateUrl: './app.html',
})
export class App implements OnInit {
  private readonly healthService = inject(HealthService);
  private readonly accountService = inject(AccountService);

  /** Signals: change the value and the screen updates itself. */
  protected readonly backendStatus = signal('checking...');

  /** Three states the screen can be in. Only one is shown at a time. */
  protected readonly accounts = signal<Account[]>([]);
  protected readonly loading = signal(true);
  protected readonly loadFailed = signal(false);

  /** Runs once, after Angular has created the component. */
  ngOnInit(): void {
    this.healthService.getHealth().subscribe({
      next: (response) => this.backendStatus.set(response.status),
      error: () => this.backendStatus.set('unreachable'),
    });

    this.accountService.listAccounts().subscribe({
      next: (accounts) => {
        this.accounts.set(accounts);
        this.loading.set(false);
      },
      error: () => {
        this.loadFailed.set(true);
        this.loading.set(false);
      },
    });
  }
}
