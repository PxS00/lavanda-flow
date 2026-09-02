import { describe, expect, it } from 'vitest';

import { localizeUiError } from './localize-ui-error';

describe('localizeUiError', () => {
  it('localizes a known backend code without exposing its message', () => {
    expect(localizeUiError({ kind: 'not-found', code: 'INVENTORY_ITEM_NOT_FOUND', message: 'Inventory item not found.' })).toEqual({
      message: 'Item de estoque não encontrado.',
    });
  });

  it('uses a safe Portuguese fallback for an unknown code', () => {
    expect(localizeUiError({ kind: 'server', code: 'UNRECOGNIZED', message: 'Sensitive details' })).toEqual({
      message: 'Ocorreu um erro no servidor. Tente novamente.',
    });
  });
});
