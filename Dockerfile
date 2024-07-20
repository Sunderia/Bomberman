# Building minestom
FROM gradle:8.8.0-jdk21 AS gradle
RUN mkdir /gradlebuild
WORKDIR /gradlebuild
COPY . .
RUN gradle shadowjar --no-daemon

FROM eclipse-temurin:21-alpine
RUN mkdir /app
WORKDIR /app
COPY --from=gradle /gradlebuild/build/libs/bomberman-all.jar .

LABEL authors="minemobs"
LABEL version="1.0.0"
EXPOSE 25565
ENTRYPOINT ["java", "-jar", "/app/bomberman-all.jar"]