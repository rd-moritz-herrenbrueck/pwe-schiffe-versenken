import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { GAME_ID, gameView } from '../testing/fixtures';
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
});
