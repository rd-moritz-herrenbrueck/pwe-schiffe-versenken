import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
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
        provideRouter([{ path: 'lobbies', children: [] }]),
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

  describe('unauthorized', () => {
    const unauthorized = { status: 401, statusText: 'Unauthorized' };

    it('forgetsTheStaleTokenAndReturnsToTheStartPage', async () => {
      const player = TestBed.inject(PlayerService);
      player.save({ nickname: 'Anna', token: 'stale-token' });
      const router = TestBed.inject(Router);
      await router.navigateByUrl('/lobbies');

      client.get('/api/lobbies').subscribe({ error: () => undefined });
      http.expectOne('/api/lobbies').flush(null, unauthorized);

      expect(player.loggedIn()).toBe(false);
      expect(localStorage.getItem('schiffe.token')).toBeNull();
      await vi.waitFor(() => expect(router.url).toBe('/'));
    });

    it('stillPassesTheErrorOnToTheCaller', () => {
      TestBed.inject(PlayerService).save({ nickname: 'Anna', token: 'stale-token' });
      let status = 0;

      client.get('/api/games').subscribe({ error: (error) => (status = error.status) });
      http.expectOne('/api/games').flush(null, unauthorized);

      expect(status).toBe(401);
    });

    it('keepsTheTokenOnOtherErrors', () => {
      const player = TestBed.inject(PlayerService);
      player.save({ nickname: 'Anna', token: 'secret-token' });

      client.get('/api/games').subscribe({ error: () => undefined });
      http.expectOne('/api/games').flush(null, { status: 500, statusText: 'Error' });

      expect(player.loggedIn()).toBe(true);
    });

    it('ignoresUnauthorizedFromOtherHosts', () => {
      const player = TestBed.inject(PlayerService);
      player.save({ nickname: 'Anna', token: 'secret-token' });

      client.get('https://example.org/data').subscribe({ error: () => undefined });
      http.expectOne('https://example.org/data').flush(null, unauthorized);

      expect(player.loggedIn()).toBe(true);
    });
  });
});
