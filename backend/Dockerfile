# Gradle preinstalled (9.8.2: newest published image; wrapper uses 9.9.0) so the build does not download a distribution.
FROM gradle:9.8.2-jdk21 AS build
WORKDIR /src
COPY . .
RUN gradle bootJar --no-daemon -q

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/build/libs/*-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
