#!/bin/bash

H2O_DIR="../../../../../.."
cd $H2O_DIR
GRADLE="./gradlew"

$GRADLE clean
NOHUP_DIR="./h2o-algos/build/jmh/nohup"
NOHUP_FILE="$NOHUP_DIR/$(date +%Y-%m-%d).out"
mkdir $NOHUP_DIR -p
BENCH_CMD="nohup $GRADLE :h2o-algos:jmh >$NOHUP_FILE"
$BENCH_CMD
