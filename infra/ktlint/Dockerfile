FROM eclipse-temurin:17-jdk-jammy

RUN apt-get update \
  && apt-get install -y curl unzip \
  && rm -rf /var/lib/apt/lists/*

# ktlint
RUN curl -sSLO https://github.com/pinterest/ktlint/releases/latest/download/ktlint \
    && chmod +x ktlint \
    && mv ktlint /usr/local/bin/

WORKDIR /workdir
