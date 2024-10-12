# 빌드 스테이지
FROM openjdk:21 AS build
WORKDIR /app
COPY . .
RUN chmod +x ./gradlew
RUN ./gradlew build --no-daemon

# 실행 스테이지
FROM openjdk:21
WORKDIR /app

# 빌드 스테이지에서 생성된 JAR 파일 복사
COPY --from=build /app/build/libs/*.jar app.jar
ARG SPRING_PROFILES_ACTIVE
ARG DATABASE_URL
ARG DATABASE_USERNAME
ARG DATABASE_PASSWORD
ARG API_KEY
# 필요한 다른 ARG들을 여기에 추가

ENV SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE}
ENV DATABASE_URL=${DATABASE_URL}
ENV DATABASE_USERNAME=${DATABASE_USERNAME}
ENV DATABASE_PASSWORD=${DATABASE_PASSWORD}
ENV API_KEY=${API_KEY}
# ARG에 대응하는 ENV 설정을 여기에 추가

ENTRYPOINT ["java","-jar","/app.jar"]