# =============================================================================
# FinTrack -- multi-stage build.
#
# Stage 1 compiles with a full JDK and Maven; stage 2 ships only the jar on a JRE.
# The build tooling never reaches the final image, which removes both the weight
# and the attack surface of a compiler and a package manager in production.
# =============================================================================

# --- Stage 1: build ----------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# The POM is copied on its own first so that the dependency download lands in its
# own layer. Docker then reuses that layer on every rebuild where only source
# changed, which is nearly all of them -- otherwise each edit re-downloads the
# whole dependency tree.
COPY pom.xml ./
RUN mvn -B dependency:go-offline

COPY src ./src

# Tests are skipped here by design. They run in CI against a controlled database;
# repeating them inside the image build would make deployment depend on test
# infrastructure being reachable from the build host.
RUN mvn -B clean package -DskipTests

# --- Stage 2: runtime --------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

# A dedicated unprivileged user. A container process running as root that escapes
# its namespace is root on the host; this one is nobody in particular.
RUN addgroup -S fintrack && adduser -S fintrack -G fintrack

WORKDIR /app

COPY --from=builder --chown=fintrack:fintrack /build/target/*.jar app.jar

USER fintrack

EXPOSE 8080

# Container-aware defaults: the JVM reads the cgroup memory limit rather than the
# host's total RAM, which is what stops it sizing a heap the container cannot have
# and being OOM-killed under load.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"

# Uses the actuator health endpoint, which reports DOWN when the database is
# unreachable -- so an orchestrator sees a broken dependency, not just a live port.
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Exec form via sh so that JAVA_OPTS is expanded; without the shell the variable
# would be passed to the JVM as a single literal argument.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
