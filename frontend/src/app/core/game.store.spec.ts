import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { GAME_ID, chatMessage, gameView } from '../testing/fixtures';
import { FakeEventSources, fakeEventSources } from '../testing/fake-event-source';
import { GameStore } from './game.store';
import { PlayerService } from './player.service';

describe('GameStore', () => {
  const gameUrl = `/api/games/${GAME_ID}`;
  let store: GameStore;
  let http: HttpTestingController;
  let sources: FakeEventSources;

  beforeEach(() => {
    localStorage.clear();
    sources = fakeEventSources();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), ...sources.providers],
    });
    TestBed.inject(PlayerService).save({ nickname: 'Anna', token: 'secret-token' });
    store = TestBed.inject(GameStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    store.close();
    http.verify();
    localStorage.clear();
  });

  function openAndLoad(view = gameView()): void {
    store.open(GAME_ID);
    http.expectOne(gameUrl).flush(view);
  }

  describe('deleteLobby', () => {
    it('deletesTheLobbyAndReportsSuccess', () => {
      openAndLoad(gameView({ phase: 'WAITING', opponentNickname: null }));
      const deleted = vi.fn();

      store.deleteLobby(deleted);

      const request = http.expectOne(`/api/lobbies/${GAME_ID}`);
      expect(request.request.method).toBe('DELETE');
      expect(deleted).not.toHaveBeenCalled();
      request.flush(null, { status: 204, statusText: 'No Content' });
      expect(deleted).toHaveBeenCalledTimes(1);
    });

    it('exposesTheErrorAndDoesNotReportSuccessWhenItFails', () => {
      openAndLoad(gameView({ phase: 'WAITING', opponentNickname: null }));
      const deleted = vi.fn();

      store.deleteLobby(deleted);
      http
        .expectOne(`/api/lobbies/${GAME_ID}`)
        .flush(
          { code: 'WRONG_PHASE', message: 'Zu spät' },
          { status: 409, statusText: 'Conflict' },
        );

      expect(store.error()?.code).toBe('WRONG_PHASE');
      expect(deleted).not.toHaveBeenCalled();
    });
  });

  describe('open', () => {
    it('hasNoGameBeforeOpening', () => {
      expect(store.game()).toBeNull();
      expect(store.error()).toBeNull();
    });

    it('loadsGameStateFromServer', () => {
      openAndLoad(gameView({ phase: 'PLACEMENT' }));

      expect(store.game()?.phase).toBe('PLACEMENT');
    });

    it('subscribesToGameEventsOfThatGame', () => {
      openAndLoad();

      expect(sources.latest().url).toContain(`/api/games/${GAME_ID}/events`);
    });

    it('exposesErrorWhenGameIsUnknown', () => {
      store.open(GAME_ID);

      http
        .expectOne(gameUrl)
        .flush(
          { code: 'GAME_NOT_FOUND', message: 'unbekannt' },
          { status: 404, statusText: 'Not Found' },
        );

      expect(store.game()).toBeNull();
      expect(store.error()).toEqual({ code: 'GAME_NOT_FOUND', message: 'unbekannt' });
    });

    it('dropsPreviousGameWhenOpeningAnotherOne', () => {
      openAndLoad();
      const first = sources.latest();
      const otherId = '22222222-2222-2222-2222-222222222222';

      store.open(otherId);

      expect(store.game()).toBeNull();
      expect(first.closed).toBe(true);
      http.expectOne(`/api/games/${otherId}`).flush(gameView({ id: otherId }));
      expect(store.game()?.id).toBe(otherId);
    });
  });

  describe('close', () => {
    it('closesEventStreamAndForgetsGame', () => {
      openAndLoad();

      store.close();

      expect(sources.open()).toEqual([]);
      expect(store.game()).toBeNull();
    });
  });

  describe('realtimeUpdates', () => {
    it.each([
      'PLAYER_JOINED',
      'FLEET_READY',
      'GAME_STARTED',
      'SHOT',
      'TURN_CHANGED',
      'GAME_FINISHED',
    ])('reloadsStateAfterEvent%s', (type) => {
      openAndLoad(gameView({ yourTurn: false }));

      sources.latest().emit(type, { gameId: GAME_ID });

      http.expectOne(gameUrl).flush(gameView({ yourTurn: true }));
      expect(store.game()?.yourTurn).toBe(true);
    });

    it('reloadsStateWhenStreamConnectsAgainAfterReconnect', () => {
      openAndLoad(gameView({ yourTurn: false }));
      sources.latest().emit('CONNECTED', { gameId: GAME_ID });
      http.expectOne(gameUrl).flush(gameView({ yourTurn: false }));

      sources.latest().emit('CONNECTED', { gameId: GAME_ID });

      http.expectOne(gameUrl).flush(gameView({ yourTurn: true }));
      expect(store.game()?.yourTurn).toBe(true);
    });

    it('usesOnlyLatestResponseWhenEventsArriveInQuickSuccession', () => {
      openAndLoad(gameView({ yourTurn: false }));
      sources.latest().emit('SHOT', { gameId: GAME_ID });
      const stale = http.expectOne(gameUrl);
      sources.latest().emit('TURN_CHANGED', { gameId: GAME_ID });
      const latest = http.expectOne(gameUrl);

      latest.flush(gameView({ yourTurn: true }));

      expect(stale.cancelled).toBe(true);
      expect(store.game()?.yourTurn).toBe(true);
    });

    it('stopsReloadingAfterClose', () => {
      openAndLoad();
      const source = sources.latest();

      store.close();
      source.emit('SHOT', { gameId: GAME_ID });

      http.expectNone(gameUrl);
    });
  });

  describe('placeFleet', () => {
    const ships = [{ x: 0, y: 0, length: 5, orientation: 'HORIZONTAL' as const }];

    it('sendsFleetAndTakesStateFromResponse', () => {
      openAndLoad(gameView({ phase: 'PLACEMENT', yourFleetReady: false }));

      store.placeFleet(ships);

      const request = http.expectOne(`${gameUrl}/fleet`);
      expect(request.request.method).toBe('PUT');
      expect(request.request.body).toEqual({ ships });
      request.flush(gameView({ phase: 'PLACEMENT', yourFleetReady: true }));
      expect(store.game()?.yourFleetReady).toBe(true);
    });

    it('exposesRuleViolationAndKeepsState', () => {
      openAndLoad(gameView({ phase: 'PLACEMENT', yourFleetReady: false }));

      store.placeFleet(ships);

      http
        .expectOne(`${gameUrl}/fleet`)
        .flush(
          { code: 'SHIPS_TOUCHING', message: 'berühren sich' },
          { status: 400, statusText: 'Bad Request' },
        );
      expect(store.error()?.code).toBe('SHIPS_TOUCHING');
      expect(store.game()?.yourFleetReady).toBe(false);
    });
  });

  describe('fire', () => {
    it('sendsShotAndTakesStateFromResponse', () => {
      openAndLoad();

      store.fire(3, 4);

      const request = http.expectOne(`${gameUrl}/shots`);
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual({ x: 3, y: 4 });
      request.flush({
        result: 'HIT',
        sunkShip: null,
        game: gameView({
          yourTurn: false,
          enemyBoard: { ships: [], shots: [{ x: 3, y: 4, hit: true }] },
        }),
      });
      expect(store.game()?.enemyBoard.shots).toEqual([{ x: 3, y: 4, hit: true }]);
      expect(store.game()?.yourTurn).toBe(false);
    });

    it('remembersResultOfLastShot', () => {
      openAndLoad();
      const sunkShip = { x: 0, y: 0, length: 2, orientation: 'HORIZONTAL' as const };

      store.fire(0, 0);

      http.expectOne(`${gameUrl}/shots`).flush({ result: 'SUNK', sunkShip, game: gameView() });
      expect(store.lastShot()).toEqual({ result: 'SUNK', sunkShip });
    });

    it.each(['NOT_YOUR_TURN', 'CELL_ALREADY_SHOT'])('exposesRejection%sAndKeepsState', (code) => {
      openAndLoad();

      store.fire(3, 4);

      http
        .expectOne(`${gameUrl}/shots`)
        .flush({ code, message: 'abgelehnt' }, { status: 409, statusText: 'Conflict' });
      expect(store.error()?.code).toBe(code);
      expect(store.game()?.enemyBoard.shots).toEqual([]);
    });

    it('reloadsStateAfterConcurrentModification', () => {
      openAndLoad();

      store.fire(3, 4);

      http
        .expectOne(`${gameUrl}/shots`)
        .flush(
          { code: 'CONCURRENT_MODIFICATION', message: 'neu laden' },
          { status: 409, statusText: 'Conflict' },
        );
      http.expectOne(gameUrl).flush(gameView({ yourTurn: false }));
      expect(store.game()?.yourTurn).toBe(false);
    });

    it('clearsPreviousErrorOnNextAction', () => {
      openAndLoad();
      store.fire(3, 4);
      http
        .expectOne(`${gameUrl}/shots`)
        .flush({ code: 'NOT_YOUR_TURN', message: 'nein' }, { status: 409, statusText: 'Conflict' });

      store.fire(5, 5);

      expect(store.error()).toBeNull();
      http.expectOne(`${gameUrl}/shots`);
    });
  });

  describe('resign', () => {
    it('resignsAndTakesFinishedStateFromResponse', () => {
      openAndLoad();

      store.resign();

      const request = http.expectOne(`${gameUrl}/resign`);
      expect(request.request.method).toBe('POST');
      request.flush(gameView({ phase: 'FINISHED', result: 'LOST', yourTurn: false }));
      expect(store.game()?.phase).toBe('FINISHED');
      expect(store.game()?.result).toBe('LOST');
    });
  });

  describe('chat', () => {
    const chatUrl = `${gameUrl}/chat`;
    const first = chatMessage({ id: 'm1', text: 'eins', createdAt: '2026-11-03T12:00:01Z' });
    const second = chatMessage({ id: 'm2', text: 'zwei', createdAt: '2026-11-03T12:00:02Z' });
    const own = chatMessage({
      id: 'm3',
      side: 'A',
      nickname: 'Anna',
      createdAt: '2026-11-03T12:00:03Z',
    });

    function loadChat(history = [first]): void {
      store.loadChat();
      http.expectOne(chatUrl).flush(history);
    }

    it('hasNoMessagesBeforeLoading', () => {
      openAndLoad();

      expect(store.messages()).toEqual([]);
      expect(store.unreadChat()).toBe(0);
    });

    it('loadsHistoryOnlyWhenRequested', () => {
      openAndLoad();

      http.expectNone(chatUrl);
      loadChat([first, second]);

      expect(store.messages()).toEqual([first, second]);
    });

    it('appendsMessageFromChatEventWithoutReloadingTheGame', () => {
      openAndLoad();
      loadChat([first]);

      sources.latest().emit('CHAT', { gameId: GAME_ID, message: second });

      http.expectNone(gameUrl);
      expect(store.messages()).toEqual([first, second]);
    });

    it('ignoresChatEventForMessageThatIsAlreadyKnown', () => {
      openAndLoad();
      loadChat([first]);

      sources.latest().emit('CHAT', { gameId: GAME_ID, message: first });

      expect(store.messages()).toEqual([first]);
    });

    it('keepsMessagesThatArrivedByEventWhileTheHistoryWasLoading', () => {
      openAndLoad();
      store.loadChat();
      const request = http.expectOne(chatUrl);

      sources.latest().emit('CHAT', { gameId: GAME_ID, message: second });
      request.flush([first, second]);

      expect(store.messages()).toEqual([first, second]);
    });

    it('sendsTextAndAddsTheStoredMessage', () => {
      openAndLoad();
      loadChat([first]);

      store.sendChat('Hallo');

      const request = http.expectOne(chatUrl);
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual({ text: 'Hallo' });
      request.flush(own);
      expect(store.messages()).toEqual([first, own]);
    });

    it('showsSentMessageOnlyOnceWhenTheEventEchoesIt', () => {
      openAndLoad();
      loadChat([]);
      store.sendChat('Hallo');
      http.expectOne(chatUrl).flush(own);

      sources.latest().emit('CHAT', { gameId: GAME_ID, message: own });

      expect(store.messages()).toEqual([own]);
    });

    it('showsSentMessageOnlyOnceWhenTheEventArrivesFirst', () => {
      openAndLoad();
      loadChat([]);
      store.sendChat('Hallo');
      const request = http.expectOne(chatUrl);

      sources.latest().emit('CHAT', { gameId: GAME_ID, message: own });
      request.flush(own);

      expect(store.messages()).toEqual([own]);
    });

    it('exposesErrorWhenSendingFails', () => {
      openAndLoad();
      loadChat([]);

      store.sendChat('Hallo');

      http
        .expectOne(chatUrl)
        .flush(
          { code: 'INVALID_REQUEST', message: 'zu lang' },
          { status: 400, statusText: 'Bad Request' },
        );
      expect(store.messages()).toEqual([]);
      expect(store.error()).toEqual({ code: 'INVALID_REQUEST', message: 'zu lang' });
    });

    it('reloadsHistoryWhenStreamConnectsAgainAfterTheChatWasLoaded', () => {
      openAndLoad();
      loadChat([first]);

      sources.latest().emit('CONNECTED', { gameId: GAME_ID });

      http.expectOne(gameUrl).flush(gameView());
      http.expectOne(chatUrl).flush([first, second]);
      expect(store.messages()).toEqual([first, second]);
    });

    it('doesNotLoadHistoryOnConnectedWhenTheChatWasNeverOpened', () => {
      openAndLoad();

      sources.latest().emit('CONNECTED', { gameId: GAME_ID });

      http.expectOne(gameUrl).flush(gameView());
      http.expectNone(chatUrl);
    });

    describe('unreadMessages', () => {
      beforeEach(() => {
        openAndLoad();
        loadChat([]);
      });

      it('countsMessagesOfTheOpponentWhileTheChatIsHidden', () => {
        sources.latest().emit('CHAT', { gameId: GAME_ID, message: first });
        sources.latest().emit('CHAT', { gameId: GAME_ID, message: second });

        expect(store.unreadChat()).toBe(2);
      });

      it('doesNotCountOwnMessages', () => {
        sources.latest().emit('CHAT', { gameId: GAME_ID, message: own });

        expect(store.unreadChat()).toBe(0);
      });

      it('doesNotCountDuplicates', () => {
        sources.latest().emit('CHAT', { gameId: GAME_ID, message: first });
        sources.latest().emit('CHAT', { gameId: GAME_ID, message: first });

        expect(store.unreadChat()).toBe(1);
      });

      it('resetsWhenTheChatBecomesVisible', () => {
        sources.latest().emit('CHAT', { gameId: GAME_ID, message: first });

        store.setChatVisible(true);

        expect(store.unreadChat()).toBe(0);
      });

      it('countsNothingWhileTheChatIsVisible', () => {
        store.setChatVisible(true);

        sources.latest().emit('CHAT', { gameId: GAME_ID, message: first });

        expect(store.unreadChat()).toBe(0);
        expect(store.messages()).toEqual([first]);
      });

      it('countsAgainAfterTheChatIsHidden', () => {
        store.setChatVisible(true);
        store.setChatVisible(false);

        sources.latest().emit('CHAT', { gameId: GAME_ID, message: first });

        expect(store.unreadChat()).toBe(1);
      });
    });

    it('dropsChatWhenTheGameIsClosed', () => {
      openAndLoad();
      loadChat([first]);
      sources.latest().emit('CHAT', { gameId: GAME_ID, message: second });

      store.close();

      expect(store.messages()).toEqual([]);
      expect(store.unreadChat()).toBe(0);
    });
  });
});
