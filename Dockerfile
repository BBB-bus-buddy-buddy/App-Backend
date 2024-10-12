# 빌드 스테이지
FROM eclipse-temurin:21-jdk AS build

# 필요한 빌드 도구 설치
RUN apt-get update && apt-get install -y \
    findutils \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY . .
RUN chmod +x ./gradlew

# Gradle 빌드 실행
RUN ./gradlew build --no-daemon --info

# 실행 스테이지
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

ARG SPRING_PROFILES_ACTIVE
ARG DATABASE_NAME
ARG MONGODB_URI
ARG OAUTH_CLIENT_ID
ARG OAUTH_SECRET_KEY
ARG UNIV_API_KEY
ARG KAKAO_REST_API_KEY
ARG JWT_SECRET

ENV SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE}
ENV DATABASE_NAME=${DATABASE_NAME}
ENV MONGODB_URI=${MONGODB_URI}
ENV OAUTH_CLIENT_ID=${OAUTH_CLIENT_ID}
ENV OAUTH_SECRET_KEY=${OAUTH_SECRET_KEY}
ENV UNIV_API_KEY=${UNIV_API_KEY}
ENV KAKAO_REST_API_KEY=${KAKAO_REST_API_KEY}
ENV JWT_SECRET=${JWT_SECRET}

ENTRYPOINT ["java", "-jar", "/app/app.jar"]