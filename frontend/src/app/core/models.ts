export type Orientation = 'HORIZONTAL' | 'VERTICAL';
export type PlayerSide = 'A' | 'B';
export type GamePhase = 'WAITING' | 'PLACEMENT' | 'RUNNING' | 'FINISHED' | 'ABANDONED';
export type GameResult = 'WON' | 'LOST';
export type ShotResult = 'MISS' | 'HIT' | 'SUNK' | 'WIN';

export interface RuleSet {
  boardWidth: number;
  boardHeight: number;
  fleet: number[];
  shootAgainOnHit: boolean;
  shipsMayTouch: boolean;
}

export interface ShipPlacement {
  x: number;
  y: number;
  length: number;
  orientation: Orientation;
}

export interface ShipView extends ShipPlacement {
  sunk: boolean;
}

export interface ShotView {
  x: number;
  y: number;
  hit: boolean;
}

export interface BoardView {
  ships: ShipView[];
  shots: ShotView[];
}

export interface GameView {
  id: string;
  phase: GamePhase;
  you: PlayerSide;
  opponentNickname: string | null;
  rules: RuleSet;
  yourTurn: boolean;
  yourFleetReady: boolean;
  opponentFleetReady: boolean;
  result: GameResult | null;
  ownBoard: BoardView;
  enemyBoard: BoardView;
}

export interface GameSummary {
  id: string;
  phase: GamePhase;
  opponentNickname: string | null;
  yourTurn: boolean;
  result: GameResult | null;
  updatedAt: string;
}

export interface LobbySummary {
  id: string;
  creatorNickname: string;
  rules: RuleSet;
  createdAt: string;
}

export interface PlayerCreated {
  id: string;
  nickname: string;
  token: string;
}

export interface ShotResponse {
  result: ShotResult;
  sunkShip: ShipPlacement | null;
  game: GameView;
}

export interface ApiError {
  code: string;
  message: string;
}

export type GameEventType =
  | 'CONNECTED'
  | 'PLAYER_JOINED'
  | 'FLEET_READY'
  | 'GAME_STARTED'
  | 'SHOT'
  | 'TURN_CHANGED'
  | 'GAME_FINISHED';

export interface GameEvent {
  type: GameEventType;
  gameId: string;
  side?: PlayerSide;
  nickname?: string;
  x?: number;
  y?: number;
  result?: ShotResult;
  sunkShip?: ShipPlacement;
  currentTurn?: PlayerSide;
  phase?: GamePhase;
  winner?: PlayerSide;
}

export type LobbyEventType = 'CONNECTED' | 'LOBBY_OPENED' | 'LOBBY_CLOSED';

export interface LobbyEvent {
  type: LobbyEventType;
  gameId?: string;
  lobby?: LobbySummary;
}
