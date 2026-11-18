FROM eclipse-temurin:25-jdk AS build
WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:25-jre

# OCR tools the worker starts as external processes:
#   ghostscript    renders the PDF pages to images
#   tesseract-ocr  recognises the text in those images (+ the language packs listed in app.ocr.languages)
RUN apt-get update \
    && apt-get install -y --no-install-recommends ghostscript tesseract-ocr tesseract-ocr-eng tesseract-ocr-deu \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /build/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
