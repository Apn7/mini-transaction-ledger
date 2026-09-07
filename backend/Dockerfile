# ---------- Stage 1: build ----------
# A full JDK, because compiling needs the compiler.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copy only the build files first.
# Docker caches each step, so dependencies are re-downloaded only when pom.xml changes,
# not every time a Java file is edited.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# The wrapper must be executable. Git carries the bit, but a GitHub "Download ZIP"
# strips it, so the build sets it here rather than trusting how the source arrived.
RUN chmod +x mvnw

RUN ./mvnw -B dependency:go-offline

# Now the source, then build the runnable jar.
COPY src/ src/
RUN ./mvnw -B clean package -DskipTests

# ---------- Stage 2: run ----------
# Only a JRE. No compiler, no Maven, no source code.
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Run as a normal user instead of root, so a compromised app cannot own the container.
RUN addgroup -S app && adduser -S app -G app
USER app

# Take just the finished jar out of the build stage.
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
