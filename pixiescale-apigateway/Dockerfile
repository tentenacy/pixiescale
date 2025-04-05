# Build stage
FROM pixiescale-common:latest AS maven-repo
FROM gradle:8.14.3-jdk17 AS build
WORKDIR /app
COPY --from=maven-repo /root/.m2/repository /root/.m2/repository
COPY . /app/
RUN gradle build -x test

# Run stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

# Set environment variables
ENV SPRING_PROFILES_ACTIVE=docker
ENV JAVA_OPTS="-Xms256m -Xmx512m"

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]