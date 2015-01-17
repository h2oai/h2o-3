#!/bin/bash

set -x
set -e

curl -v http://localhost:54321/ImportFiles.json?path=%2FUsers%2Ftomk%2F0xdata%2Fws%2Fh2o-dev%2Fsmalldata%2Firis%2Firis_wheader.csv | python -mjson.tool

curl -v --data-binary "hex=iris_wheader.hex&srcs=[nfs%3A%2F%2FUsers%2Ftomk%2F0xdata%2Fws%2Fh2o-dev%2Fsmalldata%2Firis%2Firis_wheader.csv]&pType=CSV&sep=44&ncols=5&singleQuotes=false&columnNames=[sepal_len,sepal_wid,petal_len,petal_wid,class]&checkHeader=1&delete_on_done=true&blocking=true" http://localhost:54321/Parse.json | python -mjson.tool

