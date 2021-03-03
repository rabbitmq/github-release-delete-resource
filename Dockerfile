FROM ghcr.io/graalvm/graalvm-ce:java11-21.0.0.2 as builder

COPY target/github-release-delete-resource.jar .

RUN gu install native-image

RUN native-image -jar github-release-delete-resource.jar \
		-H:Features="com.rabbitmq.concourse.NativeImageFeature" \
		--no-fallback --enable-url-protocols=https

FROM ubuntu:20.04

RUN set -eux; \
	\
	apt-get update; \
	apt-get install --yes --no-install-recommends \
		ca-certificates

COPY --from=builder github-release-delete-resource /opt/resource/
COPY scripts/* /opt/resource/