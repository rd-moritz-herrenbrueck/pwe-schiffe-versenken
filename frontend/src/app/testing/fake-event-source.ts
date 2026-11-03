import { Provider } from '@angular/core';
import { EVENT_SOURCE_FACTORY } from '../core/event.service';

/**
 * Ersatz für `EventSource` in Tests. Unterstützt genau das, was der `EventService` nutzen darf:
 * `addEventListener` (benannte Ereignisse und `error`), `readyState`, `url` und `close()`.
 */
export class FakeEventSource {
  static readonly CONNECTING = 0;
  static readonly OPEN = 1;
  static readonly CLOSED = 2;

  readyState = FakeEventSource.OPEN;
  closed = false;
  private readonly listeners = new Map<string, ((event: Event) => void)[]>();

  constructor(readonly url: string) {}

  addEventListener(name: string, listener: (event: Event) => void): void {
    this.listeners.set(name, [...(this.listeners.get(name) ?? []), listener]);
  }

  removeEventListener(name: string, listener: (event: Event) => void): void {
    this.listeners.set(
      name,
      (this.listeners.get(name) ?? []).filter((l) => l !== listener),
    );
  }

  close(): void {
    this.closed = true;
    this.readyState = FakeEventSource.CLOSED;
  }

  /** Das Backend sendet ein benanntes Ereignis mit JSON-Daten (Feld `type` gleich dem Namen). */
  emit(type: string, data: Record<string, unknown> = {}): void {
    const event = new MessageEvent(type, { data: JSON.stringify({ type, ...data }) });
    (this.listeners.get(type) ?? []).forEach((listener) => listener(event));
  }

  /** Verbindungsfehler. Mit `fatal` hat der Browser die Verbindung endgültig geschlossen. */
  fail(fatal = false): void {
    this.readyState = fatal ? FakeEventSource.CLOSED : FakeEventSource.CONNECTING;
    (this.listeners.get('error') ?? []).forEach((listener) => listener(new Event('error')));
  }
}

export interface FakeEventSources {
  readonly created: FakeEventSource[];
  /** Zuletzt erzeugte Verbindung. */
  latest(): FakeEventSource;
  /** Noch offene (nicht geschlossene) Verbindungen. */
  open(): FakeEventSource[];
  providers: Provider[];
}

export function fakeEventSources(): FakeEventSources {
  const created: FakeEventSource[] = [];
  return {
    created,
    latest: () => created[created.length - 1],
    open: () => created.filter((source) => !source.closed),
    providers: [
      {
        provide: EVENT_SOURCE_FACTORY,
        useValue: (url: string) => {
          const source = new FakeEventSource(url);
          created.push(source);
          return source as unknown as EventSource;
        },
      },
    ],
  };
}
