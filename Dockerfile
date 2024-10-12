# 빌드 스테이지
FROM openjdk:21-jdk-alpine AS build

# 필요한 빌드 도구 설치
RUN apk add --no-cache findutils

WORKDIR /app
COPY . .
RUN chmod +x ./gradlew

# Gradle 빌드 실행
RUN ./gradlew build --no-daemon --info

# 실행 스테이지
FROM openjdk:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

# 런타임에 환경 변수를 주입받도록 설정
ENTRYPOINT ["java", "-jar", "/app/app.jar"]