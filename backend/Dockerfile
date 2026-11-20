FROM eclipse-temurin:21-jdk AS build
WORKDIR /src

# Resolve dependencies in their own layer so code changes don't re-download them.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q -DskipTests package && cp target/greenhour-backend-*.jar app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 greenhour
COPY --from=build /src/app.jar app.jar
USER greenhour
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
