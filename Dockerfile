FROM amazoncorretto:17

WORKDIR /app

COPY ./target/es-indexer-*.jar /app/app.jar

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
