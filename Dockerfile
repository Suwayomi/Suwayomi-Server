FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the built jar
COPY server/build/*.jar /app/suwayomi-server.jar

# Create data directory
RUN mkdir -p /data

ENV JAVA_OPTS=""

EXPOSE 4567

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/suwayomi-server.jar"]
