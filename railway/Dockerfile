FROM node:20-alpine

RUN apk add --no-cache \
    ffmpeg \
    git \
    python3 \
    make \
    g++ \
    ca-certificates \
    tzdata

WORKDIR /app

COPY package.json ./
RUN npm install --omit=dev --no-audit --no-fund

COPY . .

RUN mkdir -p /app/data/sessions /app/tmp

ENV NODE_ENV=production
ENV PORT=3000

EXPOSE 3000

CMD ["node", "server.js"]
