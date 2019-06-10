michalk_xgb-enable#! /bin/bash

set -e

trap finish EXIT

function finish {
    set +e
    if [ -n "${gradlewPID}" ]; then
        if ! kill ${gradlewPID} > /dev/null 2>&1; then
            echo
            echo "Could not send SIGTERM to gradlew process" >&2
        fi
    fi
    if [ -n "${testRunnerPID}" ]; then
        if ! kill ${testRunnerPID} > /dev/null 2>&1; then
            echo
            echo "Could not send SIGTERM to H2OTestRunner process" >&2
        fi
    fi
    set -e
}

./gradlew h2o-ext-xgboost:test -x h2o-ext-xgboost:testMultiNode &
gradlewPID=$!
i=1
checkPassed=
while [ ${i} -le 600 ]; do
    ((i = i + 1))
    testRunnerPID=$(jps | grep H2OTestRunner | awk '{print $1}')
    linesCount=$(echo "${testRunnerPID}" | wc -l)
    if [ ${linesCount} -eq 1 ]; then
        if [ "${testRunnerPID}" ]; then
            jps | grep H2OTestRunner
            echo
            echo "H2OTestRunner PID is ${testRunnerPID}"
            echo "Checking nvidia-smi output"
            j=1
            while [ ${j} -le 100 ]; do
                ((j = j + 1))
                nvidia-smi
                if [ "`nvidia-smi | grep java | grep ${testRunnerPID}`" ]; then
                    echo "FOUND PID ${testRunnerPID} in nvidia-smi output"
                    checkPassed='true'
                    break
                fi
                sleep 5
            done
            break
        fi
     fi
    sleep 1
done
if [ -z ${checkPassed} ]; then
    echo "Failed to find H2OTestRunner PID"
    exit 1
fi
wait ${gradlewPID}
gradlewPID=
testRunnerPID=
