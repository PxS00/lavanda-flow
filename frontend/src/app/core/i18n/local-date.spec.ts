import { describe, expect, it } from 'vitest';

import { formatLocalDate } from './local-date';

describe('formatLocalDate', () => {
  it('formats an ISO civil date without creating a Date', () => {
    expect(formatLocalDate('2026-09-01')).toBe('01/09/2026');
  });
});
