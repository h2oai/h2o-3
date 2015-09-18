.DELETE_ON_ERROR:

# Source code
main_javas = $(wildcard src/main/java/water/*java src/main/java/hex/*java)
main_classes = $(patsubst src/main/java/%java,build/classes/main/%class,$(main_javas))
classes = $(main_classes)

# All the libraries; these are downloaded by gradle and slapped into the lib/ directory.
libs = $(wildcard ../lib/*jar)
jars = $(subst $(space),$(SEP),$(libs))

# Fun Args to javac.  Mostly limit to java7 source definitions, and fairly
# agressive lint warnings.
JAVAC_ARGS = -g -source 1.7 -target 1.7 -XDignore.symbol.file -Xlint:all -Xlint:-deprecation -Xlint:-serial -Xlint:-rawtypes -Xlint:-unchecked

# literal space
space :=
space +=

# Decide OS-specific questions
# jar-file seperator
ifeq ($(OS),Windows_NT)
	SEP = ;
else
# linux
	UNAME = $(shell uname)
	ifeq ($(UNAME),Darwin)
		SEP = :
	endif
	ifeq ($(UNAME),Linux)
		SEP = :
	endif
endif

# Default build: the h2o-genmodel jar file
default: build/libs/h2o-genmodel.jar

# Just the classes, no jarring step
classes: $(classes)


# Compile just the out-of-date files
$(main_classes): build/classes/main/%class: src/main/java/%java
	@echo "compiling " $@ " because " $?
	@[ -d build/classes/main ] || mkdir -p build/classes/main
	@javac $(JAVAC_ARGS) -cp "build/classes/main$(SEP)$(jars)" -sourcepath src/main/java -d build/classes/main $(main_javas)

# Build the h2o-genmodel.jar file
# All included jars are unpacked into a flat structure, then repacked into One Jar
# Name collisions amongst packages are quietly ignored.
# H2O names win over all other names.
build/libs/h2o-genmodel.jar: $(main_classes)
	@echo "jarring " $@ " because " $?
	@[ -d build/libs ] || mkdir -p build/libs
	@rm -rf build/tmp ; mkdir -p build/tmp
	@(cd build/tmp; for d in $(libs); do ( jar xf ../../$$d; rm -rf META-INF ); done )
	@jar -cfm build/libs/h2o-genmodel.jar manifest.txt -C build/classes/main . -C build/tmp . -C src/main resources
	@rm -rf build/tmp

.PHONY: clean
clean:
	rm -rf build


# Called "conf" here, after auto-conf, but really just asks each sub-make to list tools
.PHONY: conf
conf:
	@echo $(CURDIR) requires java, jar
	java -version
	which jar
