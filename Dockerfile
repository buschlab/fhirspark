FROM adoptopenjdk/maven-openjdk11:latest as build
LABEL maintainer="Niklas Reimer <niklas@nr205.de>"

RUN apt-get update && apt-get install git -y

ENV FHIRSPARK_HOME=/fhirspark
COPY $PWD /fhirspark
WORKDIR /fhirspark
RUN mvn -DskipTests clean package


FROM openjdk:11-jre-slim

COPY --from=build /fhirspark/target/fhirspark-*-jar-with-dependencies.jar /app.jar

CMD ["java", "-jar", "/app.jar"]