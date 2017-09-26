#!/bin/bash

set -e

cd /data
sudo service nginx restart
exec jupyter notebook --no-browser --NotebookApp.token=""
