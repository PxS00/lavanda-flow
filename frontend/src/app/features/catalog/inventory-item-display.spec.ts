import {
  PRODUCT_GENDER_OPTIONS,
  INVENTORY_ITEM_CATEGORY_OPTIONS,
  productGenderLabel,
} from './inventory-item-display';

describe('catalog display metadata', () => {
  it('should provide all canonical gender options with Portuguese labels', () => {
    expect(PRODUCT_GENDER_OPTIONS).toEqual([
      { value: 'M', label: 'Masculino' },
      { value: 'F', label: 'Feminino' },
      { value: 'C', label: 'Compartilhável' },
      { value: 'M/C', label: 'Compartilhável com tendência masculina' },
      { value: 'F/C', label: 'Compartilhável com tendência feminina' },
    ]);
    expect(productGenderLabel(null)).toBe('Não informado');
    expect(INVENTORY_ITEM_CATEGORY_OPTIONS).toContainEqual({
      value: 'FINISHED_PRODUCT',
      label: 'Produto finalizado',
    });
  });
});
