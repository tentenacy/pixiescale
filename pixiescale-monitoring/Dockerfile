FROM pixiescale-common:latest AS maven-repo
FROM gradle:8.14.3-jdk17 AS build
WORKDIR /app
COPY --from=maven-repo /root/.m2/repository /root/.m2/repository
COPY . /app/
RUN gradle build -x test

# 실행 단계
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

# 환경 변수 설정
ENV SPRING_PROFILES_ACTIVE=docker
ENV JAVA_OPTS="-Xms256m -Xmx512m"

EXPOSE 8085

ENTRYPOINT ["java", "-jar", "/app/app.jar"]