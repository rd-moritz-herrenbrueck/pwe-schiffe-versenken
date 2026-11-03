import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { PlayerService } from '../../core/player.service';
import { HomePage } from './home-page';

@Component({ template: '' })
class LobbiesStub {}

describe('HomePage', () => {
  let fixture: ComponentFixture<HomePage>;
  let http: HttpTestingController;
  let router: Router;
  let player: PlayerService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [HomePage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'lobbies', component: LobbiesStub }]),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    player = TestBed.inject(PlayerService);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  async function render(): Promise<void> {
    fixture = TestBed.createComponent(HomePage);
    await fixture.whenStable();
  }

  const byId = (id: string) =>
    (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>(`[data-testid="${id}"]`);
  const input = () => byId('nickname-input') as HTMLInputElement;
  const startButton = () => byId('start-button') as HTMLButtonElement;

  async function type(value: string): Promise<void> {
    input().value = value;
    input().dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  async function start(): Promise<void> {
    startButton().click();
    await fixture.whenStable();
  }

  it('asksForNickname', async () => {
    await render();

    expect(input()).not.toBeNull();
    expect(startButton()).not.toBeNull();
  });

  it('limitsNicknameLengthToTwentyCharacters', async () => {
    await render();

    expect(input().maxLength).toBe(20);
  });

  it.each(['', '   '])('disablesStartForBlankNickname[%j]', async (value) => {
    await render();

    await type(value);

    expect(startButton().disabled).toBe(true);
  });

  it('enablesStartForNickname', async () => {
    await render();

    await type('Anna');

    expect(startButton().disabled).toBe(false);
  });

  it('createsPlayerWithTrimmedNicknameAndStoresToken', async () => {
    await render();
    await type('  Anna ');

    await start();

    const request = http.expectOne('/api/players');
    expect(request.request.body).toEqual({ nickname: 'Anna' });
    request.flush({ id: 'p1', nickname: 'Anna', token: 'secret-token' });
    await fixture.whenStable();
    expect(player.token()).toBe('secret-token');
    expect(player.nickname()).toBe('Anna');
  });

  it('continuesToLobbiesAfterRegistration', async () => {
    await render();
    await type('Anna');
    await start();

    http.expectOne('/api/players').flush({ id: 'p1', nickname: 'Anna', token: 'secret-token' });
    await fixture.whenStable();

    expect(router.url).toBe('/lobbies');
  });

  it('showsServerErrorAndStaysWhenNicknameIsRejected', async () => {
    await render();
    await type('Anna');
    await start();

    http
      .expectOne('/api/players')
      .flush(
        { code: 'INVALID_NICKNAME', message: 'Nickname ungültig' },
        { status: 400, statusText: 'Bad Request' },
      );
    await fixture.whenStable();

    expect(byId('error-message')?.textContent).toContain('Nickname ungültig');
    expect(player.loggedIn()).toBe(false);
    expect(router.url).toBe('/');
  });

  it('sendsNoSecondRequestWhileRegistering', async () => {
    await render();
    await type('Anna');

    await start();
    await start();

    http.expectOne('/api/players');
  });

  it('skipsStartPageWhenPlayerAlreadyHasToken', async () => {
    player.save({ nickname: 'Anna', token: 'secret-token' });

    await render();
    await fixture.whenStable();

    expect(router.url).toBe('/lobbies');
  });
});
