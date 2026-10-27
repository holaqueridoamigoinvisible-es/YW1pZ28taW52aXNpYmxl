# ---- Build stage ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Install Maven
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

# Copy Maven config (settings.xml points to Maven Central)
COPY .mvn .mvn

# Download dependencies first — separate layer for caching
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Build the JAR
COPY src src
RUN mvn package -DskipTests -q

# ---- Runtime stage ----
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/amigo-invisible-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx400m", "-jar", "app.jar"]
