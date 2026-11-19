FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY Backend ./Backend
RUN javac Backend/StockPilotServer.java

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/Backend ./Backend
COPY Frontend ./Frontend

EXPOSE 8080

CMD ["sh", "-c", "java -cp Backend StockPilotServer ${PORT:-8080} Frontend"]
