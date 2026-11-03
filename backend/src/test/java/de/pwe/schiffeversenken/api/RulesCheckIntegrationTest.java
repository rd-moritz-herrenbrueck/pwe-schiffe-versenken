package de.pwe.schiffeversenken.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static de.pwe.schiffeversenken.api.TestApi.as;
import static de.pwe.schiffeversenken.api.TestApi.newPlayer;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.nullValue;

/**
 * Regelprüfung für das UI: {@code POST /api/rules/check} mit einem {@code RuleSet} liefert immer {@code 200} und
 * {@code {valid: true}} bzw. {@code {valid: false, code, message}} (Codes wie beim Erstellen einer Lobby). Der Client
 * lässt so nur Regeln zu, die der Server akzeptiert, ohne die Platzierungsprüfung nachzubauen.
 */
@QuarkusTest
class RulesCheckIntegrationTest {

    private static Map<String, Object> rules(int width, int height, List<Integer> fleet, boolean mayTouch) {
        return Map.of("boardWidth", width, "boardHeight", height, "fleet", fleet,
                "shootAgainOnHit", false, "shipsMayTouch", mayTouch);
    }

    static Stream<Arguments> validRules() {
        return Stream.of(
                Arguments.of("Standard", rules(10, 10, List.of(5, 4, 3, 2), false)),
                Arguments.of("kleines Feld", rules(4, 4, List.of(2), false)),
                Arguments.of("nicht quadratisch", rules(12, 6, List.of(5, 4, 3), false)),
                Arguments.of("dichte Flotte mit Berührung", rules(4, 4, List.of(3, 3, 3), true)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validRules")
    void placeableRulesAreValid(String description, Map<String, Object> rules) {
        as(newPlayer("Ada")).body(rules)
                .when().post("/api/rules/check")
                .then().statusCode(200)
                .body("valid", is(true))
                .body("code", nullValue());
    }

    static Stream<Arguments> invalidRules() {
        return Stream.of(
                Arguments.of("Feld zu breit", "INVALID_RULE_SET", rules(26, 10, List.of(5, 4, 3, 2), false)),
                Arguments.of("Feld ohne Höhe", "INVALID_RULE_SET", rules(10, 0, List.of(2), false)),
                Arguments.of("Schiff zu kurz", "INVALID_RULE_SET", rules(10, 10, List.of(1), false)),
                Arguments.of("Flotte leer", "INVALID_RULE_SET", rules(10, 10, List.of(), false)),
                Arguments.of("Flotte passt nicht", "FLEET_DOES_NOT_FIT", rules(4, 4, List.of(3, 3, 3), false)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRules")
    void invalidRulesAreReportedWithCodeAndMessage(String description, String code, Map<String, Object> rules) {
        as(newPlayer("Ada")).body(rules)
                .when().post("/api/rules/check")
                .then().statusCode(200)
                .body("valid", is(false))
                .body("code", is(code))
                .body("message", not(emptyOrNullString()));
    }

    @Test
    void checkDependsOnTheTouchingRule() {
        String token = newPlayer("Ada");
        List<Integer> fleet = List.of(3, 3, 3);

        as(token).body(rules(4, 4, fleet, true)).when().post("/api/rules/check")
                .then().statusCode(200).body("valid", is(true));
        as(token).body(rules(4, 4, fleet, false)).when().post("/api/rules/check")
                .then().statusCode(200).body("valid", is(false));
    }

    @Test
    void checkDoesNotCreateAGame() {
        String token = newPlayer("Ada");

        as(token).body(rules(10, 10, List.of(5, 4, 3, 2), false)).when().post("/api/rules/check")
                .then().statusCode(200);

        as(token).when().get("/api/games").then().statusCode(200).body("size()", is(0));
    }

    @Test
    void requestWithoutBodyIsRejected() {
        as(newPlayer("Ada"))
                .when().post("/api/rules/check")
                .then().statusCode(400).body("code", is("INVALID_REQUEST"));
    }

    @Test
    void checkRequiresToken() {
        given().contentType(ContentType.JSON).body(rules(10, 10, List.of(5, 4, 3, 2), false))
                .when().post("/api/rules/check")
                .then().statusCode(401);
    }
}
