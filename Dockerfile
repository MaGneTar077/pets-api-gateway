FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY target/api-gateway-0.0.1-SNAPSHOT.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8090

ENTRYPOINT ["java", "-jar", "app.jar"]