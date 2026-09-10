export interface AuthSessionDto {
  readonly authenticated: boolean;
  readonly username: string | null;
}

export interface LoginRequest {
  readonly username: string;
  readonly password: string;
}
