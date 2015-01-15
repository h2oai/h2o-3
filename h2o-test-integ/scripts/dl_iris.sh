#!/bin/bash

set -x
set -e

curl -v -X POST "http://localhost:54321/3/ModelBuilders.json/deeplearning?training_frame=iris_wheader.hex&response_column=class&do_classification=true&seed=-1752585613863191601&loss=MeanSquare&replicate_training_data=false" | python -mjson.tool

