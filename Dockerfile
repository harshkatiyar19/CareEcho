# Use OpenJDK 17 as base image
FROM openjdk:17-jdk-slim

# Set working directory
WORKDIR /app

# Copy Maven wrapper and pom.xml (if using Maven)
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Make Maven wrapper executable
RUN chmod +x ./mvnw

# Download dependencies (this layer will be cached if pom.xml doesn't change)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN ./mvnw clean package -DskipTests

# Create a non-root user for security
RUN addgroup --system spring && adduser --system spring --ingroup spring

# Copy the built JAR file
RUN cp target/*.jar app.jar

# Change ownership of the app
RUN chown spring:spring app.jar

USER spring:spring

# Expose the port your Spring Boot app runs on (default is 8080)
EXPOSE 8080

# Set JVM options for better container performance
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Health check (optional)
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]