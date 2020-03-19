FROM ubuntu:latest

RUN apt-get update \
	&& apt-get install curl apt-utils unzip make jq gettext-base -y

RUN curl -fsSL https://get.docker.com -o get-docker.sh \
	&& sh get-docker.sh \
	&& usermod -aG docker root

RUN curl -LO https://storage.googleapis.com/kubernetes-release/release/`curl -s https://storage.googleapis.com/kubernetes-release/release/stable.txt`/bin/linux/amd64/kubectl \
	&& chmod +x ./kubectl \
	&& mv ./kubectl /usr/local/bin/kubectl

RUN curl -s https://raw.githubusercontent.com/rancher/k3d/master/install.sh | bash

CMD \
  ["/bin/bash"]
