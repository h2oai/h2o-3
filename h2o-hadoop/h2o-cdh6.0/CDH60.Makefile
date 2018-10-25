CDHV=6.0
#CDHV=5.14


all: build

clean:
	./gradlew clean

build: .PHONY
	./gradlew build -x test

run:
	java \
		-Djetty.request.header.size=32768 \
		-Dorg.eclipse.jetty.server.HttpConfiguration.requestHeaderSize=32768 -Dorg.eclipse.jetty.server.HttpConfiguration.responseHeaderSize=32768 -Dorg.eclipse.jetty.server.Request.maxFormContentSize=500000 \
		-jar build/h2o.jar

run-driver:
	java -jar h2o-hadoop/h2o-cdh6.0-assembly/build/libs/h2odriver.jar -n 1 -mapperXmx 2g

checkjar:
	unzip -l h2o-hadoop/h2o-cdh$(CDHV)-assembly/build/libs/h2odriver.jar | grep javax/servlet/ServletRequest
tree:
	./gradlew -q :h2o-hadoop:h2o-cdh6.0:dependencies --configuration compile > h2o-hadoop/h2o-cdh6.0/tree-compile.txt
	./gradlew -q :h2o-hadoop:h2o-cdh6.0-assembly:dependencies --configuration compile > h2o-hadoop/h2o-cdh6.0-assembly/tree-compile.txt

env:
	@echo "Run following lines in your shell:"
	export BUILD_HADOOP=true
	export H2O_TARGET=cdh$(CDHV)

run-cloud:
	docker run -it --privileged --rm --name h2o-cloud -v $(shell pwd):/h2o-3 -w /h2o-3 -v /big/:/big:ro -u 2117:`id -g` \
	    -p 5005:5005 \
	    -p 8088:8088 \
	    -p 54321:54321 \
	    -p 54322:54322 \
	    -p 54323:54323 \
	    -p 54345:54345 \
	    -p 54577:54577 \
	    --entrypoint /bin/bash docker.h2o.ai/opsh2oai/h2o-3-hadoop-cdh-$(CDHV):50 \
	    -c 'sudo -E startup.sh && bash'

exec-cloud:
	docker exec -it h2o-cloud bash

CLOUD_PORT=54345
PROXY_PORT=54577

DEBUG=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005
#DEBUG=-agentlib:jdwp=transport=dt_socket,server=n,address=127.0.0.1:5005,suspend=y,onuncaught=n

cloud-h2o:
	export HADOOP_OPTS="$(DEBUG)" && \
	hadoop jar h2o-hadoop/h2o-cdh$(CDHV)-assembly/build/libs/h2odriver.jar -mapperXmx 2g -n 1 -baseport $(CLOUD_PORT)

cloud-proxy:
	export HADOOP_OPTS="$(DEBUG)" && \
	hadoop jar h2o-hadoop/h2o-cdh$(CDHV)-assembly/build/libs/h2odriver.jar -mapperXmx 2g -n 1 -baseport $(CLOUD_PORT) \
	    -proxy -baseport $(PROXY_PORT)

curl:
	curl -v http://172.17.0.2:$(PROXY_PORT)

cloud-kill:
	# kill previous instance of h2o
	[ -f h2o_one_node ] && yarn application -kill `sed -n '/job/{s/job/application/g;p;}' h2o_one_node` || true
	# remove stale h2o_one_node file
	rm -f h2o_one_node h2odriver.*

cloud-testp: cloud-kill
	# start another h2o instance on background
	hadoop jar h2o-hadoop/h2o-cdh$(CDHV)-assembly/build/libs/h2odriver.jar \
	    -libjars "$(shell cat /opt/hive-jars/hive-libjars)" -n 1 -mapperXmx 2g -baseport $(CLOUD_PORT) \
	    -notify h2o_one_node -ea -proxy  -baseport $(PROXY_PORT)  >h2odriver.txt 2>&1 &
	# proxy mode does not support the -disown option
	# so let's wait for h2o to startup
	scripts/wait-for-h2o.sh

	# fire the tests
	. /envs/h2o_env_python2.7/bin/activate; make -f scripts/jenkins/Makefile.jenkins CLOUD_PORT=$(PROXY_PORT) CLOUD_IP=localhost test-hadoop-smoke

cloud-testh: cloud-kill
	# start another h2o instance on background
	hadoop jar h2o-hadoop/h2o-cdh$(CDHV)-assembly/build/libs/h2odriver.jar \
	    -libjars "$(shell cat /opt/hive-jars/hive-libjars)" -n 1 -mapperXmx 2g -baseport $(CLOUD_PORT) \
	    -notify h2o_one_node -ea -disown  >h2odriver.txt 2>&1 &
	scripts/wait-for-h2o.sh

	# fire the tests
	. /envs/h2o_env_python2.7/bin/activate; make -f scripts/jenkins/Makefile.jenkins CLOUD_PORT=$(CLOUD_PORT) CLOUD_IP=localhost test-hadoop-smoke

kill-gradle-daemon: # TODO find how to disable daemon
	jps
	PID=$(shell jps | sed -n '/ GradleDaemon\$/{s: .*\$::;p;}') || false
	kill $(PID)


.PHONY:
