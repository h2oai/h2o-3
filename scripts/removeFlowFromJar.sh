#!/bin/sh

#
# One enterprise customer asked us to disable Flow.
#
# Here is a script that replaces the Flow index.html with a message
# that says "Access Forbidden".
#

set -e
set -x

unzip h2o-3.10.3.2-cdh5.7.zip
cp h2o-3.10.3.2-cdh5.7/h2odriver.jar .
zip -d h2odriver.jar www/flow/index.html
mkdir toadd
mkdir toadd/www
mkdir toadd/www/flow
touch toadd/www/flow/index.html
echo "<html>" >> toadd/www/flow/index.html
echo "Access Forbidden" >> toadd/www/flow/index.html
echo "</html>" >> toadd/www/flow/index.html
(cd toadd; jar uf ../h2odriver.jar .)
rm -rf toadd
rm -fr h2o-3.10.3.2-cdh5.7

