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
    ['INVALID_PRODUCTION_ALLOCATION', 'As quantidades dos lotes não correspondem aos requisitos da fórmula.'],
    ['PRODUCTION_SOURCE_BATCH_NOT_FOUND', 'Um lote selecionado para a produção não foi encontrado.'],
    ['INSUFFICIENT_STOCK', 'Um dos lotes selecionados não possui saldo suficiente.'],
    ['EXPIRED_BATCH', 'Um dos lotes selecionados está vencido e não pode ser consumido.'],
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
});
