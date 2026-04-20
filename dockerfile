# BUILD + TEST
#FROM maven:3.9.10-eclipse-temurin-21 AS build
#WORKDIR /app
#COPY . .
#RUN mvn test -Dtest=MarketDataServiceIntegrationTest
#RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/*.jar java-app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar","java-app.jar"]
