import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { PlayerService } from '../../core/player.service';
import { gameSummary } from '../../testing/fixtures';
import { MyGamesPage } from './my-games-page';

describe('MyGamesPage', () => {
  let fixture: ComponentFixture<MyGamesPage>;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [MyGamesPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    TestBed.inject(PlayerService).save({ nickname: 'Anna', token: 'secret-token' });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  async function render(games: ReturnType<typeof gameSummary>[]): Promise<void> {
    fixture = TestBed.createComponent(MyGamesPage);
    await fixture.whenStable();
    http.expectOne('/api/games').flush(games);
    await fixture.whenStable();
  }

  const root = () => fixture.nativeElement as HTMLElement;
  const entry = (id: string) => root().querySelector<HTMLElement>(`[data-testid="game-${id}"]`);

  it('listsOwnGamesWithOpponent', async () => {
    await render([
      gameSummary({ id: 'g1', opponentNickname: 'Ben' }),
      gameSummary({ id: 'g2', opponentNickname: 'Clara' }),
    ]);

    expect(entry('g1')?.textContent).toContain('Ben');
    expect(entry('g2')?.textContent).toContain('Clara');
  });

  it('linksEachGameToItsGamePage', async () => {
    await render([gameSummary({ id: 'g1' })]);

    const link = entry('g1')!.matches('a') ? entry('g1')! : entry('g1')!.querySelector('a')!;
    expect(link.getAttribute('href')).toBe('/games/g1');
  });

  it('showsWaitingGameWithoutOpponent', async () => {
    await render([gameSummary({ id: 'g1', phase: 'WAITING', opponentNickname: null })]);

    expect(entry('g1')?.getAttribute('data-phase')).toBe('WAITING');
    expect(entry('g1')?.textContent).not.toContain('null');
  });

  it.each([true, false])('marksWhetherItIsYourTurn%s', async (yourTurn) => {
    await render([gameSummary({ id: 'g1', yourTurn })]);

    expect(entry('g1')?.getAttribute('data-your-turn')).toBe(String(yourTurn));
  });

  it.each(['WON', 'LOST'] as const)('showsResult%sOfFinishedGame', async (result) => {
    await render([gameSummary({ id: 'g1', phase: 'FINISHED', result })]);

    expect(entry('g1')?.getAttribute('data-result')).toBe(result);
  });

  it('showsHintWhenThereAreNoGames', async () => {
    await render([]);

    expect(root().querySelector('[data-testid="no-games"]')).not.toBeNull();
  });

  it('hidesHintWhenGamesExist', async () => {
    await render([gameSummary({ id: 'g1' })]);

    expect(root().querySelector('[data-testid="no-games"]')).toBeNull();
  });

  it('showsErrorWhenGamesCannotBeLoaded', async () => {
    fixture = TestBed.createComponent(MyGamesPage);
    await fixture.whenStable();

    http
      .expectOne('/api/games')
      .flush(
        { code: 'ERROR', message: 'Serverfehler' },
        { status: 500, statusText: 'Server Error' },
      );
    await fixture.whenStable();

    expect(root().querySelector('[data-testid="error-message"]')?.textContent).toContain(
      'Serverfehler',
    );
  });

  describe('deleteWaitingLobbies', () => {
    const button = (id: string) =>
      root().querySelector<HTMLButtonElement>(`[data-testid="delete-game-${id}"]`);

    it('offersDeletingOnlyForGamesWaitingForAnOpponent', async () => {
      await render([
        gameSummary({ id: 'g1', phase: 'WAITING', opponentNickname: null }),
        gameSummary({ id: 'g2', phase: 'RUNNING' }),
        gameSummary({ id: 'g3', phase: 'FINISHED', result: 'WON' }),
      ]);

      expect(button('g1')).not.toBeNull();
      expect(button('g2')).toBeNull();
      expect(button('g3')).toBeNull();
    });

    it('deletesTheLobbyAndRemovesItFromTheList', async () => {
      await render([
        gameSummary({ id: 'g1', phase: 'WAITING', opponentNickname: null }),
        gameSummary({ id: 'g2', opponentNickname: 'Ben' }),
      ]);

      button('g1')!.click();
      const request = http.expectOne('/api/lobbies/g1');
      expect(request.request.method).toBe('DELETE');
      request.flush(null, { status: 204, statusText: 'No Content' });
      await fixture.whenStable();

      expect(entry('g1')).toBeNull();
      expect(entry('g2')).not.toBeNull();
    });

    it('keepsTheLobbyAndShowsTheErrorWhenDeletingFails', async () => {
      await render([gameSummary({ id: 'g1', phase: 'WAITING', opponentNickname: null })]);

      button('g1')!.click();
      http
        .expectOne('/api/lobbies/g1')
        .flush(
          { code: 'GAME_NOT_FOUND', message: 'Spiel nicht gefunden' },
          { status: 404, statusText: 'Not Found' },
        );
      await fixture.whenStable();

      expect(entry('g1')).not.toBeNull();
      expect(root().querySelector('[data-testid="error-message"]')?.textContent).toContain(
        'nicht gefunden',
      );
    });
  });
});
