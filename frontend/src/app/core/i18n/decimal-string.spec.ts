import { describe, expect, it } from 'vitest';

import { formatDecimalString } from './decimal-string';

describe('formatDecimalString', () => {
  it('formats backend decimal strings for pt-BR presentation without numeric conversion', () => {
    expect(formatDecimalString('55.000000')).toBe('55');
    expect(formatDecimalString('1234.500000')).toBe('1.234,5');
    expect(formatDecimalString('1234567.123456')).toBe('1.234.567,123456');
  });

  it('preserves an unsupported value unchanged', () => {
    expect(formatDecimalString('invalid')).toBe('invalid');
  });
});
