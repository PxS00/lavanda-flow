import { DecimalPipe } from '@angular/common';
import { LOCALE_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { appConfig } from '../../app.config';
import { APPLICATION_LOCALE } from './pt-br-locale';

describe('pt-BR locale', () => {
  it('configures the application locale and formats decimals in pt-BR', () => {
    TestBed.configureTestingModule({ providers: appConfig.providers });

    expect(TestBed.inject(LOCALE_ID)).toBe(APPLICATION_LOCALE);
    expect(new DecimalPipe(APPLICATION_LOCALE).transform(1234.5, '1.0-6')).toBe('1.234,5');
  });
});
