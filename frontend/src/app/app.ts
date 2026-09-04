import { Component, OnInit, inject, signal } from '@angular/core';
import { HealthService } from './health.service';

@Component({
  imports: [],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App implements OnInit {
  private readonly healthService = inject(HealthService);

  /** Signals: change the value and the screen updates itself. */
  protected readonly backendStatus = signal('checking...');

  /** Runs once, after Angular has created the component. */
  ngOnInit(): void {
    this.healthService.getHealth().subscribe({
      next: (response) => this.backendStatus.set(response.status),
      error: () => this.backendStatus.set('unreachable'),
    });
  }
}
