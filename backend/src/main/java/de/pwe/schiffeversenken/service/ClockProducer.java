package de.pwe.schiffeversenken.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.time.Clock;

/** Zeitquelle für Zeitstempel und Inaktivitätsfristen, in Tests austauschbar. */
public class ClockProducer {

    @Produces
    @ApplicationScoped
    Clock clock() {
        return Clock.systemUTC();
    }
}
