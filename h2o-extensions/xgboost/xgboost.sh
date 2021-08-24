#!/usr/bin/env sh

java -classpath $(cat /tmp/classpath):/tmp/ water.H2OTestNodeStarter
