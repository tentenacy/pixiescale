FROM gradle:8.14.3-jdk17

WORKDIR /app
COPY . /app/

RUN gradle clean build publishToMavenLocal