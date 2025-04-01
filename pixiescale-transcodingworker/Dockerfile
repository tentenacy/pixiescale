# Build stage
FROM gradle:8.14.3-jdk17 AS build
WORKDIR /app
COPY . /app/
RUN gradle build -x test

# Run stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

# Install FFmpeg
RUN apk add --no-cache ffmpeg

# Create directories
RUN mkdir -p /app/media/uploads /app/temp /app/output

# Set environment variables
ENV SPRING_PROFILES_ACTIVE=docker
ENV JAVA_OPTS="-Xms256m -Xmx512m"

VOLUME /app/media/uploads
VOLUME /app/output
EXPOSE 8083

ENTRYPOINT ["java", "-jar", "/app/app.jar"]