BUILD=build
DIST=$(BUILD)/dist
DEPLOY=../h2o-core/src/main/resources/www/steam
LIB=lib
MODULES=node_modules
BIN=$(MODULES)/.bin

BUILD_DIRS=\
	$(BUILD)/scripts \
	$(BUILD)/tests \
	$(BUILD)/coverage \
	$(BUILD)/doc \
	$(BUILD)/spec \
	$(DIST) \
	$(DEPLOY)

# Common javascript files that go into both the browser and node targets.
COMMON_SCRIPTS=\
	$(BUILD)/scripts/prelude.global.js \
	$(BUILD)/scripts/typedef.global.js \
	$(BUILD)/scripts/hypergraph.global.js \
	$(BUILD)/scripts/geyser.global.js \
	$(BUILD)/scripts/util.global.js

# Javascript files that go into the browser target.
BROWSER_SCRIPTS=\
	$(BUILD)/scripts/knockout.js \
	$(BUILD)/scripts/application.js \
	$(BUILD)/scripts/application-context.js \
	$(BUILD)/scripts/timers.js \
	$(BUILD)/scripts/router.js \
	$(BUILD)/scripts/routes.js \
	$(BUILD)/scripts/error-monitor.js \
	$(BUILD)/scripts/event-log.js \
	$(BUILD)/scripts/cache.js \
	$(BUILD)/scripts/localStorage.js \
	$(BUILD)/scripts/xhr.js \
	$(BUILD)/scripts/h2o-proxy.js \
	$(BUILD)/scripts/help-server.js \
	$(BUILD)/scripts/help.js \
	$(BUILD)/scripts/dialog-manager.js \
	$(BUILD)/scripts/alert-dialog.js \
	$(BUILD)/scripts/error-dialog.js \
	$(BUILD)/scripts/confirm-dialog.js \
	$(BUILD)/scripts/select-frame-dialog.js \
	$(BUILD)/scripts/configure-strip-plot-dialog.js \
	$(BUILD)/scripts/import-files-dialog.js \
	$(BUILD)/scripts/parse-files-dialog.js \
	$(BUILD)/scripts/topic-list-view.js \
	$(BUILD)/scripts/file-list-view.js \
	$(BUILD)/scripts/frame-list-view.js \
	$(BUILD)/scripts/model-list-view.js \
	$(BUILD)/scripts/scoring-list-view.js \
	$(BUILD)/scripts/notification-list-view.js \
	$(BUILD)/scripts/file-view.js \
	$(BUILD)/scripts/frame-view.js \
	$(BUILD)/scripts/model-view.js \
	$(BUILD)/scripts/frame-inspection-view.js \
	$(BUILD)/scripts/model-inspection-view.js \
	$(BUILD)/scripts/model-selection-view.js \
	$(BUILD)/scripts/scoring-view.js \
	$(BUILD)/scripts/scoring-sheet-view.js \
	$(BUILD)/scripts/notification-view.js \
	$(BUILD)/scripts/scoring-selection-view.js \
	$(BUILD)/scripts/main-view.js \
	$(BUILD)/scripts/steam.js

# Javascript files that go into the node target.
# All test scripts go at the bottom of this list.
TEST_SCRIPTS=\
	$(BUILD)/scripts/application-context.js \
	$(BUILD)/scripts/xhr.js \
	$(BUILD)/scripts/h2o-proxy.js \
	$(BUILD)/scripts/help-server.js \
	$(BUILD)/scripts/help.js \
	$(BUILD)/scripts/alert-dialog.js \
	$(BUILD)/scripts/error-dialog.js \
	$(BUILD)/scripts/confirm-dialog.js \
	$(BUILD)/scripts/select-frame-dialog.js \
	$(BUILD)/scripts/configure-strip-plot-dialog.js \
	$(BUILD)/scripts/import-files-dialog.js \
	$(BUILD)/scripts/parse-files-dialog.js \
	$(BUILD)/scripts/topic-list-view.js \
	$(BUILD)/scripts/file-list-view.js \
	$(BUILD)/scripts/frame-list-view.js \
	$(BUILD)/scripts/model-list-view.js \
	$(BUILD)/scripts/scoring-list-view.js \
	$(BUILD)/scripts/notification-list-view.js \
	$(BUILD)/scripts/file-view.js \
	$(BUILD)/scripts/frame-view.js \
	$(BUILD)/scripts/model-view.js \
	$(BUILD)/scripts/frame-inspection-view.js \
	$(BUILD)/scripts/model-inspection-view.js \
	$(BUILD)/scripts/model-selection-view.js \
	$(BUILD)/scripts/scoring-view.js \
	$(BUILD)/scripts/scoring-sheet-view.js \
	$(BUILD)/scripts/notification-view.js \
	$(BUILD)/scripts/scoring-selection-view.js \
	$(BUILD)/scripts/main-view.js \
	$(BUILD)/tests/test.global.js \
	$(BUILD)/tests/prelude.tests.js \
	$(BUILD)/tests/typedef.tests.js \
	$(BUILD)/tests/hypergraph.tests.js \
	$(BUILD)/tests/geyser.tests.js \
	$(BUILD)/tests/base.tests.js \
	$(BUILD)/tests/h2o-proxy.tests.js \
	$(BUILD)/tests/frame-list-view.tests.js \
	$(BUILD)/tests/frame-view.tests.js


# Lib javascript files that go into the browser target.
BROWSER_LIB_SCRIPTS=\
	$(LIB)/stacktrace/stacktrace.js \
	$(LIB)/jquery/dist/jquery.js \
	$(LIB)/lodash/dist/lodash.js \
	$(LIB)/momentjs/min/moment.min.js \
	$(LIB)/js-signals/dist/signals.js \
	$(LIB)/crossroads/dist/crossroads.js \
	$(LIB)/hasher/dist/js/hasher.js \
	$(LIB)/bootstrap/dist/js/bootstrap.js \
	$(LIB)/d3/d3.js \
	$(LIB)/knockout/knockout.js

# Lib javascript files that go into the browser target. 
TEST_LIB_SCRIPTS=\
	$(LIB)/jquery/dist/jquery.js \
	$(LIB)/lodash/dist/lodash.js \
	$(LIB)/knockout/knockout.js

TEMPLATES=\
	src/templates/main-view.jade \
	src/templates/help.jade \
	src/templates/geyser.jade \
	src/templates/alert-dialog.jade \
	src/templates/error-dialog.jade \
	src/templates/confirm-dialog.jade \
	src/templates/select-frame-dialog.jade \
	src/templates/configure-strip-plot-dialog.jade \
	src/templates/import-files-dialog.jade \
	src/templates/parse-files-dialog.jade \
	src/templates/topic-list-view.jade \
	src/templates/file-list-view.jade \
	src/templates/frame-list-view.jade \
	src/templates/model-list-view.jade \
	src/templates/scoring-list-view.jade \
	src/templates/notification-list-view.jade \
	src/templates/empty-view.jade \
	src/templates/file-view.jade \
	src/templates/frame-view.jade \
	src/templates/model-view.jade \
	src/templates/model-selection-view.jade \
	src/templates/frame-inspection-view.jade \
	src/templates/model-inspection-view.jade \
	src/templates/scoring-view.jade \
	src/templates/notification-view.jade \
	src/templates/scoring-selection-view.jade

STYLES=\
	src/styles/variables.styl \
	src/styles/mixins.styl \
	src/styles/fonts.styl \
	src/styles/bootstrap.styl \
	src/styles/global.styl \
	src/styles/main-view.styl \
	src/styles/select-frame-dialog.styl \
	src/styles/import-files-dialog.styl \
	src/styles/parse-files-dialog.styl \
	src/styles/topic-list-view.styl \
	src/styles/file-list-view.styl \
	src/styles/frame-list-view.styl \
	src/styles/model-list-view.styl \
	src/styles/scoring-list-view.styl \
	src/styles/notification-list-view.styl \
	src/styles/empty-view.styl \
	src/styles/file-view.styl \
	src/styles/frame-view.styl \
	src/styles/model-view.styl \
	src/styles/model-selection-view.styl \
	src/styles/scoring-view.styl \
	src/styles/notification-view.styl \
	src/styles/scoring-selection-view.styl

LIB_STYLES=\
	$(LIB)/fontawesome/css/font-awesome.css \
	$(LIB)/bootstrap/dist/css/bootstrap.css

ifeq ($(shell which node),)
default:
	@echo Dependency node not found.  Skipping UI build...  Try: brew install node for MacOS or look at h2o/client/README.md for other platforms. 1>&2

else
  ifeq ($(shell which npm),)
default:
	@echo Dependency npm not found.  Skipping UI build...  Try: brew install node for MacOS or look at h2o/client/README.md for other platforms. 1>&2

  else
    ifeq ($(shell which bower),)
default:
	@echo Dependency bower not found.  Skipping UI build...  Try: npm install -g bower 1>&2

    else
default: build

    endif
  endif
endif


setup: check
	@echo Prerequisites OK. Installing dependencies...
	npm install
	bower --config.interactive=false install
	@echo Done!

really_build: \
	$(DEPLOY)/index.html \
	$(DEPLOY)/fonts/glyphicons-halflings-regular.eot \
	$(DEPLOY)/fonts/glyphicons-halflings-regular.svg \
	$(DEPLOY)/fonts/glyphicons-halflings-regular.ttf \
	$(DEPLOY)/fonts/glyphicons-halflings-regular.woff \
	$(DEPLOY)/fonts/fontawesome-webfont.eot \
	$(DEPLOY)/fonts/fontawesome-webfont.svg \
	$(DEPLOY)/fonts/fontawesome-webfont.ttf \
	$(DEPLOY)/fonts/fontawesome-webfont.woff \
	$(DEPLOY)/img/h2o.png \
	$(DEPLOY)/img/h2o-spin-16.gif \
	$(DEPLOY)/img/h2o-spin-32.gif \
	$(DEPLOY)/img/h2o-spin-48.gif \
	$(DEPLOY)/img/h2o-spin-64.gif \
	$(DEPLOY)/css/bootstrap.css.map \
	$(DEPLOY)/css/lib.css \
	$(DEPLOY)/css/steam.css \
	$(DEPLOY)/js/lib.js \
	$(DEPLOY)/js/steam.js

build:
	$(MAKE) really_build

unit: \
	$(DEPLOY)/tests/tape.js \
	$(DEPLOY)/tests/lib.test.js \
	$(DEPLOY)/tests/steam.test.js \
	$(DEPLOY)/tests/index.html

node: \
	$(DIST)/steam.node.js

watch:
	$(BIN)/watchy --watch src -- make build

lint:
	# Sort of pointless running this. 
	# Pretty much all errors thrown point to coffeescript-generated code.
	# TODO revisit better way to lint coffeescript.
	(BIN)/jshint $(DEPLOY)/js/steam.js

preload:
	Rscript src/util/h2o-R/preload.R

help:
	@echo "Please use \`make <target>' where <target> is one of -"
	@echo ""
	@echo "Setup tasks:"
	@echo "  make check      Check prerequisites"
	@echo "  make setup      Set up dev dependencies"
	@echo "  make reset      Clean up dev dependencies"
	@echo "  make preload    Preload a few frames and models into H2O (requires R)"
	@echo ""
	@echo "Development tasks:"
	@echo "  make build      Build and deploy to ../lib/resources/steam/index.html"
	@echo "  make unit       Build browser test suite and deploy to ../lib/resources/steam/tests/index.html"
	@echo "  make test       Run all tests"
	@echo "  make debug      Run tests in debug mode"
	@echo "  make coverage   Compile test coverage"
	@echo "  make doc        Compile code documentation"
	@echo "  make clean      Clean up build directories"
	@echo "  make watch      Watch for changes and run `make build test`"

# This runs all tests by default. 
# To run specific tests, run:
#   make ARGS="-g foobar" test
# where foobar is a regexp
test: node
	@node $(DIST)/steam.node.js

faucet: node
	@node $(DIST)/steam.node.js | $(BIN)/faucet

debug: node
	@node --debug-brk $(DIST)/steam.node.js

pytest: node
	(cd ../py && python testdir_single_jvm/test_model_management.py -ccj localhost.json ApiTestCase.test_steam)

coverage: node
	@mkdir -p $(BUILD)/coverage
	@$(BIN)/istanbul \
		cover \
		--dir $(BUILD)/coverage \
		-x "**/lib/**" \
		$(DIST)/steam.node.js
	@echo Coverage generated at $(BUILD)/coverage

tags:
	(cd src/scripts && ctags -R .)

doc: 
	@mkdir -p $(BUILD)/doc
	$(BIN)/groc \
		--out $(BUILD)/doc \
		--index src/README.md \
		--index-page-title "index" \
		src/scripts/**/*.coffee
	@echo Docs generated at $(BUILD)/doc

clean:
	rm -rf $(BUILD_DIRS)
	@echo Cleaned!

check:
	@echo Checking prerequisites...

reset: clean
	rm -rf \
		$(MODULES) \
		$(LIB)
	@echo Done! To set up your dev environment again, run: make setup

$(BUILD)/scripts/%.global.js: src/scripts/%.global.coffee
	@mkdir -p $(@D)
	@$(BIN)/coffee --bare --output $(@D) --compile $<
	@echo Built $@

$(BUILD)/tests/%.global.js: src/tests/%.global.coffee
	@mkdir -p $(@D)
	@$(BIN)/coffee --bare --output $(@D) --compile $<
	@echo Built $@

$(BUILD)/scripts/%.js: src/scripts/%.coffee
	@mkdir -p $(@D)
	@$(BIN)/coffee --output $(@D) --compile $<
	@echo Built $@

$(BUILD)/tests/%.js: src/tests/%.coffee
	@mkdir -p $(@D)
	@$(BIN)/coffee --output $(@D) --compile $<
	@echo Built $@

$(DEPLOY)/index.html: src/templates/index.jade $(TEMPLATES)
	@mkdir -p $(@D)
	@$(BIN)/jade --pretty --out $(@D) $<
	@echo Built $@

$(DEPLOY)/css/steam.css: src/styles/steam.styl $(STYLES)
	@mkdir -p $(@D)
	@$(BIN)/stylus --use nib --out $(@D) $<
	@echo Built $@

$(DEPLOY)/js/steam.js: $(COMMON_SCRIPTS) $(BROWSER_SCRIPTS)
	@mkdir -p $(@D)
	@echo '"use strict";' > $@
	@echo '(function(){' >> $@
	@cat $^ >> $@
	@echo '}).call(this);' >> $@
	@echo Built $@

$(DIST)/steam.node.js: $(BUILD)/tests/node.global.js $(COMMON_SCRIPTS) $(TEST_SCRIPTS)
	@mkdir -p $(@D)
	@echo '"use strict";' > $@
	@echo '(function(){' >> $@
	@cat $^ >> $@
	@echo '}).call(this);' >> $@
	@echo Built $@

$(DEPLOY)/tests/steam.test.js: $(COMMON_SCRIPTS) $(TEST_SCRIPTS)
	@mkdir -p $(@D)
	@echo '"use strict";' > $@
	@echo '(function(){' >> $@
	@cat $^ >> $@
	@echo '}).call(this);' >> $@
	@echo Built $@

$(DEPLOY)/img/%.png: src/images/%.png
	@mkdir -p $(@D)
	cp $< $@

$(DEPLOY)/img/%.gif: src/images/%.gif
	@mkdir -p $(@D)
	cp $< $@

$(DEPLOY)/fonts/%.eot: src/fonts/%.eot
	@mkdir -p $(@D)
	cp $< $@
$(DEPLOY)/fonts/%.svg: src/fonts/%.svg
	@mkdir -p $(@D)
	cp $< $@
$(DEPLOY)/fonts/%.ttf: src/fonts/%.ttf
	@mkdir -p $(@D)
	cp $< $@
$(DEPLOY)/fonts/%.woff: src/fonts/%.woff
	@mkdir -p $(@D)
	cp $< $@
$(DEPLOY)/fonts/%.eot: $(LIB)/bootstrap/dist/fonts/%.eot
	@mkdir -p $(@D)
	cp $< $@
$(DEPLOY)/fonts/%.svg: $(LIB)/bootstrap/dist/fonts/%.svg
	@mkdir -p $(@D)
	cp $< $@
$(DEPLOY)/fonts/%.ttf: $(LIB)/bootstrap/dist/fonts/%.ttf
	@mkdir -p $(@D)
	cp $< $@
$(DEPLOY)/fonts/%.woff: $(LIB)/bootstrap/dist/fonts/%.woff
	@mkdir -p $(@D)
	cp $< $@
$(DEPLOY)/fonts/%.eot: $(LIB)/fontawesome/fonts/%.eot
	@mkdir -p $(@D)
	cp $< $@
$(DEPLOY)/fonts/%.svg: $(LIB)/fontawesome/fonts/%.svg
	@mkdir -p $(@D)
	cp $< $@
$(DEPLOY)/fonts/%.ttf: $(LIB)/fontawesome/fonts/%.ttf
	@mkdir -p $(@D)
	cp $< $@
$(DEPLOY)/fonts/%.woff: $(LIB)/fontawesome/fonts/%.woff
	@mkdir -p $(@D)
	cp $< $@

$(DEPLOY)/css/%.map: $(LIB)/bootstrap/dist/css/%.map
	@mkdir -p $(@D)
	cp $< $@

$(DEPLOY)/css/lib.css: $(LIB_STYLES)
	@mkdir -p $(@D)
	@cat $^ > $@
	@echo Built $@

$(DEPLOY)/js/lib.js: $(BROWSER_LIB_SCRIPTS)
	@mkdir -p $(@D)
	@cat $^ > $@
	@echo Built $@

$(DEPLOY)/tests/lib.test.js: $(TEST_LIB_SCRIPTS)
	@mkdir -p $(@D)
	@cat $^ > $@
	@echo Built $@

$(DEPLOY)/tests/tape.js: node_modules/tape/index.js
	@mkdir -p $(@D)
	@$(BIN)/browserify $< -s test -o $@
	@echo Built $@

$(DEPLOY)/tests/index.html: src/tests/index.html
	@mkdir -p $(@D)
	cp $< $@
	@echo Built $@

.PHONY: default really_build build node unit smoke test debug report spec coverage doc clean setup reset watch help preload pytest tags
