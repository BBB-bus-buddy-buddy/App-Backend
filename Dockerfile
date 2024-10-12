FROM openjdk:21
ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} app.jar
COPY src/main/resources/env.properties /app/env.properties
ENTRYPOINT ["java","-jar","/app.jar"]