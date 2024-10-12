# 빌드 스테이지
FROM openjdk:21 AS build
WORKDIR /app
COPY . .
RUN chmod +x ./gradlew
# Gradle 디버그 모드 활성화
RUN ./gradlew build --no-daemon --info

# 실행 스테이지
FROM openjdk:21
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

# 런타임에 환경 변수를 주입받도록 설정
ENTRYPOINT ["java", "-jar", "/app/app.jar"]