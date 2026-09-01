import { Component, computed, input } from '@angular/core';

import { UiError } from '../../../core/http/ui-error';

@Component({
  selector: 'app-error-state',
  imports: [],
  templateUrl: './error-state.html',
  styleUrl: './error-state.scss',
})
export class ErrorState {
  readonly error = input.required<UiError>();

  protected readonly fieldErrors = computed(() => Object.entries(this.error().fieldErrors ?? {}));
}
