FROM gradle:8.5-jdk21 AS build

WORKDIR /app

COPY build.gradle settings.gradle gradle.properties ./
COPY gradle ./gradle
COPY backend-core ./backend-core

RUN gradle :backend-core:bootJar -x test

FROM amazoncorretto:21-alpine-jdk

WORKDIR /app

COPY --from=build /app/backend-core/build/libs/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]