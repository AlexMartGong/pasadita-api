FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring

COPY target/pasadita-api-*.jar app.jar

RUN chown -R spring:spring /app

USER spring:spring

EXPOSE 8080

# shortcut: -XX:-UseBASE64Intrinsics mitigates Java4Shell (CVE-2026-114938) while the Temurin image ships 21.0.13+9;
# remove it once `java -XshowSettings:properties -version` in this base image reports java.runtime.version >= 21.0.13.1
ENTRYPOINT ["java", "-XX:-UseBASE64Intrinsics", "-jar", "app.jar", "--spring.profiles.active=prod"]
