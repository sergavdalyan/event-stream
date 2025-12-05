# Build stage
FROM maven:3.9-amazoncorretto-17 AS build

WORKDIR /app

# Copy pom.xml and download dependencies
COPY pom.xml ./
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# Runtime stage
FROM amazoncorretto:17

WORKDIR /app

# Copy the built artifact from build stage
COPY --from=build /app/target/*.jar app.jar

# Expose port
EXPOSE 8088

# Set environment variables
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

