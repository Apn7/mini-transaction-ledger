import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** Shape of the backend's health response. */
export interface HealthStatus {
  status: string;
}

/**
 * Talks to the backend's health endpoint.
 *
 * Components ask this service for data; only the service knows the URL.
 * Note the path is relative — the app never knows the backend's address.
 */
@Injectable({ providedIn: 'root' })
export class HealthService {
  private readonly http = inject(HttpClient);

  getHealth(): Observable<HealthStatus> {
    return this.http.get<HealthStatus>('/api/health');
  }
}
