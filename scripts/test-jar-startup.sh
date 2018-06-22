#! /bin/bash

set -e

trap finish EXIT

function finish {
    set +e
    if [ -n "${h2oPID}" ]; then
        kill -9 ${h2oPID} > /dev/null 2>&1
    fi
    set -e
}

echo "Starting H2O..."
java -jar build/h2o.jar &> h2o-jar-startup.log &

h2oPID=$!
ps aux | grep java

sleep 5

echo "Checking H2O using Python client..."
python -c """import h2o
import time

for _ in range(0,10):
    try:
        h2o.connect()
        exit(0)
    except Exception:
        time.sleep(1)

print('Cannot connect to H2O')
exit(1)
"""