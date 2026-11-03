import { TestBed } from '@angular/core/testing';
import { Subscription } from 'rxjs';
import { GAME_ID } from '../testing/fixtures';
import { FakeEventSources, fakeEventSources } from '../testing/fake-event-source';
import { EventService, RECONNECT_DELAYS_MS } from './event.service';
import { GameEvent, LobbyEvent } from './models';
import { PlayerService } from './player.service';

describe('EventService', () => {
  let sources: FakeEventSources;
  let service: EventService;
  let subscription: Subscription | undefined;

  beforeEach(() => {
    localStorage.clear();
    sources = fakeEventSources();
    TestBed.configureTestingModule({ providers: sources.providers });
    TestBed.inject(PlayerService).save({ nickname: 'Anna', token: 'to/ken+1' });
    service = TestBed.inject(EventService);
  });

  afterEach(() => {
    subscription?.unsubscribe();
    vi.useRealTimers();
    localStorage.clear();
  });

  describe('watchGame', () => {
    it('connectsToGameStreamWithEncodedTokenAsQueryParameter', () => {
      subscription = service.watchGame(GAME_ID).subscribe();

      expect(sources.latest().url).toBe(`/api/games/${GAME_ID}/events?access_token=to%2Fken%2B1`);
    });

    it.each([
      'CONNECTED',
      'PLAYER_JOINED',
      'FLEET_READY',
      'GAME_STARTED',
      'SHOT',
      'TURN_CHANGED',
      'GAME_FINISHED',
      'CHAT',
    ])('emitsNamedEvent%s', (type) => {
      const events: GameEvent[] = [];
      subscription = service.watchGame(GAME_ID).subscribe((event) => events.push(event));

      sources.latest().emit(type, { gameId: GAME_ID });

      expect(events).toEqual([{ type, gameId: GAME_ID }]);
    });

    it('emitsEventDataAsParsedObject', () => {
      const events: GameEvent[] = [];
      subscription = service.watchGame(GAME_ID).subscribe((event) => events.push(event));

      sources.latest().emit('SHOT', { gameId: GAME_ID, side: 'A', x: 3, y: 4, result: 'SUNK' });

      expect(events[0]).toMatchObject({ side: 'A', x: 3, y: 4, result: 'SUNK' });
    });

    it('ignoresHeartbeats', () => {
      const events: GameEvent[] = [];
      subscription = service.watchGame(GAME_ID).subscribe((event) => events.push(event));

      sources.latest().emit('HEARTBEAT');

      expect(events).toEqual([]);
    });

    it('closesConnectionOnUnsubscribe', () => {
      subscription = service.watchGame(GAME_ID).subscribe();

      subscription.unsubscribe();

      expect(sources.latest().closed).toBe(true);
    });

    it('failsWithoutToken', () => {
      TestBed.inject(PlayerService).clear();
      const errors: unknown[] = [];

      subscription = service.watchGame(GAME_ID).subscribe({ error: (error) => errors.push(error) });

      expect(errors).toHaveLength(1);
      expect(sources.created).toHaveLength(0);
    });
  });

  describe('reconnect', () => {
    beforeEach(() => vi.useFakeTimers());

    it('doesNotOpenSecondConnectionWhileBrowserRetriesOnItsOwn', () => {
      subscription = service.watchGame(GAME_ID).subscribe();

      sources.latest().fail(false);
      vi.advanceTimersByTime(RECONNECT_DELAYS_MS[0] * 10);

      expect(sources.created).toHaveLength(1);
    });

    it('opensNewConnectionAfterDelayWhenBrowserClosedIt', () => {
      subscription = service.watchGame(GAME_ID).subscribe();

      sources.latest().fail(true);
      vi.advanceTimersByTime(RECONNECT_DELAYS_MS[0] - 1);
      expect(sources.created).toHaveLength(1);
      vi.advanceTimersByTime(1);

      expect(sources.created).toHaveLength(2);
      expect(sources.open()).toEqual([sources.latest()]);
    });

    it('usesLongerDelayForEachFailedAttempt', () => {
      subscription = service.watchGame(GAME_ID).subscribe();

      sources.latest().fail(true);
      vi.advanceTimersByTime(RECONNECT_DELAYS_MS[0]);
      sources.latest().fail(true);
      vi.advanceTimersByTime(RECONNECT_DELAYS_MS[1] - 1);
      expect(sources.created).toHaveLength(2);
      vi.advanceTimersByTime(1);

      expect(sources.created).toHaveLength(3);
    });

    it('failsAfterAllReconnectAttemptsFailed', () => {
      const errors: unknown[] = [];
      subscription = service.watchGame(GAME_ID).subscribe({ error: (error) => errors.push(error) });

      for (const delay of RECONNECT_DELAYS_MS) {
        sources.latest().fail(true);
        vi.advanceTimersByTime(delay);
      }
      expect(errors).toHaveLength(0);
      sources.latest().fail(true);
      vi.advanceTimersByTime(RECONNECT_DELAYS_MS[RECONNECT_DELAYS_MS.length - 1] * 10);

      expect(errors).toHaveLength(1);
      expect(sources.created).toHaveLength(RECONNECT_DELAYS_MS.length + 1);
    });

    it('startsCountingAgainAfterSuccessfulReconnect', () => {
      subscription = service.watchGame(GAME_ID).subscribe();
      sources.latest().fail(true);
      vi.advanceTimersByTime(RECONNECT_DELAYS_MS[0]);
      sources.latest().emit('CONNECTED', { gameId: GAME_ID });

      sources.latest().fail(true);
      vi.advanceTimersByTime(RECONNECT_DELAYS_MS[0]);

      expect(sources.created).toHaveLength(3);
    });

    it('opensNoNewConnectionAfterUnsubscribe', () => {
      subscription = service.watchGame(GAME_ID).subscribe();
      sources.latest().fail(true);

      subscription.unsubscribe();
      vi.advanceTimersByTime(RECONNECT_DELAYS_MS[0] * 10);

      expect(sources.created).toHaveLength(1);
    });
  });

  describe('watchLobbies', () => {
    it('connectsToLobbyStreamWithTokenAsQueryParameter', () => {
      subscription = service.watchLobbies().subscribe();

      expect(sources.latest().url).toBe('/api/lobbies/events?access_token=to%2Fken%2B1');
    });

    it.each(['CONNECTED', 'LOBBY_OPENED', 'LOBBY_CLOSED'])('emitsNamedEvent%s', (type) => {
      const events: LobbyEvent[] = [];
      subscription = service.watchLobbies().subscribe((event) => events.push(event));

      sources.latest().emit(type, { gameId: GAME_ID });

      expect(events).toEqual([{ type, gameId: GAME_ID }]);
    });

    it('closesConnectionOnUnsubscribe', () => {
      subscription = service.watchLobbies().subscribe();

      subscription.unsubscribe();

      expect(sources.latest().closed).toBe(true);
    });
  });
});
