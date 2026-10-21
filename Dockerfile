FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /build

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

FROM bellsoft/liberica-openjre-debian:25-cds

WORKDIR /application

COPY --from=build /build/target/*.jar application.jar

ENTRYPOINT ["java", "-jar", "application.jar"]