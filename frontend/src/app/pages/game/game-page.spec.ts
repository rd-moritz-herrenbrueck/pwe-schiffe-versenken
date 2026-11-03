import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ChatMessage, GameView } from '../../core/models';
import { PlayerService } from '../../core/player.service';
import { GAME_ID, chatMessage, gameView, rules } from '../../testing/fixtures';
import { FakeEventSources, fakeEventSources } from '../../testing/fake-event-source';
import { GamePage } from './game-page';

describe('GamePage', () => {
  const gameUrl = `/api/games/${GAME_ID}`;
  let fixture: ComponentFixture<GamePage>;
  let http: HttpTestingController;
  let sources: FakeEventSources;

  beforeEach(() => {
    localStorage.clear();
    sources = fakeEventSources();
    TestBed.configureTestingModule({
      imports: [GamePage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        ...sources.providers,
      ],
    });
    TestBed.inject(PlayerService).save({ nickname: 'Anna', token: 'secret-token' });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  async function render(view: GameView): Promise<void> {
    fixture = TestBed.createComponent(GamePage);
    fixture.componentRef.setInput('id', GAME_ID);
    await fixture.whenStable();
    http.expectOne(gameUrl).flush(view);
    await fixture.whenStable();
  }

  const root = () => fixture.nativeElement as HTMLElement;
  const byId = (id: string) => root().querySelector<HTMLElement>(`[data-testid="${id}"]`);
  const cellIn = (boardId: string, x: number, y: number) =>
    byId(boardId)!.querySelector<HTMLButtonElement>(`[data-testid="cell-${x}-${y}"]`)!;

  async function click(element: HTMLElement | null): Promise<void> {
    element!.click();
    await fixture.whenStable();
  }

  describe('loading', () => {
    it('loadsGameWithIdFromRoute', async () => {
      await render(gameView());

      expect(byId('own-board')).not.toBeNull();
    });

    it('showsErrorInsteadOfBoardsWhenGameIsUnknown', async () => {
      fixture = TestBed.createComponent(GamePage);
      fixture.componentRef.setInput('id', GAME_ID);
      await fixture.whenStable();

      http
        .expectOne(gameUrl)
        .flush(
          { code: 'GAME_NOT_FOUND', message: 'Spiel nicht gefunden' },
          { status: 404, statusText: 'Not Found' },
        );
      await fixture.whenStable();

      expect(byId('error-message')?.textContent).toContain('Spiel nicht gefunden');
      expect(byId('own-board')).toBeNull();
    });

    it('stopsListeningToEventsWhenPageIsLeft', async () => {
      await render(gameView());

      fixture.destroy();

      expect(sources.open()).toEqual([]);
    });
  });

  describe('waitingForOpponent', () => {
    beforeEach(() =>
      render(
        gameView({
          phase: 'WAITING',
          opponentNickname: null,
          yourFleetReady: false,
          opponentFleetReady: false,
        }),
      ),
    );

    it('showsWaitingHint', () => {
      expect(byId('waiting-for-opponent')).not.toBeNull();
    });

    it('showsNoBoards', () => {
      expect(byId('own-board')).toBeNull();
      expect(byId('enemy-board')).toBeNull();
    });

    it('offersNoResignYet', () => {
      expect(byId('resign-button')).toBeNull();
    });

    it('switchesToPlacementWhenOpponentJoins', async () => {
      sources.latest().emit('PLAYER_JOINED', { gameId: GAME_ID, nickname: 'Ben' });
      http
        .expectOne(gameUrl)
        .flush(gameView({ phase: 'PLACEMENT', yourFleetReady: false, opponentFleetReady: false }));
      await fixture.whenStable();

      expect(byId('fleet-placement')).not.toBeNull();
      expect(byId('waiting-for-opponent')).toBeNull();
    });
  });

  describe('fleetPlacement', () => {
    const placementView = (overrides: Partial<GameView> = {}) =>
      gameView({
        phase: 'PLACEMENT',
        yourFleetReady: false,
        opponentFleetReady: false,
        yourTurn: false,
        ...overrides,
      });

    it('showsPlacementBoardSizedByRuleSet', async () => {
      await render(
        placementView({ rules: rules({ boardWidth: 6, boardHeight: 4, fleet: [2, 3] }) }),
      );

      expect(byId('fleet-placement')).not.toBeNull();
      expect(byId('own-board')!.querySelectorAll('[data-testid^="cell-"]')).toHaveLength(24);
      expect(byId('enemy-board')).toBeNull();
    });

    it('sendsConfirmedFleetToServer', async () => {
      await render(
        placementView({ rules: rules({ boardWidth: 6, boardHeight: 4, fleet: [2, 3] }) }),
      );

      await click(cellIn('own-board', 0, 0));
      await click(cellIn('own-board', 0, 2));
      await click(byId('confirm-fleet-button'));

      const request = http.expectOne(`${gameUrl}/fleet`);
      expect(request.request.method).toBe('PUT');
      expect(request.request.body).toEqual({
        ships: [
          { x: 0, y: 0, length: 2, orientation: 'HORIZONTAL' },
          { x: 0, y: 2, length: 3, orientation: 'HORIZONTAL' },
        ],
      });
      request.flush(placementView({ yourFleetReady: true }));
    });

    it('showsServerRejectionOfFleetAndKeepsPlacementOpen', async () => {
      await render(
        placementView({ rules: rules({ boardWidth: 6, boardHeight: 4, fleet: [2, 3] }) }),
      );
      await click(cellIn('own-board', 0, 0));
      await click(cellIn('own-board', 0, 1));
      await click(byId('confirm-fleet-button'));

      http
        .expectOne(`${gameUrl}/fleet`)
        .flush(
          { code: 'SHIPS_TOUCHING', message: 'Schiffe berühren sich' },
          { status: 400, statusText: 'Bad Request' },
        );
      await fixture.whenStable();

      expect(byId('error-message')?.textContent).toContain('Schiffe berühren sich');
      expect(byId('fleet-placement')).not.toBeNull();
    });

    it('showsWaitingForOpponentFleetAfterConfirming', async () => {
      await render(placementView({ yourFleetReady: true }));

      expect(byId('waiting-for-fleet')).not.toBeNull();
      expect(byId('fleet-placement')).toBeNull();
    });

    it('showsOwnShipsWhileWaitingForOpponentFleet', async () => {
      await render(
        placementView({
          yourFleetReady: true,
          ownBoard: {
            ships: [{ x: 1, y: 1, length: 2, orientation: 'HORIZONTAL', sunk: false }],
            shots: [],
          },
        }),
      );

      expect(cellIn('own-board', 1, 1).getAttribute('data-state')).toBe('ship');
    });

    it('startsGameWhenSecondFleetIsReady', async () => {
      await render(placementView({ yourFleetReady: true }));

      sources.latest().emit('GAME_STARTED', { gameId: GAME_ID, currentTurn: 'A' });
      http.expectOne(gameUrl).flush(gameView({ phase: 'RUNNING', opponentFleetReady: true }));
      await fixture.whenStable();

      expect(byId('enemy-board')).not.toBeNull();
      expect(byId('waiting-for-fleet')).toBeNull();
    });
  });

  describe('runningGame', () => {
    const small = rules({ boardWidth: 8, boardHeight: 6, fleet: [2, 3] });

    it('showsOwnAndEnemyBoardSizedByRuleSet', async () => {
      await render(gameView({ rules: small }));

      expect(byId('own-board')!.querySelectorAll('[data-testid^="cell-"]')).toHaveLength(48);
      expect(byId('enemy-board')!.querySelectorAll('[data-testid^="cell-"]')).toHaveLength(48);
    });

    it('showsOpponentNickname', async () => {
      await render(gameView({ opponentNickname: 'Ben' }));

      expect(byId('opponent-name')?.textContent).toContain('Ben');
    });

    it('showsOwnShipsAndReceivedShotsOnOwnBoard', async () => {
      await render(
        gameView({
          ownBoard: {
            ships: [{ x: 0, y: 0, length: 2, orientation: 'HORIZONTAL', sunk: false }],
            shots: [
              { x: 0, y: 0, hit: true },
              { x: 5, y: 5, hit: false },
            ],
          },
        }),
      );

      expect(cellIn('own-board', 0, 0).getAttribute('data-state')).toBe('hit');
      expect(cellIn('own-board', 1, 0).getAttribute('data-state')).toBe('ship');
      expect(cellIn('own-board', 5, 5).getAttribute('data-state')).toBe('miss');
    });

    it('showsOnlyOwnShotsAndSunkShipsOnEnemyBoard', async () => {
      await render(
        gameView({
          enemyBoard: {
            ships: [{ x: 2, y: 2, length: 2, orientation: 'VERTICAL', sunk: true }],
            shots: [
              { x: 2, y: 2, hit: true },
              { x: 2, y: 3, hit: true },
              { x: 7, y: 7, hit: false },
            ],
          },
        }),
      );

      expect(
        [cellIn('enemy-board', 2, 2), cellIn('enemy-board', 2, 3)].map((c) =>
          c.getAttribute('data-state'),
        ),
      ).toEqual(['sunk', 'sunk']);
      expect(cellIn('enemy-board', 7, 7).getAttribute('data-state')).toBe('miss');
      expect(cellIn('enemy-board', 0, 0).getAttribute('data-state')).toBe('empty');
    });

    it.each([true, false])('showsWhoseTurnItIs%s', async (yourTurn) => {
      await render(gameView({ yourTurn }));

      expect(byId('turn-indicator')?.getAttribute('data-your-turn')).toBe(String(yourTurn));
    });

    it('firesAtClickedEnemyCellOnYourTurn', async () => {
      await render(gameView({ yourTurn: true }));

      await click(cellIn('enemy-board', 3, 4));

      const request = http.expectOne(`${gameUrl}/shots`);
      expect(request.request.body).toEqual({ x: 3, y: 4 });
      request.flush({
        result: 'MISS',
        sunkShip: null,
        game: gameView({
          yourTurn: false,
          enemyBoard: { ships: [], shots: [{ x: 3, y: 4, hit: false }] },
        }),
      });
      await fixture.whenStable();
      expect(cellIn('enemy-board', 3, 4).getAttribute('data-state')).toBe('miss');
      expect(byId('turn-indicator')?.getAttribute('data-your-turn')).toBe('false');
    });

    it('showsResultOfLastShot', async () => {
      await render(gameView({ yourTurn: true }));
      await click(cellIn('enemy-board', 3, 4));

      http.expectOne(`${gameUrl}/shots`).flush({
        result: 'SUNK',
        sunkShip: { x: 3, y: 4, length: 2, orientation: 'HORIZONTAL' },
        game: gameView(),
      });
      await fixture.whenStable();

      expect(byId('shot-feedback')?.getAttribute('data-result')).toBe('SUNK');
    });

    it('doesNotFireWhenItIsNotYourTurn', async () => {
      await render(gameView({ yourTurn: false }));

      await click(cellIn('enemy-board', 3, 4));

      http.expectNone(`${gameUrl}/shots`);
    });

    it('doesNotFireAtOwnBoard', async () => {
      await render(gameView({ yourTurn: true }));

      await click(cellIn('own-board', 3, 4));

      http.expectNone(`${gameUrl}/shots`);
    });

    it('doesNotFireAgainAtAlreadyShotCell', async () => {
      await render(
        gameView({
          yourTurn: true,
          enemyBoard: { ships: [], shots: [{ x: 3, y: 4, hit: false }] },
        }),
      );

      await click(cellIn('enemy-board', 3, 4));

      http.expectNone(`${gameUrl}/shots`);
    });

    it('showsServerRejectionOfShot', async () => {
      await render(gameView({ yourTurn: true }));
      await click(cellIn('enemy-board', 3, 4));

      http
        .expectOne(`${gameUrl}/shots`)
        .flush(
          { code: 'NOT_YOUR_TURN', message: 'Nicht dein Zug' },
          { status: 409, statusText: 'Conflict' },
        );
      await fixture.whenStable();

      expect(byId('error-message')?.textContent).toContain('Nicht dein Zug');
    });

    it('showsOpponentsShotAfterShotEvent', async () => {
      await render(gameView({ yourTurn: false }));

      sources.latest().emit('SHOT', { gameId: GAME_ID, side: 'B', x: 6, y: 1, result: 'MISS' });
      http.expectOne(gameUrl).flush(
        gameView({
          yourTurn: true,
          ownBoard: { ships: [], shots: [{ x: 6, y: 1, hit: false }] },
        }),
      );
      await fixture.whenStable();

      expect(cellIn('own-board', 6, 1).getAttribute('data-state')).toBe('miss');
      expect(byId('turn-indicator')?.getAttribute('data-your-turn')).toBe('true');
    });

    it('reloadsStateWhenStreamReconnects', async () => {
      await render(gameView({ yourTurn: false }));

      sources.latest().emit('CONNECTED', { gameId: GAME_ID });
      http.expectOne(gameUrl).flush(gameView({ yourTurn: true }));
      await fixture.whenStable();

      expect(byId('turn-indicator')?.getAttribute('data-your-turn')).toBe('true');
    });
  });

  describe('finishedGame', () => {
    it.each([
      ['WON', 'gewonnen'],
      ['LOST', 'verloren'],
    ] as const)('announcesResult%s', async (result, text) => {
      await render(gameView({ phase: 'FINISHED', result, yourTurn: false }));

      expect(byId('result-message')?.getAttribute('data-result')).toBe(result);
      expect(byId('result-message')?.textContent?.toLowerCase()).toContain(text);
    });

    it('keepsBoardsVisibleButNotShootable', async () => {
      await render(gameView({ phase: 'FINISHED', result: 'WON', yourTurn: true }));

      await click(cellIn('enemy-board', 3, 4));

      expect(byId('enemy-board')).not.toBeNull();
      http.expectNone(`${gameUrl}/shots`);
    });

    it('offersNoResign', async () => {
      await render(gameView({ phase: 'FINISHED', result: 'WON' }));

      expect(byId('resign-button')).toBeNull();
    });

    it('showsFinishedStateAfterGameFinishedEvent', async () => {
      await render(gameView({ yourTurn: false }));

      sources.latest().emit('GAME_FINISHED', { gameId: GAME_ID, phase: 'FINISHED', winner: 'B' });
      http
        .expectOne(gameUrl)
        .flush(gameView({ phase: 'FINISHED', result: 'LOST', yourTurn: false }));
      await fixture.whenStable();

      expect(byId('result-message')?.getAttribute('data-result')).toBe('LOST');
    });
  });

  describe('abandonedGame', () => {
    it('showsAbandonedHintAndNoResign', async () => {
      await render(gameView({ phase: 'ABANDONED', yourTurn: false }));

      expect(byId('abandoned-message')).not.toBeNull();
      expect(byId('resign-button')).toBeNull();
    });
  });

  describe('resign', () => {
    it.each(['PLACEMENT', 'RUNNING'] as const)('offersResignDuring%s', async (phase) => {
      await render(gameView({ phase, yourFleetReady: false }));

      expect(byId('resign-button')).not.toBeNull();
    });

    it('asksForConfirmationBeforeResigning', async () => {
      await render(gameView());

      await click(byId('resign-button'));

      expect(byId('resign-confirm-button')).not.toBeNull();
      http.expectNone(`${gameUrl}/resign`);
    });

    it('resignsAfterConfirmationAndShowsLoss', async () => {
      await render(gameView());
      await click(byId('resign-button'));

      await click(byId('resign-confirm-button'));

      const request = http.expectOne(`${gameUrl}/resign`);
      expect(request.request.method).toBe('POST');
      request.flush(gameView({ phase: 'FINISHED', result: 'LOST', yourTurn: false }));
      await fixture.whenStable();
      expect(byId('result-message')?.getAttribute('data-result')).toBe('LOST');
    });

    it('keepsPlayingWhenResignIsCancelled', async () => {
      await render(gameView());
      await click(byId('resign-button'));

      await click(byId('resign-cancel-button'));

      expect(byId('resign-confirm-button')).toBeNull();
      http.expectNone(`${gameUrl}/resign`);
    });
  });

  describe('inviteCode', () => {
    const waiting = (inviteCode: string | null) =>
      gameView({
        phase: 'WAITING',
        opponentNickname: null,
        yourFleetReady: false,
        opponentFleetReady: false,
        inviteCode,
      });

    it('showsTheInviteCodeOfAPrivateLobbyWhileWaiting', async () => {
      await render(waiting('ABCD2345'));

      expect(byId('invite-code')?.textContent).toContain('ABCD2345');
    });

    it('showsNoInviteCodeForPublicLobbies', async () => {
      await render(waiting(null));

      expect(byId('invite-code')).toBeNull();
    });

    it('hidesTheInviteCodeOnceTheOpponentHasJoined', async () => {
      await render(waiting('ABCD2345'));

      sources.latest().emit('PLAYER_JOINED', { gameId: GAME_ID, nickname: 'Ben' });
      http
        .expectOne(gameUrl)
        .flush(gameView({ phase: 'PLACEMENT', yourFleetReady: false, inviteCode: 'ABCD2345' }));
      await fixture.whenStable();

      expect(byId('invite-code')).toBeNull();
    });
  });

  describe('chat', () => {
    const chatUrl = `${gameUrl}/chat`;
    const fromOpponent = chatMessage({ id: 'm1', side: 'B', nickname: 'Ben', text: 'Viel Glück!' });
    const mine = chatMessage({ id: 'm2', side: 'A', nickname: 'Anna', text: 'Dir auch' });

    async function openChat(history: ChatMessage[] = []): Promise<void> {
      await click(byId('chat-toggle'));
      http.expectOne(chatUrl).flush(history);
      await fixture.whenStable();
    }

    it.each(['PLACEMENT', 'RUNNING', 'FINISHED'] as const)(
      'offersTheChatInPhase%s',
      async (phase) => {
        await render(
          gameView({ phase, yourFleetReady: false, result: phase === 'FINISHED' ? 'WON' : null }),
        );

        expect(byId('chat-toggle')).not.toBeNull();
      },
    );

    it.each(['WAITING', 'ABANDONED'] as const)('offersNoChatInPhase%s', async (phase) => {
      await render(gameView({ phase, opponentNickname: null }));

      expect(byId('chat-toggle')).toBeNull();
    });

    it('keepsTheChatClosedAndUnloadedByDefault', async () => {
      await render(gameView());

      expect(byId('chat')).toBeNull();
      http.expectNone(chatUrl);
    });

    it('loadsAndShowsTheHistoryWhenOpened', async () => {
      await render(gameView());

      await openChat([fromOpponent, mine]);

      expect(byId('chat')).not.toBeNull();
      expect(byId('chat-message-m1')?.textContent).toContain('Viel Glück!');
      expect(byId('chat-message-m2')?.getAttribute('data-own')).toBe('true');
    });

    it('hidesTheChatAgainOnToggle', async () => {
      await render(gameView());
      await openChat();

      await click(byId('chat-toggle'));

      expect(byId('chat')).toBeNull();
    });

    it('sendsTheTypedTextAndShowsTheStoredMessage', async () => {
      await render(gameView());
      await openChat();
      const input = byId('chat-input') as HTMLInputElement;
      input.value = 'Dir auch';
      input.dispatchEvent(new Event('input'));
      await fixture.whenStable();

      await click(byId('chat-send-button'));

      const request = http.expectOne(chatUrl);
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual({ text: 'Dir auch' });
      request.flush(mine);
      await fixture.whenStable();
      expect(byId('chat-message-m2')).not.toBeNull();
    });

    it('showsMessagesOfTheOpponentLiveWhileOpen', async () => {
      await render(gameView());
      await openChat();

      sources.latest().emit('CHAT', { gameId: GAME_ID, message: fromOpponent });
      await fixture.whenStable();

      http.expectNone(gameUrl);
      expect(byId('chat-message-m1')).not.toBeNull();
    });

    it('showsServerRejectionOfAMessage', async () => {
      await render(gameView());
      await openChat();
      const input = byId('chat-input') as HTMLInputElement;
      input.value = 'Hallo';
      input.dispatchEvent(new Event('input'));
      await fixture.whenStable();

      await click(byId('chat-send-button'));

      http
        .expectOne(chatUrl)
        .flush(
          { code: 'WRONG_PHASE', message: 'Chat nicht möglich' },
          { status: 409, statusText: 'Conflict' },
        );
      await fixture.whenStable();
      expect(byId('error-message')?.textContent).toContain('Chat nicht möglich');
    });

    describe('unreadIndicator', () => {
      beforeEach(() => render(gameView()));

      it('showsNoIndicatorInitially', () => {
        expect(byId('chat-unread')).toBeNull();
      });

      it('countsMessagesOfTheOpponentWhileTheChatIsClosed', async () => {
        sources.latest().emit('CHAT', { gameId: GAME_ID, message: fromOpponent });
        sources.latest().emit('CHAT', { gameId: GAME_ID, message: { ...fromOpponent, id: 'm3' } });
        await fixture.whenStable();

        expect(byId('chat-unread')?.textContent?.trim()).toBe('2');
      });

      it('ignoresOwnMessages', async () => {
        sources.latest().emit('CHAT', { gameId: GAME_ID, message: mine });
        await fixture.whenStable();

        expect(byId('chat-unread')).toBeNull();
      });

      it('disappearsWhenTheChatIsOpened', async () => {
        sources.latest().emit('CHAT', { gameId: GAME_ID, message: fromOpponent });
        await fixture.whenStable();

        await openChat([fromOpponent]);

        expect(byId('chat-unread')).toBeNull();
      });
    });
  });
});
