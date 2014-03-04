
JAVAC_ARGS = -g -source 1.6 -target 1.6 -XDignore.symbol.file -Xlint:all -Xlint:-deprecation -Xlint:-serial -Xlint:-rawtypes -Xlint:-unchecked

javas = $(wildcard src/main/water/*java src/test/water/*java)
classes = $(patsubst src/main/%.java,build/classes/%.class,$(javas))

default: build/h2o.jar

# Dumb-ass javac *all* java files, then jar them
build/h2o.jar: $(classes)
	@echo "compiling "
	[ -d build/classes ] || mkdir -p build/classes
	javac $(JAVAC_ARGS) -cp "" -sourcepath "." -d "build/classes" $(javas)
	jar -cfm build/h2o.jar manifest.txt -C build/classes .

.PHONY: clean
clean:
	rm -rf build

# Called "config" here, after auto-conf, but really just asks each sub-make to list tools
.PHONY: conf
conf:
	@echo $(CURDIR) requires java
	java -version
