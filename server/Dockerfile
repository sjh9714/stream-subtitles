# Builds the server and runs it on a JRE image. The base images exist for both amd64 and arm64.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre
RUN useradd --system app
USER app
COPY --from=build /src/build/libs/*.jar /app/server.jar
ENTRYPOINT ["java", "-jar", "/app/server.jar"]
