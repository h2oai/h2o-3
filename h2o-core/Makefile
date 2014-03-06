
# Source code
# Note that BuildVersion is not forced to be rebuilt here - so incremental
# makes in this directory will endlessly use the same BuildVersion.
javas = $(wildcard src/main/water/*java src/main/water/*/*java src/test/water/*java ../build/BuildVersion.java)

# All the libraries; these are downloaded by gradle and slapped into the lib/ directory.
libs = $(wildcard ../lib/*jar)
jars = $(subst $(space),$(SEP),$(libs))

# Fun Args to javac.  Mostly limit to java6 source definitions, and fairly
# agressive lint warnings.
JAVAC_ARGS = -g -source 1.6 -target 1.6 -XDignore.symbol.file -Xlint:all -Xlint:-deprecation -Xlint:-serial -Xlint:-rawtypes -Xlint:-unchecked

# literal space
space :=
space +=

# jar-file seperator
ifeq (,$findstring cygwin,$(MAKE_HOST))  
# linux
SEP = :
else   
# windows
SEP = ;
endif


default: build/h2o-core.jar

check: build/h2o-core.jar
	java -cp "$(jars)" -jar build/h2o-core.jar

# Compile just the out-of-date files, then make a jar
# All included jars are unpacked into a flat structure, then repacked into One Jar
# Name collisions amongst packages are quietly ignored.
# H2O names win over all other names.
build/h2o-core.jar: $(javas)
	@echo "compiling " $?
	@[ -d build/classes ] || mkdir -p build/classes
	@javac $(JAVAC_ARGS) -cp "build/classes$(SEP)$(jars)" -sourcepath src/main -d build/classes $?
	@echo "jaring " $@
	@mkdir -p build/tmp
	@(cd build/tmp; for d in $(libs); do ( jar xf ../../$$d ); done )
	@jar -cfm build/h2o-core.jar manifest.txt -C build/classes . -C build/tmp .
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
