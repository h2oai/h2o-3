#!/bin/bash

set -e

# Use 90% of RAM for H2O.
memTotalKb=`cat /proc/meminfo | grep MemTotal | sed 's/MemTotal:[ \t]*//' | sed 's/ kB//'`
memTotalMb=$[ $memTotalKb / 1024 ]
tmp=$[ $memTotalMb * 90 ]
xmxMb=$[ $tmp / 100 ]
nohup java -jar -Xmx${xmxMb}m h2o-3*/h2o.jar -port 54321 1> log/h2o.out 2> log/h2o.err &

