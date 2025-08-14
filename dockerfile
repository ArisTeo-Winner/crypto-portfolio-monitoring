# BUILD + TEST
#FROM maven:3.9.10-eclipse-temurin-21 AS build
#WORKDIR /app
#COPY . .
#RUN mvn test -Dtest=MarketDataServiceIntegrationTest
#RUN mvn clean package -DskipTests

FROM openjdk:21-jdk-slim
WORKDIR /app
COPY target/crypto-portfolio-monitoring-0.0.1-SNAPSHOT.jar java-app.jar
ENTRYPOINT ["java", "-jar","java-app.jar"]