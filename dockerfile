FROM maven:3.9.10-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw

COPY src src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/*.jar java-app.jar

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java -jar java-app.jar --server.port=${PORT:-8080}"]
