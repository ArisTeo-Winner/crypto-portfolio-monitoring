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

# Gate Trivy: parches de seguridad del SO (openssl) que la imagen base aun no trae,
# y se retira pebble (binario Go de la base, sin paquete apt y sin uso: el
# ENTRYPOINT arranca java directamente).
RUN apt-get update \
    && apt-get upgrade -y \
    && rm -rf /var/lib/apt/lists/* \
    && rm -f /usr/bin/pebble

COPY --from=build /app/target/*.jar java-app.jar

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java -Duser.timezone=UTC -jar java-app.jar --server.port=${PORT:-8080}"]
