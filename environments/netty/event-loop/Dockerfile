# syntax=docker/dockerfile:1

# Build stage: the full reactor is needed because Maven resolves the module tree from the root
# pom.xml before restricting the actual build to one module with -pl/-am.
FROM docker.io/library/maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY modules ./modules
RUN mvn -pl modules/netty/event-loop-lab -am -DskipTests package

# Runtime stage: a minimal JRE, not the JDK used to build. No compiler, no Maven, no build cache
# ships in the image that actually runs. The Alpine JRE tag only publishes an amd64 variant; this
# Debian-based tag is genuinely multi-arch (amd64 and arm64), so it also runs on Apple Silicon
# dev machines without emulation.
FROM docker.io/library/eclipse-temurin:17-jre
RUN groupadd --system flos && useradd --system --gid flos flos
USER flos
WORKDIR /app
COPY --from=build --chown=flos:flos /workspace/modules/netty/event-loop-lab/target/event-loop-lab.jar app.jar
EXPOSE 9000
HEALTHCHECK --interval=5s --timeout=3s --retries=12 \
    CMD bash -c 'exec 3<>/dev/tcp/localhost/9000' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
