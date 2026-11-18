# Etapa 1: compilar con JDK 25
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -q dependency:go-offline
COPY src src
RUN ./mvnw -q package -DskipTests

# Etapa 2: ejecutar solo con JRE 25
FROM eclipse-temurin:25-jre
RUN useradd --system --no-create-home turnos
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
USER turnos
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]