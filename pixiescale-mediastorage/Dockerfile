# Build stage
FROM gradle:8.14.3-jdk17 AS build
WORKDIR /app
COPY . /app/
RUN gradle build -x test

# Run stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

# Create directories
RUN mkdir -p /app/media/transcoded

# Set environment variables
ENV SPRING_PROFILES_ACTIVE=docker
ENV JAVA_OPTS="-Xms256m -Xmx512m"

VOLUME /app/media/transcoded
EXPOSE 8084

ENTRYPOINT ["java", "-jar", "/app/app.jar"]