export interface ApiErrorResponse {
  readonly timestamp: string;
  readonly status: number;
  readonly error: string;
  readonly code: string;
  readonly message: string;
  readonly path: string;
  readonly details: Record<string, string>;
}
