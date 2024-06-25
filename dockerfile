FROM openjdk:17-jdk-alpine
WORKDIR /app
COPY target/crypto-portfolio-monitoring-0.0.1-SNAPSHOT.jar java-app.jar
ENTRYPOINT ["java", "-jar","java-app.jar"]