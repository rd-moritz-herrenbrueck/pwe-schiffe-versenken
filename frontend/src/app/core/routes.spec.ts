import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../app.routes';
import { GAME_ID } from '../testing/fixtures';
import { fakeEventSources } from '../testing/fake-event-source';
import { PlayerService } from './player.service';

describe('routes', () => {
  const protectedPages = ['/lobbies', '/games', `/games/${GAME_ID}`];

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes, withComponentInputBinding()),
        provideHttpClient(),
        provideHttpClientTesting(),
        ...fakeEventSources().providers,
      ],
    });
  });

  afterEach(() => localStorage.clear());

  async function visit(url: string): Promise<RouterTestingHarness> {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    return harness;
  }

  describe('withoutToken', () => {
    it.each(protectedPages)('redirectsProtectedPage%sToStartPage', async (url) => {
      await visit(url);

      expect(TestBed.inject(Router).url).toBe('/');
    });

    it('showsStartPageWithNicknameInput', async () => {
      const harness = await visit('/');

      expect(TestBed.inject(Router).url).toBe('/');
      expect(
        harness.routeNativeElement?.querySelector('[data-testid="nickname-input"]'),
      ).not.toBeNull();
    });
  });

  describe('withToken', () => {
    beforeEach(() =>
      TestBed.inject(PlayerService).save({ nickname: 'Anna', token: 'secret-token' }),
    );

    it.each(protectedPages)('allowsProtectedPage%s', async (url) => {
      await visit(url);

      expect(TestBed.inject(Router).url).toBe(url);
    });
  });
});
