import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ChatMessage } from '../../core/models';
import { chatMessage } from '../../testing/fixtures';
import { Chat } from './chat';

describe('Chat', () => {
  const fromOpponent = chatMessage({ id: 'm1', side: 'B', nickname: 'Ben', text: 'Viel Glück!' });
  const fromMe = chatMessage({ id: 'm2', side: 'A', nickname: 'Anna', text: 'Dir auch' });
  let fixture: ComponentFixture<Chat>;
  let sent: string[];

  async function render(messages: ChatMessage[] = []): Promise<void> {
    fixture = TestBed.createComponent(Chat);
    fixture.componentRef.setInput('messages', messages);
    fixture.componentRef.setInput('you', 'A');
    sent = [];
    fixture.componentInstance.send.subscribe((text) => sent.push(text));
    await fixture.whenStable();
  }

  const root = () => fixture.nativeElement as HTMLElement;
  const byId = (id: string) => root().querySelector<HTMLElement>(`[data-testid="${id}"]`);
  const input = () => byId('chat-input') as HTMLInputElement;
  const sendButton = () => byId('chat-send-button') as HTMLButtonElement;

  async function type(text: string): Promise<void> {
    input().value = text;
    input().dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  async function submit(): Promise<void> {
    sendButton().click();
    await fixture.whenStable();
  }

  describe('history', () => {
    it('showsHintWhenThereAreNoMessages', async () => {
      await render([]);

      expect(byId('chat-empty')).not.toBeNull();
    });

    it('showsMessagesWithSenderAndTextInGivenOrder', async () => {
      await render([fromOpponent, fromMe]);

      const items = root().querySelectorAll('[data-testid^="chat-message-"]');
      expect(items).toHaveLength(2);
      expect(items[0].textContent).toContain('Ben');
      expect(items[0].textContent).toContain('Viel Glück!');
      expect(items[1].textContent).toContain('Dir auch');
      expect(byId('chat-empty')).toBeNull();
    });

    it('marksOwnMessages', async () => {
      await render([fromOpponent, fromMe]);

      expect(byId('chat-message-m1')?.getAttribute('data-own')).toBe('false');
      expect(byId('chat-message-m2')?.getAttribute('data-own')).toBe('true');
    });

    it('showsMessagesAsPlainTextAndNeverAsMarkup', async () => {
      await render([
        chatMessage({ id: 'm1', text: '<img src=x data-testid="injected"> <b>fett</b>' }),
      ]);

      expect(byId('injected')).toBeNull();
      expect(root().querySelector('b')).toBeNull();
      expect(byId('chat-message-m1')?.textContent).toContain('<b>fett</b>');
    });

    it('showsNewMessagesWhenTheListChanges', async () => {
      await render([fromOpponent]);

      fixture.componentRef.setInput('messages', [fromOpponent, fromMe]);
      await fixture.whenStable();

      expect(byId('chat-message-m2')).not.toBeNull();
    });
  });

  describe('sending', () => {
    beforeEach(() => render());

    it('cannotSendWhileTheInputIsEmpty', () => {
      expect(sendButton().disabled).toBe(true);
    });

    it('cannotSendWhitespaceOnly', async () => {
      await type('   ');

      expect(sendButton().disabled).toBe(true);
    });

    it('emitsTrimmedTextWhenSendIsClicked', async () => {
      await type('  Hallo Ben ');

      await submit();

      expect(sent).toEqual(['Hallo Ben']);
    });

    it('clearsTheInputAfterSending', async () => {
      await type('Hallo');

      await submit();

      expect(input().value).toBe('');
      expect(sendButton().disabled).toBe(true);
    });

    it('sendsOnEnter', async () => {
      await type('Hallo');

      input().dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
      await fixture.whenStable();

      expect(sent).toEqual(['Hallo']);
    });

    it('doesNotSendOnEnterWhenTheInputIsEmpty', async () => {
      input().dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
      await fixture.whenStable();

      expect(sent).toEqual([]);
    });

    it('limitsTheInputToFiveHundredCharacters', () => {
      expect(input().maxLength).toBe(500);
    });
  });
});
