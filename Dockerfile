# ---- build ----
# Usa o wrapper, nao o maven da imagem: a versao do build e a mesma do CI e da maquina de quem
# desenvolve, definida em .mvn/wrapper/maven-wrapper.properties.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copia so o necessario para resolver dependencias primeiro. Enquanto o pom nao mudar, esta
# camada e reaproveitada do cache e o build nao rebaixa nada.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src ./src
RUN ./mvnw -B clean package -DskipTests

# ---- runtime ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Container nao roda como root.
RUN addgroup -S finup && adduser -S finup -G finup

COPY --from=build --chown=finup:finup /app/target/*.jar app.jar
USER finup

EXPOSE 8080

# MaxRAMPercentage faz a JVM respeitar o limite de memoria do container em vez de enxergar a RAM
# do host inteiro — sem isso o processo e morto por OOM sob limite de memoria.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
