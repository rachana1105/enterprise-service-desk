#!/usr/bin/env bash
# Pulls each base image once, resolves its immutable digest and prints .env lines.
# Review the output, test the stack with those digests, then paste the lines into .env.
set -euo pipefail
declare -A images=(
  [MYSQL_IMAGE]=mysql:8.4
  [MAVEN_IMAGE]=maven:3.9.9-eclipse-temurin-17
  [BACKEND_RUNTIME_IMAGE]=gcr.io/distroless/java17-debian13:nonroot
  [SECURE_RUNTIME_IMAGE]=cgr.dev/chainguard/jre:latest
  [NODE_IMAGE]=node:22-alpine
  [NGINX_IMAGE]=nginx:alpine
)
for key in MYSQL_IMAGE MAVEN_IMAGE BACKEND_RUNTIME_IMAGE SECURE_RUNTIME_IMAGE NODE_IMAGE NGINX_IMAGE; do
  ref=${images[$key]}
  docker pull -q "$ref" > /dev/null
  digest=$(docker image inspect --format '{{index .RepoDigests 0}}' "$ref")
  echo "$key=$digest"
done
