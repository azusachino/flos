# syntax=docker/dockerfile:1

# Build stage: the full reactor is needed because Maven resolves the module tree from the root
# pom.xml before restricting the actual build to one module with -pl/-am.
FROM docker.io/library/maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY modules ./modules
RUN mvn -pl modules/netty/event-loop-lab -am -DskipTests package

# Java 17 runtime source: Debian trixie ships no openjdk-17 package (only 21), and the workstation
# shared base is Python-only. The JRE is copied from Eclipse Temurin's 17 JRE image (glibc-based,
# multi-arch), so the final image runs the same Java 17 class-file target the lab is compiled for.
FROM docker.io/library/eclipse-temurin:17-jre AS jre

# Runtime stage: the workstation shared runtime base (Python 3.14 on Debian trixie, tini, curl,
# certificates, timezone data). It provides no JVM, so the Java 17 JRE is layered on top of it.
FROM azusachino.com/media-base:py3.14-trixie-r2
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH=${JAVA_HOME}/bin:${PATH}
COPY --from=jre /opt/java/openjdk ${JAVA_HOME}
RUN java -version 2>&1 | head -1
RUN groupadd --system flos && useradd --system --gid flos flos
USER flos
WORKDIR /app
COPY --from=build --chown=flos:flos /workspace/modules/netty/event-loop-lab/target/event-loop-lab.jar app.jar
EXPOSE 9000
# The shared base has no netcat; the TCP probe uses its Python 3 interpreter instead.
HEALTHCHECK --interval=5s --timeout=3s --retries=12 \
    CMD python3 -c "import socket; socket.create_connection(('localhost', 9000), timeout=2).close()" || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
