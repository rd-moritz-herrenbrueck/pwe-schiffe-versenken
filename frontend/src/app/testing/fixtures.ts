import { ChatMessage, GameSummary, GameView, LobbySummary, RuleSet } from '../core/models';

export const DEFAULT_RULES: RuleSet = {
  boardWidth: 10,
  boardHeight: 10,
  fleet: [5, 4, 3, 2],
  shootAgainOnHit: false,
  shipsMayTouch: false,
};

export const GAME_ID = '11111111-1111-1111-1111-111111111111';

export function rules(overrides: Partial<RuleSet> = {}): RuleSet {
  return { ...DEFAULT_RULES, ...overrides };
}

export function gameView(overrides: Partial<GameView> = {}): GameView {
  return {
    id: GAME_ID,
    phase: 'RUNNING',
    you: 'A',
    opponentNickname: 'Gegner',
    rules: DEFAULT_RULES,
    yourTurn: true,
    yourFleetReady: true,
    opponentFleetReady: true,
    result: null,
    inviteCode: null,
    ownBoard: { ships: [], shots: [] },
    enemyBoard: { ships: [], shots: [] },
    ...overrides,
  };
}

export function lobby(id: string, creatorNickname = 'Anna'): LobbySummary {
  return { id, creatorNickname, rules: DEFAULT_RULES, createdAt: '2026-11-03T12:00:00Z' };
}

export function chatMessage(overrides: Partial<ChatMessage> & { id: string }): ChatMessage {
  return {
    side: 'B',
    nickname: 'Gegner',
    text: 'Hallo',
    createdAt: '2026-11-03T12:00:00Z',
    ...overrides,
  };
}

export function gameSummary(overrides: Partial<GameSummary> & { id: string }): GameSummary {
  return {
    phase: 'RUNNING',
    opponentNickname: 'Gegner',
    yourTurn: false,
    result: null,
    updatedAt: '2026-11-03T12:00:00Z',
    ...overrides,
  };
}
