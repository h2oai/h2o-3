FROM alpine:latest
RUN apk update && apk upgrade &&  apk add openjdk8 nodejs npm git

ENV DIRECTORIES=".config .npm .cache .local"

RUN for dir in $DIRECTORIES; do \
      mkdir -p /$dir; \
      chown -R 2117:2117 /$dir; \
    done 

CMD ["/bin/bash"]
