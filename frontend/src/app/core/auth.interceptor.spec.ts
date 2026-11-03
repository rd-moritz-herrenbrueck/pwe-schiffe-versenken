import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { authInterceptor } from './auth.interceptor';
import { PlayerService } from './player.service';

describe('authInterceptor', () => {
  let client: HttpClient;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('sendsTokenAsBearerToApiRequests', () => {
    TestBed.inject(PlayerService).save({ nickname: 'Anna', token: 'secret-token' });

    client.get('/api/games').subscribe();

    const request = http.expectOne('/api/games');
    expect(request.request.headers.get('Authorization')).toBe('Bearer secret-token');
    request.flush([]);
  });

  it('sendsNoAuthorizationHeaderWithoutToken', () => {
    client.post('/api/players', { nickname: 'Anna' }).subscribe();

    const request = http.expectOne('/api/players');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
  });

  it('doesNotLeakTokenToOtherHosts', () => {
    TestBed.inject(PlayerService).save({ nickname: 'Anna', token: 'secret-token' });

    client.get('https://example.org/data').subscribe();

    const request = http.expectOne('https://example.org/data');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
  });
});
