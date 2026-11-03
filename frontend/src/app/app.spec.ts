import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { PlayerService } from './core/player.service';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  afterEach(() => localStorage.clear());

  async function render(): Promise<HTMLElement> {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('rendersGameTitle', async () => {
    const compiled = await render();
    expect(compiled.querySelector('[data-testid="app-title"]')?.textContent).toContain(
      'Schiffe versenken',
    );
  });

  it('providesRouterOutletForPages', async () => {
    const compiled = await render();
    expect(compiled.querySelector('router-outlet')).not.toBeNull();
  });

  it('offersThemeToggleOnEveryPage', async () => {
    const compiled = await render();
    expect(compiled.querySelector('[data-testid="theme-toggle"]')).not.toBeNull();
  });

  it('hidesNavigationUntilPlayerHasToken', async () => {
    const compiled = await render();

    expect(compiled.querySelector('[data-testid="nav-lobbies"]')).toBeNull();
    expect(compiled.querySelector('[data-testid="nav-my-games"]')).toBeNull();
  });

  it('linksToLobbiesAndOwnGamesOncePlayerHasToken', async () => {
    TestBed.inject(PlayerService).save({ nickname: 'Anna', token: 'secret-token' });

    const compiled = await render();

    expect(compiled.querySelector('[data-testid="nav-lobbies"]')?.getAttribute('href')).toBe(
      '/lobbies',
    );
    expect(compiled.querySelector('[data-testid="nav-my-games"]')?.getAttribute('href')).toBe(
      '/games',
    );
  });

  it('showsNicknameOfPlayer', async () => {
    TestBed.inject(PlayerService).save({ nickname: 'Anna', token: 'secret-token' });

    const compiled = await render();

    expect(compiled.querySelector('[data-testid="player-nickname"]')?.textContent).toContain(
      'Anna',
    );
  });
});
