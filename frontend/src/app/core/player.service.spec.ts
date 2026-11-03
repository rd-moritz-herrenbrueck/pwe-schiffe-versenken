import { TestBed } from '@angular/core/testing';
import { PlayerService } from './player.service';

describe('PlayerService', () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => localStorage.clear());

  function create(): PlayerService {
    TestBed.resetTestingModule();
    return TestBed.inject(PlayerService);
  }

  it('hasNoTokenOnFirstVisit', () => {
    const service = create();

    expect(service.token()).toBeNull();
    expect(service.nickname()).toBeNull();
    expect(service.loggedIn()).toBe(false);
  });

  it('keepsSavedPlayerInSignals', () => {
    const service = create();

    service.save({ nickname: 'Anna', token: 'secret-token' });

    expect(service.token()).toBe('secret-token');
    expect(service.nickname()).toBe('Anna');
    expect(service.loggedIn()).toBe(true);
  });

  it('restoresSavedPlayerAfterReload', () => {
    create().save({ nickname: 'Anna', token: 'secret-token' });

    const afterReload = create();

    expect(afterReload.token()).toBe('secret-token');
    expect(afterReload.nickname()).toBe('Anna');
    expect(afterReload.loggedIn()).toBe(true);
  });

  it('forgetsPlayerAfterClear', () => {
    const service = create();
    service.save({ nickname: 'Anna', token: 'secret-token' });

    service.clear();

    expect(service.loggedIn()).toBe(false);
    expect(create().token()).toBeNull();
  });
});
