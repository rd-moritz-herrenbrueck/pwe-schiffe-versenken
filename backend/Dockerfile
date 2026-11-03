# Multi-Stage-Build: Maven-Build im JDK-Image, Laufzeit im schlanken JRE-Image.
FROM eclipse-temurin:25-jdk AS build
# Ohne unzip lädt mvnw die .tar.gz statt der .zip und scheitert an distributionSha256Sum (gilt nur für die .zip).
RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B package -DskipTests

FROM eclipse-temurin:25-jre
# curl nur für den Healthcheck im Compose-Stack
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /deployments
COPY --from=build --chown=1001:0 /build/target/quarkus-app/lib/ lib/
COPY --from=build --chown=1001:0 /build/target/quarkus-app/*.jar ./
COPY --from=build --chown=1001:0 /build/target/quarkus-app/app/ app/
COPY --from=build --chown=1001:0 /build/target/quarkus-app/quarkus/ quarkus/
USER 1001
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar quarkus-run.jar"]
