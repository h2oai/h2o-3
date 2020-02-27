FROM ubuntu:latest
MAINTAINER pavel@h2o.ai

EXPOSE 54321 54322

RUN apt update && \
	apt install openjdk-11-jdk -y

COPY build/libs/h2o-k8s.jar .


CMD java -jar h2o-k8s.jar
