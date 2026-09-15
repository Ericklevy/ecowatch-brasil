# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copia o wrapper e o pom.xml primeiro (camada de cache do Maven)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q

# Copia o código e compila (sem rodar testes — serão rodados na pipeline CI)
COPY src ./src
RUN ./mvnw package -DskipTests -q

# ── Stage 2: Run ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Usuário não-root por segurança
RUN addgroup -S ecowatch && adduser -S ecowatch -G ecowatch

COPY --from=build /app/target/*.jar app.jar
USER ecowatch

EXPOSE 8083

# Parâmetros de JVM otimizados para containers (limite de memória do container)
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Xms64m", \
  "-Xmx256m", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
