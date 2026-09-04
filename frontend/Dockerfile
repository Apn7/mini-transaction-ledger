# ---------- Stage 1: build ----------
# Node is needed to compile Angular into plain HTML, CSS and JavaScript.
FROM node:22-alpine AS build
WORKDIR /app

# Copy the dependency files first, so npm install is only repeated when they change.
COPY package.json package-lock.json ./
RUN npm ci

# Now the source, then produce the optimised bundle.
COPY . .
RUN npm run build

# ---------- Stage 2: run ----------
# nginx serves static files. No Node, no source code, no node_modules.
FROM nginx:1.27-alpine AS runtime

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html

EXPOSE 80
