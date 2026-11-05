# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /build

COPY pom.xml .

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp dependency:go-offline

COPY src ./src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp clean verify

FROM bellsoft/liberica-openjre-debian:25-cds

WORKDIR /application

COPY --from=build /build/target/*.jar application.jar

ENTRYPOINT ["java", "-jar", "application.jar"]

LABEL org.opencontainers.image.source="https://github.com/Stock-Inc/audio-saas-backend"
