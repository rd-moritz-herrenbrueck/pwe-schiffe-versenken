package de.pwe.schiffeversenken.api;

import de.pwe.schiffeversenken.persistence.PlayerEntity;
import de.pwe.schiffeversenken.persistence.PlayerRepository;
import de.pwe.schiffeversenken.service.PlayerTokens;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.UUID;

import static de.pwe.schiffeversenken.api.TestApi.as;
import static de.pwe.schiffeversenken.api.TestApi.newPlayer;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@QuarkusTest
class PlayerResourceIntegrationTest {

    @Inject
    PlayerRepository players;

    @Test
    void creatingPlayerReturnsIdNicknameAndToken() {
        given().contentType(ContentType.JSON).body(Map.of("nickname", "Ada"))
                .when().post("/api/players")
                .then().statusCode(201)
                .body("id", not(emptyOrNullString()))
                .body("nickname", is("Ada"))
                .body("token", not(emptyOrNullString()));
    }

    @Test
    void nicknameIsTrimmed() {
        given().contentType(ContentType.JSON).body(Map.of("nickname", "  Ada Lovelace  "))
                .when().post("/api/players")
                .then().statusCode(201)
                .body("nickname", is("Ada Lovelace"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "abcdefghijklmnopqrstu"})
    void invalidNicknameIsRejected(String nickname) {
        given().contentType(ContentType.JSON).body(Map.of("nickname", nickname))
                .when().post("/api/players")
                .then().statusCode(400)
                .body("code", is("INVALID_NICKNAME"));
    }

    @Test
    void missingNicknameIsRejected() {
        given().contentType(ContentType.JSON).body(Map.of())
                .when().post("/api/players")
                .then().statusCode(400)
                .body("code", is("INVALID_NICKNAME"));
    }

    @Test
    void tokenIsStoredOnlyAsHash() {
        ExtractableResponse<Response> response = given().contentType(ContentType.JSON)
                .body(Map.of("nickname", "Ada"))
                .when().post("/api/players")
                .then().statusCode(201)
                .extract();
        UUID id = UUID.fromString(response.path("id"));
        String token = response.path("token");

        PlayerEntity stored = QuarkusTransaction.requiringNew().call(() -> players.findById(id));

        assertNotEquals(token, stored.tokenHash);
        assertEquals(PlayerTokens.hash(token), stored.tokenHash);
    }

    @Test
    void everyPlayerGetsADifferentToken() {
        assertNotEquals(newPlayer("Ada"), newPlayer("Ada"));
    }

    @Test
    void requestWithoutTokenIsRejected() {
        given().when().get("/api/games")
                .then().statusCode(401);
    }

    @Test
    void requestWithUnknownTokenIsRejected() {
        as("unknown-token").when().get("/api/games")
                .then().statusCode(401);
    }

    @Test
    void requestWithValidTokenIsAccepted() {
        as(newPlayer("Ada")).when().get("/api/games")
                .then().statusCode(200);
    }
}
