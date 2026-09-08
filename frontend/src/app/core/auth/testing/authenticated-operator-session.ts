import { signal } from '@angular/core';
import { of } from 'rxjs';

import { AuthSessionState } from '../auth-session.service';

export function authenticatedOperatorSession(username = 'Operator') {
  const state = signal<AuthSessionState>({ kind: 'authenticated', username });

  return {
    state: state.asReadonly(),
    username: signal(username).asReadonly(),
    bootstrap: () => of(state()),
    logout: () => of<AuthSessionState>({ kind: 'unauthenticated' }),
  };
}
