import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { PlayerService } from '../../core/player.service';
import { gameView, lobby, rules } from '../../testing/fixtures';
import { FakeEventSources, fakeEventSources } from '../../testing/fake-event-source';
import { LobbyListPage } from './lobby-list-page';

@Component({ template: '' })
class GameStub {}

describe('LobbyListPage', () => {
  const anna = lobby('aaaaaaaa-0000-0000-0000-000000000001', 'Anna');
  const ben = lobby('bbbbbbbb-0000-0000-0000-000000000002', 'Ben');
  let fixture: ComponentFixture<LobbyListPage>;
  let http: HttpTestingController;
  let sources: FakeEventSources;

  beforeEach(() => {
    localStorage.clear();
    sources = fakeEventSources();
    TestBed.configureTestingModule({
      imports: [LobbyListPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'games/:id', component: GameStub }]),
        ...sources.providers,
      ],
    });
    TestBed.inject(PlayerService).save({ nickname: 'Zoe', token: 'secret-token' });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  async function render(lobbies = [anna, ben]): Promise<void> {
    fixture = TestBed.createComponent(LobbyListPage);
    await fixture.whenStable();
    http.expectOne('/api/lobbies').flush(lobbies);
    await fixture.whenStable();
  }

  const root = () => fixture.nativeElement as HTMLElement;
  const byId = (id: string) => root().querySelector<HTMLElement>(`[data-testid="${id}"]`);
  const row = (id: string) => byId(`lobby-${id}`);

  describe('list', () => {
    it('showsOpenLobbiesWithCreator', async () => {
      await render();

      expect(row(anna.id)?.textContent).toContain('Anna');
      expect(row(ben.id)?.textContent).toContain('Ben');
    });

    it('showsBoardSizeOfLobbyRules', async () => {
      await render([
        lobby(anna.id, 'Anna'),
        { ...ben, rules: rules({ boardWidth: 8, boardHeight: 6, fleet: [2, 3] }) },
      ]);

      expect(row(anna.id)?.textContent).toMatch(/10\s*[x×]\s*10/);
      expect(row(ben.id)?.textContent).toMatch(/8\s*[x×]\s*6/);
    });

    it('showsHintWhenNoLobbyIsOpen', async () => {
      await render([]);

      expect(byId('lobby-empty')).not.toBeNull();
    });

    it('hidesHintWhenLobbiesExist', async () => {
      await render();

      expect(byId('lobby-empty')).toBeNull();
    });

    it('showsErrorWhenListCannotBeLoaded', async () => {
      fixture = TestBed.createComponent(LobbyListPage);
      await fixture.whenStable();

      http
        .expectOne('/api/lobbies')
        .flush(
          { code: 'ERROR', message: 'Serverfehler' },
          { status: 500, statusText: 'Server Error' },
        );
      await fixture.whenStable();

      expect(byId('error-message')?.textContent).toContain('Serverfehler');
    });
  });

  describe('creating', () => {
    it('createsLobbyWithDefaultRulesAndOpensGame', async () => {
      await render();

      byId('create-lobby-button')!.click();
      await fixture.whenStable();

      const request = http.expectOne((r) => r.url === '/api/lobbies' && r.method === 'POST');
      expect(request.request.body.rules ?? null).toBeNull();
      request.flush(gameView({ id: 'new-game', phase: 'WAITING' }));
      await fixture.whenStable();
      expect(TestBed.inject(Router).url).toBe('/games/new-game');
    });
  });

  describe('joining', () => {
    it('joinsChosenLobbyAndOpensGame', async () => {
      await render();

      byId(`join-lobby-${ben.id}`)!.click();
      await fixture.whenStable();

      const request = http.expectOne('/api/lobbies/join');
      expect(request.request.body).toEqual({ gameId: ben.id });
      request.flush(gameView({ id: ben.id, phase: 'PLACEMENT' }));
      await fixture.whenStable();
      expect(TestBed.inject(Router).url).toBe(`/games/${ben.id}`);
    });

    it('showsRejectionAndStaysOnLobbyList', async () => {
      await render();

      byId(`join-lobby-${anna.id}`)!.click();
      await fixture.whenStable();

      http
        .expectOne('/api/lobbies/join')
        .flush(
          { code: 'CANNOT_JOIN_OWN_GAME', message: 'Eigenes Spiel' },
          { status: 409, statusText: 'Conflict' },
        );
      await fixture.whenStable();
      expect(byId('error-message')?.textContent).toContain('Eigenes Spiel');
      expect(TestBed.inject(Router).url).toBe('/');
    });
  });

  describe('realtimeUpdates', () => {
    it('subscribesToLobbyEvents', async () => {
      await render();

      expect(sources.latest().url).toContain('/api/lobbies/events');
    });

    it('addsNewLobbyOnLobbyOpened', async () => {
      await render([anna]);

      sources.latest().emit('LOBBY_OPENED', { gameId: ben.id, lobby: ben });
      await fixture.whenStable();

      expect(row(ben.id)).not.toBeNull();
      expect(row(anna.id)).not.toBeNull();
    });

    it('showsLobbyOnlyOnceWhenOpenedEventArrivesTwice', async () => {
      await render([anna]);

      sources.latest().emit('LOBBY_OPENED', { gameId: anna.id, lobby: anna });
      await fixture.whenStable();

      expect(root().querySelectorAll(`[data-testid="lobby-${anna.id}"]`)).toHaveLength(1);
    });

    it('removesLobbyOnLobbyClosed', async () => {
      await render();

      sources.latest().emit('LOBBY_CLOSED', { gameId: anna.id });
      await fixture.whenStable();

      expect(row(anna.id)).toBeNull();
      expect(row(ben.id)).not.toBeNull();
    });

    it('showsHintWhenLastLobbyCloses', async () => {
      await render([anna]);

      sources.latest().emit('LOBBY_CLOSED', { gameId: anna.id });
      await fixture.whenStable();

      expect(byId('lobby-empty')).not.toBeNull();
    });

    it('reloadsListWhenStreamConnectsAgain', async () => {
      await render([anna]);

      sources.latest().emit('CONNECTED');
      http.expectOne('/api/lobbies').flush([ben]);
      await fixture.whenStable();

      expect(row(anna.id)).toBeNull();
      expect(row(ben.id)).not.toBeNull();
    });

    it('closesStreamWhenPageIsLeft', async () => {
      await render();

      fixture.destroy();

      expect(sources.open()).toEqual([]);
    });
  });

  describe('createWithOptions', () => {
    it('hidesTheFormUntilRequested', async () => {
      await render();

      expect(byId('create-lobby-form')).toBeNull();
    });

    it('showsTheFormOnRequestAndHidesItAgain', async () => {
      await render();

      byId('open-lobby-form-button')!.click();
      await fixture.whenStable();
      expect(byId('create-lobby-form')).not.toBeNull();

      byId('open-lobby-form-button')!.click();
      await fixture.whenStable();
      expect(byId('create-lobby-form')).toBeNull();
    });

    it('createsPrivateLobbyWithChosenOptionsAndOpensGame', async () => {
      await render();
      byId('open-lobby-form-button')!.click();
      await fixture.whenStable();
      byId('visibility-private')!.click();
      await fixture.whenStable();

      byId('create-submit-button')!.click();
      await fixture.whenStable();

      const request = http.expectOne((r) => r.url === '/api/lobbies' && r.method === 'POST');
      expect(request.request.body).toEqual({
        rules: rules(),
        visibility: 'PRIVATE',
        vsAi: false,
      });
      request.flush(gameView({ id: 'private-game', phase: 'WAITING', inviteCode: 'ABCD2345' }));
      await fixture.whenStable();
      expect(TestBed.inject(Router).url).toBe('/games/private-game');
    });

    it('showsRejectionOfTheServerAndKeepsTheForm', async () => {
      await render();
      byId('open-lobby-form-button')!.click();
      await fixture.whenStable();
      byId('create-submit-button')!.click();
      await fixture.whenStable();

      http
        .expectOne((r) => r.url === '/api/lobbies' && r.method === 'POST')
        .flush(
          { code: 'INVALID_RULE_SET', message: 'Regeln ungültig' },
          { status: 400, statusText: 'Bad Request' },
        );
      await fixture.whenStable();

      expect(byId('error-message')?.textContent).toContain('Regeln ungültig');
      expect(byId('create-lobby-form')).not.toBeNull();
      expect(TestBed.inject(Router).url).toBe('/');
    });
  });

  describe('playAgainstAi', () => {
    it('createsGameAgainstAiWithStandardRulesAndOpensGame', async () => {
      await render();

      byId('play-vs-ai-button')!.click();
      await fixture.whenStable();

      const request = http.expectOne((r) => r.url === '/api/lobbies' && r.method === 'POST');
      expect(request.request.body.vsAi).toBe(true);
      expect(request.request.body.rules ?? null).toBeNull();
      request.flush(gameView({ id: 'ai-game', phase: 'PLACEMENT', opponentNickname: 'KI' }));
      await fixture.whenStable();
      expect(TestBed.inject(Router).url).toBe('/games/ai-game');
    });
  });

  describe('joinByInviteCode', () => {
    const codeInput = () => byId('invite-code-input') as HTMLInputElement;
    const joinButton = () => byId('join-by-code-button') as HTMLButtonElement;

    async function enter(code: string): Promise<void> {
      codeInput().value = code;
      codeInput().dispatchEvent(new Event('input'));
      await fixture.whenStable();
    }

    it('cannotJoinWithoutCode', async () => {
      await render();

      expect(joinButton().disabled).toBe(true);

      await enter('   ');
      expect(joinButton().disabled).toBe(true);
    });

    it('joinsWithTrimmedCodeAndOpensGame', async () => {
      await render();
      await enter('  ABCD2345 ');

      joinButton().click();
      await fixture.whenStable();

      const request = http.expectOne('/api/lobbies/join');
      expect(request.request.body).toEqual({ inviteCode: 'ABCD2345' });
      request.flush(gameView({ id: 'private-game', phase: 'PLACEMENT' }));
      await fixture.whenStable();
      expect(TestBed.inject(Router).url).toBe('/games/private-game');
    });

    it('showsRejectionForUnknownCodeAndStaysOnLobbyList', async () => {
      await render();
      await enter('ZZZZZZZZ');

      joinButton().click();
      await fixture.whenStable();

      http
        .expectOne('/api/lobbies/join')
        .flush(
          { code: 'GAME_NOT_FOUND', message: 'Spiel nicht gefunden' },
          { status: 404, statusText: 'Not Found' },
        );
      await fixture.whenStable();
      expect(byId('error-message')?.textContent).toContain('Spiel nicht gefunden');
      expect(TestBed.inject(Router).url).toBe('/');
    });
  });
});
