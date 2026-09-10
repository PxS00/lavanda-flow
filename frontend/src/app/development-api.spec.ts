import proxyConfiguration from '../../proxy.conf.json';

import { environment } from '../environments/environment.development';

describe('development API configuration', () => {
  it('keeps browser API requests same-origin through the local backend proxy', () => {
    expect(environment.apiBaseUrl).toBe('/api/v1');
    expect(proxyConfiguration['/api']).toEqual({
      target: 'http://localhost:8080',
      secure: false,
    });
  });
});
