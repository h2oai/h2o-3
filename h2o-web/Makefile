default: 
	node_modules/.bin/gulp

clean:
	node_modules/.bin/gulp clean

watch:
	node_modules/.bin/gulp watch

check:

test:
	node_modules/.bin/gulp build-unit-test-script && node src/main/resources/www/steam/js/steam-unit-tests.js | node_modules/.bin/faucet

system-test:
	node_modules/.bin/gulp build-system-test-script && node src/main/resources/www/steam/js/steam-system-tests.js -s | node_modules/.bin/faucet 

.PHONY: default watch clean test system-test

