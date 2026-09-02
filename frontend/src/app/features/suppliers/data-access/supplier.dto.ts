export interface SupplierDto {
  readonly id: string;
  readonly name: string;
  readonly identifier: string | null;
  readonly contact: string | null;
  readonly notes: string | null;
  readonly active: boolean;
}

export interface SupplierPageDto {
  readonly content: readonly SupplierDto[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
}

export interface SupplierSearchQuery {
  readonly name?: string;
  readonly active?: boolean;
  readonly page: number;
  readonly size: number;
}

export interface RegisterSupplierRequest {
  readonly name: string;
  readonly identifier: string | null;
  readonly contact: string | null;
  readonly notes: string | null;
}
