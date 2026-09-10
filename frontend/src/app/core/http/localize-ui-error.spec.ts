import { describe, expect, it } from 'vitest';

import { localizeUiError } from './localize-ui-error';

describe('localizeUiError', () => {
  it('localizes a known backend code without exposing its message', () => {
    expect(
      localizeUiError({
        kind: 'not-found',
        code: 'INVENTORY_ITEM_NOT_FOUND',
        message: 'Inventory item not found.',
      }),
    ).toEqual({
      message: 'Item de estoque não encontrado.',
    });
  });

  it.each([
    [
      'INVALID_PRODUCTION_ALLOCATION',
      'As quantidades dos lotes não correspondem aos requisitos da fórmula.',
    ],
    [
      'PRODUCTION_SOURCE_BATCH_NOT_FOUND',
      'Um lote selecionado para a produção não foi encontrado.',
    ],
    ['INSUFFICIENT_STOCK', 'Um dos lotes selecionados não possui saldo suficiente.'],
    ['EXPIRED_BATCH', 'Um dos lotes selecionados está vencido e não pode ser consumido.'],
    [
      'BATCH_NOT_EXPIRED',
      'Este lote ainda não está vencido e não pode ser descartado por vencimento.',
    ],
    ['INVALID_STOCK_MOVEMENT_REASON', 'Informe um motivo válido para a movimentação.'],
    ['INVALID_STOCK_ADJUSTMENT', 'Informe um ajuste de estoque diferente de zero.'],
  ])('localizes production error code %s', (code, message) => {
    expect(localizeUiError({ kind: 'unprocessable', code, message: 'Backend message' })).toEqual({
      message,
    });
  });

  it('uses a safe Portuguese fallback for an unknown code', () => {
    expect(
      localizeUiError({ kind: 'server', code: 'UNRECOGNIZED', message: 'Sensitive details' }),
    ).toEqual({
      message: 'Ocorreu um erro no servidor. Tente novamente.',
    });
  });

  it.each([
    ['AUTHENTICATION_FAILED', 'Usuário ou senha incorretos.'],
    ['AUTHENTICATION_REQUIRED', 'Sua sessão expirou. Entre novamente.'],
    ['ACCESS_DENIED', 'Você não tem acesso a esta operação.'],
    [
      'CSRF_VALIDATION_FAILED',
      'Não foi possível confirmar a segurança da operação. Tente novamente.',
    ],
  ])('localizes security code %s', (code, message) => {
    expect(localizeUiError({ kind: 'unknown', code, message: 'Backend message' })).toEqual({
      message,
    });
  });
});
