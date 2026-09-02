export type UiErrorKind =
  | 'validation'
  | 'not-found'
  | 'conflict'
  | 'unprocessable'
  | 'network'
  | 'server'
  | 'unknown';

export interface UiError {
  readonly kind: UiErrorKind;
  readonly message: string;
  readonly code?: string;
  readonly details?: Readonly<Record<string, string>>;
}
