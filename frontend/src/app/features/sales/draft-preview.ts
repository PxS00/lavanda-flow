/** Input normalization only; the backend remains the authority for precision, eligibility and totals. */
export function normalizeDraftDecimal(value: string): string {
  return value.trim().replace(',', '.');
}

export function validDraftDecimal(
  value: string,
  integer: number,
  fraction: number,
  positive: boolean,
): boolean {
  const normalized = normalizeDraftDecimal(value);
  const match = /^(\d+)(?:\.(\d+))?$/.exec(normalized);
  return (
    match !== null &&
    match[1].replace(/^0+(?=\d)/, '').length <= integer &&
    (match[2]?.length ?? 0) <= fraction &&
    (!positive || /[1-9]/.test(normalized))
  );
}

/** Provisional line-rounded preview with integer arithmetic; never used as an authoritative request value. */
export function draftTotalPreview(
  lines: readonly { quantity: string; unitPrice: string }[],
): string | null {
  let cents = 0n;
  for (const line of lines) {
    if (
      !validDraftDecimal(line.quantity, 13, 6, true) ||
      !validDraftDecimal(line.unitPrice, 15, 4, false)
    )
      return null;
    const quantity = scaled(line.quantity, 6);
    const price = scaled(line.unitPrice, 4);
    cents += (quantity * price + 50_000_000n) / 100_000_000n;
  }
  return `${cents / 100n}.${(cents % 100n).toString().padStart(2, '0')}`;
}
function scaled(value: string, scale: number): bigint {
  const [integer, fraction = ''] = normalizeDraftDecimal(value).split('.');
  return BigInt(integer + fraction.padEnd(scale, '0'));
}
