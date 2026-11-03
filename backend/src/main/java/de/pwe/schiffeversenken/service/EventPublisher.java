package de.pwe.schiffeversenken.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;

import java.util.List;
import java.util.UUID;

/**
 * Veröffentlicht Ereignisse erst nach dem erfolgreichen Commit der laufenden Transaktion. So sehen Clients beim
 * Nachladen den Zustand, zu dem das Ereignis gehört, und zurückgerollte Aktionen erzeugen keine Ereignisse.
 */
@ApplicationScoped
class EventPublisher {

    @Inject
    GameEventBus bus;

    @Inject
    TransactionSynchronizationRegistry transactions;

    void publishGameEvents(List<GameEvent> events) {
        afterCommit(() -> events.forEach(bus::publish));
    }

    void publishLobbyEvent(LobbyEvent event) {
        afterCommit(() -> bus.publish(event));
    }

    void publishLobbyClosed(UUID gameId) {
        publishLobbyEvent(LobbyEvent.closed(gameId));
    }

    private void afterCommit(Runnable action) {
        if (transactions.getTransactionStatus() == Status.STATUS_NO_TRANSACTION) {
            action.run();
            return;
        }
        transactions.registerInterposedSynchronization(new Synchronization() {
            @Override
            public void beforeCompletion() {
            }

            @Override
            public void afterCompletion(int status) {
                if (status == Status.STATUS_COMMITTED) {
                    action.run();
                }
            }
        });
    }
}
