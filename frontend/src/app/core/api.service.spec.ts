import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { DEFAULT_RULES, GAME_ID, gameView } from '../testing/fixtures';
import { ApiService } from './api.service';

describe('ApiService', () => {
  let api: ApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('createsPlayerWithNickname', () => {
    let result: unknown;
    api.createPlayer('Anna').subscribe((player) => (result = player));

    const request = http.expectOne('/api/players');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ nickname: 'Anna' });
    request.flush({ id: 'p1', nickname: 'Anna', token: 't' });

    expect(result).toEqual({ id: 'p1', nickname: 'Anna', token: 't' });
  });

  it('listsOpenLobbies', () => {
    api.listLobbies().subscribe();

    const request = http.expectOne('/api/lobbies');
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('createsLobbyWithoutRulesForDefaultRules', () => {
    api.createLobby().subscribe();

    const request = http.expectOne('/api/lobbies');
    expect(request.request.method).toBe('POST');
    expect(request.request.body.rules ?? null).toBeNull();
    request.flush(gameView());
  });

  it('createsLobbyWithGivenRules', () => {
    api.createLobby(DEFAULT_RULES).subscribe();

    const request = http.expectOne('/api/lobbies');
    expect(request.request.body).toEqual({ rules: DEFAULT_RULES });
    request.flush(gameView());
  });

  it('joinsLobbyById', () => {
    api.joinLobby(GAME_ID).subscribe();

    const request = http.expectOne('/api/lobbies/join');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ gameId: GAME_ID });
    request.flush(gameView());
  });

  it('listsOwnGames', () => {
    api.listGames().subscribe();

    const request = http.expectOne('/api/games');
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('loadsGameFromPlayersPointOfView', () => {
    api.getGame(GAME_ID).subscribe();

    const request = http.expectOne(`/api/games/${GAME_ID}`);
    expect(request.request.method).toBe('GET');
    request.flush(gameView());
  });

  it('placesFleetWithPut', () => {
    const ships = [{ x: 0, y: 0, length: 5, orientation: 'HORIZONTAL' as const }];

    api.placeFleet(GAME_ID, ships).subscribe();

    const request = http.expectOne(`/api/games/${GAME_ID}/fleet`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ ships });
    request.flush(gameView());
  });

  it('firesShotAtCoordinate', () => {
    api.fire(GAME_ID, 3, 7).subscribe();

    const request = http.expectOne(`/api/games/${GAME_ID}/shots`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ x: 3, y: 7 });
    request.flush({ result: 'MISS', sunkShip: null, game: gameView() });
  });

  it('resignsGame', () => {
    api.resign(GAME_ID).subscribe();

    const request = http.expectOne(`/api/games/${GAME_ID}/resign`);
    expect(request.request.method).toBe('POST');
    request.flush(gameView());
  });
});
