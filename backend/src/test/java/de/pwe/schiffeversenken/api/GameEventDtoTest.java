package de.pwe.schiffeversenken.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.pwe.schiffeversenken.api.dto.GameEventDto;
import de.pwe.schiffeversenken.api.dto.LobbyEventDto;
import de.pwe.schiffeversenken.domain.Coordinate;
import de.pwe.schiffeversenken.domain.GamePhase;
import de.pwe.schiffeversenken.domain.Orientation;
import de.pwe.schiffeversenken.domain.RuleSet;
import de.pwe.schiffeversenken.domain.ShipPlacement;
import de.pwe.schiffeversenken.domain.ShotOutcome;
import de.pwe.schiffeversenken.domain.ShotResult;
import de.pwe.schiffeversenken.service.GameEvent;
import de.pwe.schiffeversenken.service.LobbyEvent;
import de.pwe.schiffeversenken.service.OpenLobby;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static de.pwe.schiffeversenken.domain.PlayerSide.A;
import static de.pwe.schiffeversenken.domain.PlayerSide.B;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Format der SSE-Daten: Nur gesetzte Felder erscheinen im JSON, nie Schiffspositionen außer dem versenkten Schiff. */
class GameEventDtoTest {

    private static final UUID GAME_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    private JsonNode toJson(GameEvent event) throws Exception {
        return json.readTree(json.writeValueAsString(GameEventDto.from(event)));
    }

    @Test
    void shotEventCarriesShooterTargetAndResultOnly() throws Exception {
        ShotOutcome outcome = new ShotOutcome(new Coordinate(3, 4), ShotResult.HIT, Optional.empty());

        JsonNode node = toJson(GameEvent.shot(GAME_ID, A, outcome));

        assertEquals("SHOT", node.get("type").asText());
        assertEquals(GAME_ID.toString(), node.get("gameId").asText());
        assertEquals("A", node.get("side").asText());
        assertEquals(3, node.get("x").asInt());
        assertEquals(4, node.get("y").asInt());
        assertEquals("HIT", node.get("result").asText());
        assertEquals(6, node.size(), "unerwartete Felder: " + node);
    }

    @Test
    void sunkShipIsIncludedWithItsPlacement() throws Exception {
        ShipPlacement ship = new ShipPlacement(new Coordinate(1, 2), 3, Orientation.VERTICAL);
        ShotOutcome outcome = new ShotOutcome(new Coordinate(1, 4), ShotResult.SUNK, Optional.of(ship));

        JsonNode sunkShip = toJson(GameEvent.shot(GAME_ID, B, outcome)).get("sunkShip");

        assertEquals(1, sunkShip.get("x").asInt());
        assertEquals(2, sunkShip.get("y").asInt());
        assertEquals(3, sunkShip.get("length").asInt());
        assertEquals("VERTICAL", sunkShip.get("orientation").asText());
    }

    @Test
    void playerJoinedCarriesTheNickname() throws Exception {
        JsonNode node = toJson(GameEvent.playerJoined(GAME_ID, "Bob"));

        assertEquals("PLAYER_JOINED", node.get("type").asText());
        assertEquals("Bob", node.get("nickname").asText());
        assertEquals(3, node.size(), "unerwartete Felder: " + node);
    }

    @Test
    void fleetReadyCarriesTheSide() throws Exception {
        JsonNode node = toJson(GameEvent.fleetReady(GAME_ID, B));

        assertEquals("FLEET_READY", node.get("type").asText());
        assertEquals("B", node.get("side").asText());
    }

    @Test
    void gameStartedAndTurnChangedCarryTheCurrentTurn() throws Exception {
        JsonNode started = toJson(GameEvent.gameStarted(GAME_ID, A));
        JsonNode changed = toJson(GameEvent.turnChanged(GAME_ID, B));

        assertEquals("GAME_STARTED", started.get("type").asText());
        assertEquals("A", started.get("currentTurn").asText());
        assertEquals("TURN_CHANGED", changed.get("type").asText());
        assertEquals("B", changed.get("currentTurn").asText());
    }

    @Test
    void finishedGameCarriesPhaseAndWinner() throws Exception {
        JsonNode node = toJson(GameEvent.gameFinished(GAME_ID, GamePhase.FINISHED, A));

        assertEquals("GAME_FINISHED", node.get("type").asText());
        assertEquals("FINISHED", node.get("phase").asText());
        assertEquals("A", node.get("winner").asText());
    }

    @Test
    void abandonedGameHasNoWinnerField() throws Exception {
        JsonNode node = toJson(GameEvent.gameFinished(GAME_ID, GamePhase.ABANDONED, null));

        assertEquals("ABANDONED", node.get("phase").asText());
        assertFalse(node.has("winner"));
    }

    @Test
    void openedLobbyCarriesTheLobbySummary() throws Exception {
        OpenLobby lobby = new OpenLobby(GAME_ID, "Ada", RuleSet.standard(), Instant.parse("2026-11-03T12:00:00Z"));

        JsonNode node = json.readTree(json.writeValueAsString(LobbyEventDto.from(LobbyEvent.opened(lobby))));

        assertEquals("LOBBY_OPENED", node.get("type").asText());
        assertEquals(GAME_ID.toString(), node.get("gameId").asText());
        assertEquals(GAME_ID.toString(), node.get("lobby").get("id").asText());
        assertEquals("Ada", node.get("lobby").get("creatorNickname").asText());
        assertEquals(10, node.get("lobby").get("rules").get("boardWidth").asInt());
        assertTrue(node.get("lobby").has("createdAt"));
    }

    @Test
    void closedLobbyCarriesOnlyTheGameId() throws Exception {
        JsonNode node = json.readTree(json.writeValueAsString(LobbyEventDto.from(LobbyEvent.closed(GAME_ID))));

        assertEquals("LOBBY_CLOSED", node.get("type").asText());
        assertEquals(GAME_ID.toString(), node.get("gameId").asText());
        assertFalse(node.has("lobby"));
    }
}
