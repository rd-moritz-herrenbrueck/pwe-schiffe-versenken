package de.pwe.schiffeversenken.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.Map;

/**
 * Baut aus den Ereignissen des Event-Busses einen SSE-Stream. Jedes Ereignis hat als SSE-Namen seinen Typ, die Daten
 * sind JSON mit dem Feld {@code type}. Der Stream beginnt mit {@code CONNECTED} und sendet periodisch
 * {@code HEARTBEAT}.
 */
@ApplicationScoped
public class EventStreams {

    /** Ein Ereignis mit SSE-Namen und JSON-Daten. */
    public record Named(String name, Object data) {
    }

    @Inject
    ObjectMapper mapper;

    @ConfigProperty(name = "game.sse.heartbeat-interval")
    Duration heartbeatInterval;

    /**
     * {@code live} muss ein "kalter" Stream sein, der sich erst beim Abonnieren am Bus anmeldet. {@code CONNECTED}
     * steht als letzter Zweig im Merge: Es wird erst abonniert (und gesendet), nachdem {@code live} angemeldet ist,
     * danach verpasst der Client keine Ereignisse mehr.
     */
    public Multi<OutboundSseEvent> stream(Sse sse, Named connected, Multi<Named> live) {
        Multi<Named> heartbeats = Multi.createFrom().ticks().every(heartbeatInterval)
                .map(tick -> new Named("HEARTBEAT", Map.of("type", "HEARTBEAT")));
        return Multi.createBy().merging()
                .streams(live, heartbeats, Multi.createFrom().item(connected))
                .map(event -> toSse(sse, event));
    }

    private OutboundSseEvent toSse(Sse sse, Named event) {
        try {
            return sse.newEventBuilder().name(event.name()).data(mapper.writeValueAsString(event.data())).build();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Ereignis nicht serialisierbar: " + event.name(), e);
        }
    }
}
