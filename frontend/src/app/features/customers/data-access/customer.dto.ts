export interface CustomerDto {
  readonly id: string;
  readonly name: string;
  readonly phone: string | null;
  readonly email: string | null;
  readonly active: boolean;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface CustomerPageDto {
  readonly content: readonly CustomerDto[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
}

export interface CustomerSearchQuery {
  readonly q?: string;
  readonly active?: boolean;
  readonly page: number;
  readonly size: number;
}

/** Complete contact fields for registration or replacement; state has explicit action endpoints. */
export interface CustomerRequest {
  readonly name: string;
  readonly phone: string | null;
  readonly email: string | null;
}
