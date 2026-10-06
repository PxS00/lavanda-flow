import { draftTotalPreview, validDraftDecimal } from './draft-preview';

describe('provisional draft preview', () => {
  it('rounds each exact line HALF_UP before summing without binary floating point', () => {
    expect(
      draftTotalPreview([
        { quantity: '0,5', unitPrice: '0,01' },
        { quantity: '0.5', unitPrice: '0.01' },
      ]),
    ).toBe('0.02');
    expect(draftTotalPreview([{ quantity: '1', unitPrice: '1.2350' }])).toBe('1.24');
    expect(draftTotalPreview([{ quantity: '1234567890123.123456', unitPrice: '0.0001' }])).toBe(
      '123456789.01',
    );
    expect(draftTotalPreview([{ quantity: '0.000001', unitPrice: '0' }])).toBe('0.00');
  });
  it.each(['0', '-1', '0.0000001', '10000000000000', '1e3', '1.2.3'])(
    'does not preview invalid quantity %s',
    (quantity) => {
      expect(validDraftDecimal(quantity, 13, 6, true)).toBe(false);
      expect(draftTotalPreview([{ quantity, unitPrice: '1' }])).toBeNull();
    },
  );
  it.each(['-0.01', '0.00001', '1000000000000000'])(
    'does not preview invalid price %s',
    (unitPrice) => {
      expect(draftTotalPreview([{ quantity: '1', unitPrice }])).toBeNull();
    },
  );
});
