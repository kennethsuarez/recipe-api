FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/
# Support Windows checkouts and run the database-independent tests during the build.
RUN sed -i 's/\r$//' mvnw && sh mvnw --batch-mode --no-transfer-progress verify

FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app
COPY --from=build /workspace/target/recipe-api-0.0.1-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
