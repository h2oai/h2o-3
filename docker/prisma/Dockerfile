FROM alpine:latest
RUN apk update && apk upgrade &&  apk add openjdk8 nodejs npm git curl

ENV DIRECTORIES=".config .npm .cache .local"

RUN for dir in $DIRECTORIES; do \
      mkdir -p /$dir; \
      chown -R 2117:2117 /$dir; \
    done 

RUN curl -sfL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/install.sh | sh -s -- -b /usr/local/bin

CMD ["/bin/bash"]
