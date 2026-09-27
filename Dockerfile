# ---- build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B package -DskipTests

# ---- runtime ----
FROM eclipse-temurin:21-jre
# Native Tesseract for the local OCR pipeline (Tess4J loads libtesseract via JNA)
RUN apt-get update \
 && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-eng wget \
 && rm -rf /var/lib/apt/lists/*
RUN useradd --system --create-home --uid 10001 appuser
WORKDIR /app
COPY --from=build /src/target/label-verification-*.jar app.jar
RUN mkdir -p /app/data/uploads && chown -R appuser /app
USER appuser

# MALLOC_ARENA_MAX: caps glibc per-thread malloc arenas (native memory) in small containers.
# OMP_THREAD_LIMIT: Tesseract's OpenMP threads multiply memory use; one is enough for label images.
# JAVA_OPTS: sized for a 512 MB container (e.g. Railway free plan). On larger hosts override it,
#            e.g. JAVA_OPTS="-XX:MaxRAMPercentage=75".
ENV APP_STORAGE_DIR=/app/data/uploads \
    MALLOC_ARENA_MAX=2 \
    OMP_THREAD_LIMIT=1 \
    JAVA_OPTS="-Xms64m -Xmx256m -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=48m -XX:MaxDirectMemorySize=32m -Xss512k -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s CMD wget -qO- http://localhost:${PORT:-8080}/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
