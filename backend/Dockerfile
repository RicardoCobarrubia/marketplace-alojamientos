# Imagen del backend para el despliegue con Docker Compose (MA-93, ADR-008).
# Se construye desde la carpeta backend:  docker build -t marketplace-backend .

# ─── 1. Compilar ─────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS construccion
WORKDIR /app
# Primero el pom: si solo cambia el código, Docker reutiliza las dependencias descargadas
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp -q dependency:go-offline
COPY src src
# Las pruebas ya corren en GitHub Actions antes del merge
RUN ./mvnw -B -ntp -q package -Dmaven.test.skip=true -Djacoco.skip=true

# ─── 2. Ejecutar ─────────────────────────────────────────
FROM eclipse-temurin:21-jre
WORKDIR /app
# Sin permisos de administrador dentro del contenedor
RUN groupadd --system marketplace && useradd --system --gid marketplace marketplace
COPY --from=construccion /app/target/marketplace-*.jar app.jar
USER marketplace
EXPOSE 8080
# La memoria de Java se ajusta al límite del contenedor
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
