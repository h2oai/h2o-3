#!/bin/bash

memTotalKb=`cat /proc/meminfo | grep MemTotal | sed 's/MemTotal:[ \t]*//' | sed 's/ kB//'`
memTotalMb=$[ $memTotalKb / 1024 ]
tmp=$[ $memTotalMb * 70 ]
xmxMb=$[ $tmp / 100 ]

/usr/bin/java -Xmx${xmxMb}m -jar h2o.jar -name sagemakerCloud
