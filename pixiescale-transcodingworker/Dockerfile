# Build stage
FROM pixiescale-common:latest AS maven-repo
FROM gradle:8.14.3-jdk17 AS build
WORKDIR /app
COPY --from=maven-repo /root/.m2/repository /root/.m2/repository
COPY . /app/
RUN gradle build -x test

# NVIDIA FFmpeg 이미지 사용
FROM jrottenberg/ffmpeg:4.4-nvidia
WORKDIR /app

# OpenJDK 설치
RUN apt-get update && apt-get install -y \
    openjdk-17-jre-headless \
    && rm -rf /var/lib/apt/lists/*

# 애플리케이션 JAR 복사
COPY --from=build /app/build/libs/*.jar app.jar

# 기본 디렉토리 생성
RUN mkdir -p /app/media/uploads /app/temp /app/output

# 환경 변수
ENV SPRING_PROFILES_ACTIVE=docker
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

# 포트 노출
EXPOSE 8083

# 실행 명령
ENTRYPOINT ["java", "-jar", "/app/app.jar"]