export function formatDecimalString(value: string): string {
  const match = /^(-?)(\d+)(?:\.(\d+))?$/.exec(value);

  if (match === null) {
    return value;
  }

  const [, sign, rawInteger, rawFraction] = match;

  const normalizedInteger = rawInteger.replace(/^0+(?=\d)/, '');
  const groupedInteger = normalizedInteger.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  const fraction = rawFraction?.replace(/0+$/, '') ?? '';

  return fraction.length > 0
    ? `${sign}${groupedInteger},${fraction}`
    : `${sign}${groupedInteger}`;
}
