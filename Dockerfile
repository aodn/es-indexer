FROM amazoncorretto:17
# Need to take node on the X-APi-KEY environment varibale
WORKDIR /app
COPY ./indexer/target/indexer-*.jar /app/app.jar

ENV PROFILE='default'
EXPOSE 8080

ENTRYPOINT exec java ${JAVA_OPTS} -jar app.jar
