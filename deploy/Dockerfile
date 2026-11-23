FROM eclipse-temurin:17-jre
WORKDIR /app
ARG JAR
COPY ${JAR} /app/app.jar
USER 10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
