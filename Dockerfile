# Stage 1: Build the Spring Boot application
# FROM maven:latest AS builder
# WORKDIR /app
# COPY pom.xml .
# RUN mvn dependency:go-offline

# COPY src ./src

# RUN mvn clean install -DskipTests

#########################

# Stage 2: Run the Spring Boot application
FROM amazoncorretto:17
VOLUME /tmp
#WORKDIR /app

COPY  /target/es-indexer-0.0.1-SNAPSHOT.jar app.jar

#EXPOSE 8080

#CMD ["java", "-jar", "app.jar"]
ENTRYPOINT ["java","-Dserver.port=${PORT}","-Delasticsearch.serverUrl=${ELASTIC_URL}","-Delasticsearch.apiKey=${ELASTIC_KEY}","-jar","/app.jar"]
