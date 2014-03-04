
JAVAC_ARGS = -g -source 1.6 -target 1.6 -XDignore.symbol.file -Xlint:all -Xlint:-deprecation -Xlint:-serial -Xlint:-rawtypes -Xlint:-unchecked


default: build/h2o.jar

build/h2o.jar:
	echo "building classes..."
	mkdir -p build/classes
	javac $(JAVAC_ARGS) \
	-cp "${CLASSPATH}" \
	-sourcepath "." \
	-d "build/classes" \
	src/main/java/water/*.java
	jar -cfm build/h2o.jar manifest.txt build/classes

.PHONY: clean
clean:
	rm -rf build
