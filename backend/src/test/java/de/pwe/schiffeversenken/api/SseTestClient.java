package de.pwe.schiffeversenken.api;

import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Minimaler SSE-Client für Integrationstests. Liest den Stream in einem eigenen Thread und stellt die Ereignisse in
 * eine Warteschlange. {@link #next()} überspringt {@code HEARTBEAT}, damit Tests nicht vom Zeitablauf abhängen.
 * Warten gibt es nur auf erwartete Ereignisse, nicht erwartete werden über die Reihenfolge ausgeschlossen.
 */
public final class SseTestClient implements AutoCloseable {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final Event CLOSED = new Event("<closed>", "");

    /** Ein Ereignis: Name aus {@code event:}, Daten aus {@code data:} (JSON). */
    public record Event(String name, String data) {
        public JsonPath json() {
            return JsonPath.from(data);
        }
    }

    private final BlockingQueue<Event> events = new LinkedBlockingQueue<>();
    private final HttpResponse<InputStream> response;
    private final String errorBody;
    private final Thread reader;

    private SseTestClient(HttpResponse<InputStream> response) throws IOException {
        this.response = response;
        if (response.statusCode() == 200) {
            errorBody = null;
            reader = new Thread(this::readEvents, "sse-test-client");
            reader.setDaemon(true);
            reader.start();
        } else {
            errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            reader = null;
        }
    }

    /** Verbindet mit {@code Authorization}-Header, ohne Ereignisse zu lesen. {@code token} darf {@code null} sein. */
    public static SseTestClient connect(String path, String token) {
        return send(path, token, null);
    }

    /** Verbindet mit dem Token als Query-Parameter {@code access_token} (Browser-{@code EventSource}). */
    public static SseTestClient connectWithQueryToken(String path, String token) {
        return send(path, null, token);
    }

    /** Verbindet, erwartet Status 200 und das einleitende {@code CONNECTED}-Ereignis. */
    public static SseTestClient open(String path, String token) {
        SseTestClient client = connect(path, token);
        assertEquals(200, client.status(), "Verbindung abgelehnt: " + client.errorBody);
        assertEquals("CONNECTED", client.next().name());
        return client;
    }

    private static SseTestClient send(String path, String headerToken, String queryToken) {
        String query = queryToken == null ? "" : "?access_token=" + queryToken;
        HttpRequest.Builder request = HttpRequest.newBuilder(
                        URI.create(RestAssured.baseURI + ":" + RestAssured.port + path + query))
                .header("Accept", "text/event-stream")
                .GET();
        if (headerToken != null) {
            request.header("Authorization", "Bearer " + headerToken);
        }
        try {
            return new SseTestClient(HttpClient.newHttpClient()
                    .send(request.build(), HttpResponse.BodyHandlers.ofInputStream()));
        } catch (IOException e) {
            throw new AssertionError("Verbindung fehlgeschlagen", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Verbindung unterbrochen", e);
        }
    }

    public int status() {
        return response.statusCode();
    }

    public String contentType() {
        return response.headers().firstValue("Content-Type").orElse("");
    }

    /** Fehlercode ({@code code}) der JSON-Fehlerantwort bei einem Status ungleich 200. */
    public String errorCode() {
        return errorBody == null || errorBody.isBlank() ? null : JsonPath.from(errorBody).getString("code");
    }

    /** Nächstes Ereignis ohne {@code HEARTBEAT}. Schlägt nach 5 s ohne Ereignis oder bei geschlossenem Stream fehl. */
    public Event next() {
        Event event;
        do {
            event = nextIncludingHeartbeats();
        } while (event.name().equals("HEARTBEAT"));
        return event;
    }

    public List<Event> next(int count) {
        List<Event> received = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            received.add(next());
        }
        return received;
    }

    /** Wartet auf das nächste {@code HEARTBEAT}-Ereignis. */
    public Event nextHeartbeat() {
        Event event;
        do {
            event = nextIncludingHeartbeats();
        } while (!event.name().equals("HEARTBEAT"));
        return event;
    }

    private Event nextIncludingHeartbeats() {
        try {
            Event event = events.poll(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (event == null) {
                return fail("Kein Ereignis innerhalb von " + TIMEOUT);
            }
            if (event == CLOSED) {
                return fail("Stream wurde geschlossen");
            }
            return event;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fail("Warten unterbrochen");
        }
    }

    private void readEvents() {
        try (BufferedReader lines = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String name = "message";
            StringBuilder data = new StringBuilder();
            String line;
            while ((line = lines.readLine()) != null) {
                if (line.isEmpty()) {
                    if (data.length() > 0) {
                        events.add(new Event(name, data.toString()));
                    }
                    name = "message";
                    data.setLength(0);
                } else if (line.startsWith("event:")) {
                    name = line.substring("event:".length()).trim();
                } else if (line.startsWith("data:")) {
                    if (data.length() > 0) {
                        data.append('\n');
                    }
                    data.append(line.substring("data:".length()).stripLeading());
                }
            }
        } catch (IOException e) {
            // Verbindung beendet, z. B. durch close()
        } finally {
            events.add(CLOSED);
        }
    }

    @Override
    public void close() {
        try {
            response.body().close();
        } catch (IOException e) {
            // schon geschlossen
        }
    }
}
