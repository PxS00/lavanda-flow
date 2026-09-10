import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api-base-url.token';
import { AuthSessionDto, LoginRequest } from './auth.dto';

/** Typed HTTP client for the backend-authoritative operator session contract. */
@Service()
export class AuthApiService {
  private readonly http = inject(HttpClient);
  private readonly authUrl = buildAuthUrl(inject(API_BASE_URL));

  getSession(): Observable<AuthSessionDto> {
    return this.http.get<AuthSessionDto>(`${this.authUrl}/session`);
  }

  login(request: LoginRequest): Observable<AuthSessionDto> {
    return this.http.post<AuthSessionDto>(`${this.authUrl}/login`, request);
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${this.authUrl}/logout`, {});
  }
}

function buildAuthUrl(apiBaseUrl: string): string {
  const normalizedBaseUrl = apiBaseUrl.replace(/\/+$/, '');
  const versionedBaseUrl = normalizedBaseUrl.endsWith('/api/v1')
    ? normalizedBaseUrl
    : `${normalizedBaseUrl}/api/v1`;

  return `${versionedBaseUrl}/auth`;
}
