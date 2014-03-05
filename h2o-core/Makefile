
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

javas = $(wildcard src/main/water/*java src/test/water/*java)
libs = $(wildcard ../lib/*jar)
jars = $(subst $(space),$(SEP),$(libs))

default: build/h2o.jar

# Compile just the out-of-date files.
build/h2o.jar: $(javas)
	@echo "compiling " $?
	@[ -d build/classes ] || mkdir -p build/classes
	javac $(JAVAC_ARGS) -cp "build/classes$(SEP)$(jars)" -sourcepath src/main -d build/classes $?
	@echo "jaring " $@
	@jar -cfm build/h2o.jar manifest.txt -C build/classes .

.PHONY: clean
clean:
	rm -rf build

# Called "config" here, after auto-conf, but really just asks each sub-make to list tools
.PHONY: conf
conf:
	@echo $(CURDIR) requires java, jar
	java -version
	which jar
