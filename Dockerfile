# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

# Copiar Maven Wrapper e pom.xml primeiro (cache de dependências)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Dar permissão de execução ao wrapper
RUN chmod +x mvnw

# Baixar dependências (camada cacheável)
RUN ./mvnw dependency:go-offline -B

# Copiar código-fonte
COPY src/ src/

# Build do JAR (sem testes para acelerar o build da imagem)
RUN ./mvnw package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Criar usuário não-root para segurança
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copiar JAR do stage de build
COPY --from=build /app/target/*.jar app.jar

# Usar usuário não-root
USER appuser

# Porta da aplicação
EXPOSE 5000

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:5000/v3/api-docs || exit 1

# Entrypoint com flags de otimização para containers
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
