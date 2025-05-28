# 1. Base Image with JDK and Maven
FROM maven:3.9-eclipse-temurin-23 AS builder

# 2. Set Working Directory
WORKDIR /app

# 3. Copy pom.xml and lib directory
# Copy pom.xml first to leverage Docker cache for dependencies if they were Maven central ones
COPY pom.xml .
COPY lib ./lib/

# Optional: If we had many Maven central dependencies, this would be useful:
# RUN mvn dependency:go-offline -B

# 4. Copy Source Code
COPY src ./src/

# 5. Compile with Maven
# Using -DskipTests to speed up build; tests should be run in a separate CI/CD step
RUN mvn clean package -DskipTests

# --- Second Stage: Runtime Image ---
# Use a slim OpenJDK image for a smaller footprint
FROM openjdk:23-slim

WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=builder /app/target/TP_BMO-1.0-SNAPSHOT.jar ./app.jar

# Copy the lib directory which contains runtime dependencies declared as system scope in pom.xml
# Although the shaded JAR should ideally include these, the current pom.xml's shade plugin
# might not be perfectly configured for the server part if it's focused on client.ClientApplication.
# To be safe for the server, we ensure libs are available.
# If the shaded JAR is confirmed to contain all server dependencies, this COPY ./lib can be omitted.
COPY lib ./lib/

# 6. Expose Port
EXPOSE 8080

# 7. Define the command to run the server application
# The classpath needs the main JAR and potentially all JARs in the lib directory.
# If the TP_BMO-1.0-SNAPSHOT.jar is a correctly shaded "uber-jar" for the server,
# then just "java -jar app.jar" would suffice if Main-Class in manifest was serveur.ServeurWebSocket
# However, current pom.xml seems to set client.ClientApplication as Main-Class.
# So, we explicitly set classpath and main class.
CMD ["java", "-cp", "app.jar:lib/*", "serveur.ServeurWebSocket"]
