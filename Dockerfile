# ===== ESTÁGIO 1: BUILD =====
# Imagem com Maven + JDK 21 para compilar o código
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copia só o pom.xml e baixa as dependências (aproveita cache do Maven)
COPY pom.xml .
RUN mvn -q dependency:go-offline

# Copia o código-fonte e gera o jar
COPY src ./src
RUN mvn -q clean package -DskipTests

# ===== ESTÁGIO 2: RUNTIME =====
# Imagem enxuta, só com o JRE 21 (sem Maven, sem JDK)
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copia o jar gerado no estágio anterior
COPY --from=build /app/target/adoAI-0.0.1-SNAPSHOT.jar app.jar

# Porta que a app escuta
EXPOSE 8080

# Comando executado quando o container sobe
ENTRYPOINT ["java", "-jar", "app.jar"]