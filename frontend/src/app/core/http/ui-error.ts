export type UiErrorKind =
  'validation' | 'not-found' | 'conflict' | 'network' | 'server' | 'unknown';

export interface UiError {
  readonly kind: UiErrorKind;
  readonly message: string;
  readonly code?: string;
  readonly fieldErrors?: Readonly<Record<string, string>>;
}
