# --- Build stage ---
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q clean package -DskipTests

# --- Run stage ---
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/booking-system.jar app.jar

EXPOSE 8080
ENV JWT_SECRET=""
ENV JWT_EXPIRATION_MS=3600000

ENTRYPOINT ["java", "-jar", "app.jar"]
