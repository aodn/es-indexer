# Stage 1: Build the Spring Boot application
FROM maven:latest AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline

COPY src ./src

RUN mvn clean install -DskipTests

#########################3

# Stage 2: Run the Spring Boot application
FROM amazoncorretto:17

WORKDIR /app

COPY --from=builder /app/target/es-indexer-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
