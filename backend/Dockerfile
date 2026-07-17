# Multi-stage build for Spring Boot backend
FROM eclipse-temurin:17-jdk AS build

# Set working directory
WORKDIR /app

# Copy maven files first (for dependency caching)
COPY pom.xml ./
COPY mvnw ./
COPY .mvn ./.mvn

# Download dependencies (cached if pom.xml hasn't changed)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN ./mvnw package -Dmaven.test.skip=true

# Runtime stage
FROM eclipse-temurin:17-jre

WORKDIR /app

# Create non-root user for security
RUN groupadd -r appgroup && \
    useradd -r -g appgroup appuser

# Copy the built JAR
COPY --from=build /app/target/*.jar app.jar

# Set ownership
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "-Xms256m", "-Xmx512m", "app.jar"]