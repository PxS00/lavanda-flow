import { describe, expect, it } from 'vitest';

import { createPtBrPaginatorIntl } from './pt-br-paginator-intl';

describe('createPtBrPaginatorIntl', () => {
  it('provides Portuguese labels and ranges', () => {
    const paginator = createPtBrPaginatorIntl();

    expect(paginator.itemsPerPageLabel).toBe('Itens por página:');
    expect(paginator.nextPageLabel).toBe('Próxima página');
    expect(paginator.getRangeLabel(0, 20, 0)).toBe('0 de 0');
    expect(paginator.getRangeLabel(0, 20, 53)).toBe('1 – 20 de 53');
    expect(paginator.getRangeLabel(2, 20, 53)).toBe('41 – 53 de 53');
  });
});
