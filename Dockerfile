FROM amazoncorretto:17
# Need to take node on the X-APi-KEY environment varibale
WORKDIR /app

COPY ./target/es-indexer-*.jar /app/app.jar

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
