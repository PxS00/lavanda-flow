import { Component, computed, input } from '@angular/core';

import { localizeUiError } from '../../../core/http/localize-ui-error';
import { UiError } from '../../../core/http/ui-error';

@Component({
  selector: 'app-error-state',
  imports: [],
  templateUrl: './error-state.html',
  styleUrl: './error-state.scss',
})
export class ErrorState {
  readonly error = input.required<UiError>();

  protected readonly presentation = computed(() => localizeUiError(this.error()));
}
