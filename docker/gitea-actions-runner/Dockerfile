FROM catthehacker/ubuntu:act-latest

ENV DEBIAN_FRONTEND=noninteractive
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        default-mysql-client \
        jq \
        maven \
        openjdk-21-jdk \
    && rm -rf /var/lib/apt/lists/*

RUN npm install -g @playwright/test@1.59.1 \
    && mkdir -p "$PLAYWRIGHT_BROWSERS_PATH" \
    && npx playwright install --with-deps chromium \
    && chmod -R 777 "$PLAYWRIGHT_BROWSERS_PATH"
