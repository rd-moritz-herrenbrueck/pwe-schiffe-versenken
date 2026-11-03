import { HttpErrorResponse } from '@angular/common/http';
import { toApiError } from './errors';

describe('toApiError', () => {
  const response = (status: number, body: unknown = null) =>
    new HttpErrorResponse({ status, error: body });

  it('takesCodeAndMessageFromTheServer', () => {
    const error = response(409, { code: 'WRONG_PHASE', message: 'Nicht möglich' });

    expect(toApiError(error)).toEqual({ code: 'WRONG_PHASE', message: 'Nicht möglich' });
  });

  it('reportsUnreachableServerWithoutResponse', () => {
    const error = toApiError(response(0));

    expect(error.code).toBe('NETWORK_ERROR');
    expect(error.message).toContain('nicht erreichbar');
  });

  it('reportsUnknownErrorsAsNetworkErrorToo', () => {
    expect(toApiError(new Error('boom')).code).toBe('NETWORK_ERROR');
  });

  it('explainsExpiredLoginOnUnauthorized', () => {
    const error = toApiError(response(401));

    expect(error.code).toBe('UNAUTHORIZED');
    expect(error.message).toContain('neu an');
  });

  it('explainsTooManyRequestsWithoutServerMessage', () => {
    const error = toApiError(response(429));

    expect(error.code).toBe('RATE_LIMITED');
    expect(error.message).toContain('zu schnell');
  });

  it.each([500, 502, 503, 504])('reportsServerErrorFor%i', (status) => {
    const error = toApiError(response(status, '<html>Bad Gateway</html>'));

    expect(error.code).toBe('SERVER_ERROR');
    expect(error.message).toContain('Server');
    expect(error.message).not.toContain('html');
  });

  it('prefersTheServerMessageOverTheStatusText', () => {
    const error = response(429, { code: 'RATE_LIMITED', message: 'Bitte kurz warten' });

    expect(toApiError(error).message).toBe('Bitte kurz warten');
  });
});
