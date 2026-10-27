package de.pwe.schiffeversenken.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class HealthTest {

    @Test
    void applicationIsReadyWithDatabaseConnection() {
        given()
                .when().get("/q/health/ready")
                .then()
                .statusCode(200)
                .body("status", is("UP"))
                .body("checks.find { it.name == 'Database connections health check' }.status", is("UP"));
    }
}
