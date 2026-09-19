# syntax=docker/dockerfile:1

# ---- build stage: a JDK 17 image compiles and packages the jar -------------
# Both default images are Temurin 17; override with --build-arg when the local
# machine already has a JDK 17 image under another tag.
ARG BUILD_IMAGE=maven:3.9-eclipse-temurin-17
FROM ${BUILD_IMAGE} AS build
WORKDIR /build

# Resolve dependencies first so they cache independently of source changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

# ---- runtime stage: JRE 17 only, in-process SQLite needs no DB server ------
ARG RUNTIME_IMAGE=eclipse-temurin:17-jre
FROM ${RUNTIME_IMAGE}
WORKDIR /app
COPY --from=build /build/target/flametemp-1.0.0.jar /app/app.jar

# SQLite file lives on a mounted volume; no external database container.
ENV FLAMETEMP_DB=/data/flametemp.db
VOLUME ["/data"]
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
