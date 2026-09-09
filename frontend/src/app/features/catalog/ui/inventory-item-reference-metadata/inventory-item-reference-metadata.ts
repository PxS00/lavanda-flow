import { Component, input } from '@angular/core';

@Component({
  selector: 'app-inventory-item-reference-metadata',
  templateUrl: './inventory-item-reference-metadata.html',
  styleUrl: './inventory-item-reference-metadata.scss',
})
export class InventoryItemReferenceMetadata {
  readonly essenceReference = input<string | null>(null);
  readonly productionTypeCode = input<string | null>(null);
}
