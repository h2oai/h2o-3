B=node_modules/.bin
O=src/main/resources/www/steam

default: 
	$B/gulp

clean:
	$B/gulp clean

watch:
	$B/gulp watch

check:

launch:
	java -Dwebdev=1 -Xmx4g -jar ../h2o-app/build/libs/h2o-app.jar

test:
	$B/gulp build-unit-test-script && node $O/js/steam-unit-tests.js | $B/faucet

system-test:
	$B/gulp build-system-test-script && node $O/js/steam-system-tests.js -s | $B/faucet 

coverage:
	@mkdir -p $O/coverage
	$B/istanbul cover --dir $O/coverage -x "**/lib/**" $O/js/steam-system-tests.js

.PHONY: default watch clean test system-test coverage launch 

