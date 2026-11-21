FROM alpine:latest

RUN apk update && apk upgrade && apk add ca-certificates openjdk8 nodejs npm git bash \
    && update-ca-certificates \
    && JAVA_BIN="$(readlink -f "$(command -v java)")" \
    && JAVA_HOME="$(cd "$(dirname "$JAVA_BIN")/../.." && pwd)" \
    && KEYTOOL="$JAVA_HOME/bin/keytool" \
    && CACERTS="$JAVA_HOME/jre/lib/security/cacerts" \
    && test -x "$JAVA_BIN" \
    && test -x "$KEYTOOL" \
    && test -r "$CACERTS" \
    && listing="$("$KEYTOOL" -list -keystore "$CACERTS" -storepass changeit)" \
    && for alias in 'emsignrootcag1 [jdk]' 'emsignrootcag2 [jdk]' 'emsigneccrootcag3 [jdk]' 'comodoaaaca [jdk]'; do \
         if printf '%s\n' "$listing" | grep -Fq -- "$alias"; then echo "Compromised CA alias remains in $CACERTS: $alias" >&2; exit 1; fi; \
       done

ENV DIRECTORIES=".config .npm .cache .local"

RUN for dir in $DIRECTORIES; do \
      mkdir -p "/$dir"; \
      chown -R 2117:2117 "/$dir"; \
    done

CMD ["/bin/sh"]
