FROM eclipse-temurin:21-jre

WORKDIR /app
COPY target/kma-mini-server-*.jar app.jar
RUN mkdir -p /app/upload/knowledge /app/logs

EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
