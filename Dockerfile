FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies -q > /dev/null 2>&1 || true
COPY src ./src
RUN ./gradlew --no-daemon war -q

FROM tomcat:10.1-jdk21-temurin
RUN rm -rf /usr/local/tomcat/webapps/* && mkdir -p /var/waybill/exports
COPY --from=build /src/build/libs/ROOT.war /usr/local/tomcat/webapps/ROOT.war
ENV EXPORT_DIR=/var/waybill/exports
EXPOSE 8080
