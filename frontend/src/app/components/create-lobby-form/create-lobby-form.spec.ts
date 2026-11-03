import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CreateLobbyOptions, RuleSet } from '../../core/models';
import { DEFAULT_RULES, rules } from '../../testing/fixtures';
import { CreateLobbyForm } from './create-lobby-form';

/**
 * Das Formular prüft Regeln nur über den Server (`POST /api/rules/check`). Die Standardregeln gelten als gültig, ohne
 * dass angefragt wird. Jede Änderung an Regeln löst genau eine neue Prüfung aus, nur die letzte zählt. Die Eingaben
 * `rules-width`, `rules-height`, `fleet-new-length` sind native `<input>`, `rules-shoot-again`, `rules-ships-touch`,
 * `visibility-private` und `vs-ai` native Checkboxen.
 */
describe('CreateLobbyForm', () => {
  const CHECK_URL = '/api/rules/check';
  let fixture: ComponentFixture<CreateLobbyForm>;
  let http: HttpTestingController;
  let submitted: Required<CreateLobbyOptions>[];

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [CreateLobbyForm],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CreateLobbyForm);
    submitted = [];
    fixture.componentInstance.submitted.subscribe((options) => submitted.push(options));
    await fixture.whenStable();
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  const root = () => fixture.nativeElement as HTMLElement;
  const byId = (id: string) => root().querySelector<HTMLElement>(`[data-testid="${id}"]`);
  const field = (id: string) => byId(id) as HTMLInputElement;
  const submitButton = () => byId('create-submit-button') as HTMLButtonElement;

  async function setNumber(id: string, value: number): Promise<void> {
    field(id).value = String(value);
    field(id).dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  async function click(id: string): Promise<void> {
    byId(id)!.click();
    await fixture.whenStable();
  }

  async function answer(request: TestRequest, body: object): Promise<void> {
    request.flush(body);
    await fixture.whenStable();
  }

  async function expectCheck(expected: RuleSet): Promise<TestRequest> {
    const request = http.expectOne(CHECK_URL);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(expected);
    return request;
  }

  describe('initialState', () => {
    it('startsWithStandardRules', () => {
      expect(field('rules-width').value).toBe('10');
      expect(field('rules-height').value).toBe('10');
      expect([0, 1, 2, 3].map((i) => byId(`fleet-ship-${i}`)?.getAttribute('data-length'))).toEqual(
        ['5', '4', '3', '2'],
      );
      expect(byId('fleet-ship-4')).toBeNull();
      expect(field('rules-shoot-again').checked).toBe(false);
      expect(field('rules-ships-touch').checked).toBe(false);
      expect(field('visibility-private').checked).toBe(false);
      expect(field('vs-ai').checked).toBe(false);
    });

    it('canBeSubmittedImmediatelyWithoutAskingTheServer', async () => {
      expect(submitButton().disabled).toBe(false);

      await click('create-submit-button');

      expect(submitted).toEqual([{ rules: DEFAULT_RULES, visibility: 'PUBLIC', vsAi: false }]);
      expect(byId('rules-error')).toBeNull();
    });
  });

  describe('ruleCheck', () => {
    it('asksTheServerWhenTheBoardSizeChanges', async () => {
      await setNumber('rules-width', 8);

      const request = await expectCheck(rules({ boardWidth: 8 }));
      await answer(request, { valid: true });

      expect(submitButton().disabled).toBe(false);
      await click('create-submit-button');
      expect(submitted[0].rules.boardWidth).toBe(8);
    });

    it('disablesSubmitWhileTheCheckIsRunning', async () => {
      await setNumber('rules-height', 6);

      expect(submitButton().disabled).toBe(true);
      await click('create-submit-button');
      expect(submitted).toEqual([]);
      (await expectCheck(rules({ boardHeight: 6 }))).flush({ valid: true });
    });

    it('showsServerMessageAndBlocksSubmitForInvalidRules', async () => {
      await setNumber('rules-width', 4);
      await setNumber('rules-height', 4);
      const [, second] = http.match(CHECK_URL);

      await answer(second, {
        valid: false,
        code: 'FLEET_DOES_NOT_FIT',
        message: 'Flotte passt nicht auf das Feld',
      });

      expect(byId('rules-error')?.textContent).toContain('Flotte passt nicht auf das Feld');
      expect(submitButton().disabled).toBe(true);
      await click('create-submit-button');
      expect(submitted).toEqual([]);
    });

    it('allowsSubmitAgainOnceTheRulesAreFixed', async () => {
      await setNumber('rules-width', 4);
      await answer(http.expectOne(CHECK_URL), {
        valid: false,
        code: 'FLEET_DOES_NOT_FIT',
        message: 'nein',
      });

      await setNumber('rules-width', 10);
      await answer(http.expectOne(CHECK_URL), { valid: true });

      expect(byId('rules-error')).toBeNull();
      expect(submitButton().disabled).toBe(false);
    });

    it('onlyTheLatestCheckCounts', async () => {
      await setNumber('rules-width', 4);
      await setNumber('rules-width', 12);
      const [outdated, latest] = http.match(CHECK_URL);

      expect(outdated.cancelled).toBe(true);
      await answer(latest, { valid: true });

      expect(byId('rules-error')).toBeNull();
      expect(submitButton().disabled).toBe(false);
    });

    it('checksWhenAFlagIsToggled', async () => {
      await click('rules-ships-touch');
      await answer(await expectCheck(rules({ shipsMayTouch: true })), { valid: true });

      await click('rules-shoot-again');
      await answer(await expectCheck(rules({ shipsMayTouch: true, shootAgainOnHit: true })), {
        valid: true,
      });

      await click('create-submit-button');
      expect(submitted[0].rules).toEqual(rules({ shipsMayTouch: true, shootAgainOnHit: true }));
    });

    it('showsAnErrorWhenTheCheckCannotBeDone', async () => {
      await setNumber('rules-width', 8);

      http
        .expectOne(CHECK_URL)
        .flush(
          { code: 'ERROR', message: 'Serverfehler' },
          { status: 500, statusText: 'Server Error' },
        );
      await fixture.whenStable();

      expect(byId('rules-error')?.textContent).toContain('Serverfehler');
      expect(submitButton().disabled).toBe(true);
    });
  });

  describe('boardSize', () => {
    it.each([26, 0, -3])('rejectsSize%sWithoutAskingTheServer', async (size) => {
      await setNumber('rules-width', size);

      expect(byId('rules-error')).not.toBeNull();
      expect(submitButton().disabled).toBe(true);
      http.expectNone(CHECK_URL);
    });

    it('acceptsTheMaximumOf25', async () => {
      await setNumber('rules-width', 25);

      await answer(await expectCheck(rules({ boardWidth: 25 })), { valid: true });

      expect(byId('rules-error')).toBeNull();
    });

    it('limitsTheInputsToTheAllowedRange', () => {
      expect(field('rules-width').min).toBe('1');
      expect(field('rules-width').max).toBe('25');
      expect(field('rules-height').min).toBe('1');
      expect(field('rules-height').max).toBe('25');
    });
  });

  describe('fleet', () => {
    const lengths = () =>
      Array.from(root().querySelectorAll('[data-testid^="fleet-ship-"]')).map((ship) =>
        ship.getAttribute('data-length'),
      );

    it('addsAShipWithTheChosenLength', async () => {
      await setNumber('fleet-new-length', 3);

      await click('fleet-add-button');

      expect(lengths()).toEqual(['5', '4', '3', '2', '3']);
      await answer(await expectCheck(rules({ fleet: [5, 4, 3, 2, 3] })), { valid: true });
    });

    it('removesTheChosenShip', async () => {
      await click('fleet-remove-0');

      expect(lengths()).toEqual(['4', '3', '2']);
      await answer(await expectCheck(rules({ fleet: [4, 3, 2] })), { valid: true });
    });

    it('doesNotAddShipsShorterThanTwo', async () => {
      await setNumber('fleet-new-length', 1);

      expect((byId('fleet-add-button') as HTMLButtonElement).disabled).toBe(true);
      await click('fleet-add-button');
      expect(lengths()).toEqual(['5', '4', '3', '2']);
      http.expectNone(CHECK_URL);
    });

    it('submitsTheEditedFleet', async () => {
      await setNumber('fleet-new-length', 2);
      await click('fleet-add-button');
      await answer(await expectCheck(rules({ fleet: [5, 4, 3, 2, 2] })), { valid: true });

      await click('create-submit-button');

      expect(submitted[0].rules.fleet).toEqual([5, 4, 3, 2, 2]);
    });
  });

  describe('options', () => {
    it('submitsPrivateLobby', async () => {
      await click('visibility-private');

      await click('create-submit-button');

      expect(submitted).toEqual([{ rules: DEFAULT_RULES, visibility: 'PRIVATE', vsAi: false }]);
    });

    it('submitsGameAgainstAi', async () => {
      await click('vs-ai');

      await click('create-submit-button');

      expect(submitted).toEqual([{ rules: DEFAULT_RULES, visibility: 'PUBLIC', vsAi: true }]);
    });

    it('offersNoPrivateLobbyAgainstTheAi', async () => {
      await click('visibility-private');

      await click('vs-ai');

      expect(field('visibility-private').disabled).toBe(true);
      expect(field('visibility-private').checked).toBe(false);
    });

    it('enablesPrivateLobbyAgainWhenTheAiIsDeselected', async () => {
      await click('vs-ai');

      await click('vs-ai');

      expect(field('visibility-private').disabled).toBe(false);
    });

    it('submitsEditedRulesTogetherWithTheOptions', async () => {
      await setNumber('rules-width', 8);
      await answer(await expectCheck(rules({ boardWidth: 8 })), { valid: true });
      await click('visibility-private');

      await click('create-submit-button');

      expect(submitted).toEqual([
        { rules: rules({ boardWidth: 8 }), visibility: 'PRIVATE', vsAi: false },
      ]);
    });
  });
});
