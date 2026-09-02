import { UiError } from './ui-error';

export interface UiErrorPresentation {
  readonly message: string;
}

const CODE_MESSAGES: Readonly<Record<string, string>> = {
  INVENTORY_ITEM_NOT_FOUND: 'Item de estoque não encontrado.',
  SUPPLIER_NOT_FOUND: 'Fornecedor não encontrado.',
  INACTIVE_INVENTORY_ITEM: 'Este item de estoque está inativo.',
  INACTIVE_SUPPLIER: 'Este fornecedor está inativo.',
  INSUFFICIENT_ELIGIBLE_STOCK: 'Não há estoque elegível suficiente para concluir a saída.',
  MINIMUM_STOCK_LEVEL_NOT_FOUND: 'Estoque mínimo não configurado.',
  INVALID_MINIMUM_STOCK_QUANTITY: 'Informe uma quantidade mínima válida.',
  INVALID_BATCH_DATA: 'Os dados do lote são inválidos.',
  VALIDATION_ERROR: 'Revise os dados informados.',
  INVALID_REQUEST_PARAMETER: 'Um parâmetro informado é inválido.',
  MALFORMED_REQUEST_BODY: 'Não foi possível processar os dados enviados.',
  MISSING_REQUEST_PARAMETER: 'Falta uma informação obrigatória.',
};

const KIND_MESSAGES: Readonly<Record<UiError['kind'], string>> = {
  validation: 'Revise os dados informados.',
  'not-found': 'O recurso solicitado não foi encontrado.',
  conflict: 'A operação entrou em conflito com o estado atual.',
  unprocessable: 'Não foi possível concluir a operação no estado atual.',
  network: 'Não foi possível conectar ao servidor.',
  server: 'Ocorreu um erro no servidor. Tente novamente.',
  unknown: 'Ocorreu um erro inesperado. Tente novamente.',
};

export function localizeUiError(error: UiError): UiErrorPresentation {
  return { message: error.code === undefined ? KIND_MESSAGES[error.kind] : (CODE_MESSAGES[error.code] ?? KIND_MESSAGES[error.kind]) };
}

export function localizeFieldError(error: UiError | null, field: string): string | undefined {
  return error?.details?.[field] === undefined ? undefined : 'Verifique o valor informado.';
}

export function hasUnhandledDetails(error: UiError | null, inlineFields: readonly string[]): boolean {
  return error !== null && Object.keys(error.details ?? {}).some((field) => !inlineFields.includes(field));
}
