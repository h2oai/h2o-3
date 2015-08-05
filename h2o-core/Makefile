.DELETE_ON_ERROR:

# Source code
# Note that BuildVersion is not forced to be rebuilt here - so incremental
# makes in this directory will endlessly use the same BuildVersion.
main_javas = $(wildcard src/main/java/water/*java src/main/java/water/*/*java src/main/java/jsr166y/*java src/main/java/hex/*java src/main/java/hex/*/*java )
test_javas = $(wildcard src/test/java/water/*java src/test/java/water/*/*java src/test/java/water/*/*/*java)
main_classes = $(patsubst src/main/java/%java,build/classes/main/%class,$(main_javas))
test_classes = $(patsubst src/test/java/%java,build/classes/test/%class,$(test_javas))
test_clazzes = $(patsubst src/test/java/%.java,%,$(wildcard src/test/java/water/*java src/test/java/water/*/*java))
classes = $(main_classes) $(test_classes)

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

# Default build: the h2o-core jar file
default: build/libs/h2o-core.jar build/libs/h2o-core-test.jar

# Just the classes, no jarring step
classes: $(classes)


#JVM = nice java -ea -cp "build/classes/main$(SEP)build/classes/test$(SEP)$(jars)" 
## Tests that happen during Cloud startup
#JUNIT_TESTS_BOOT = water.AAA_PreCloudLock
## Too slow for normal junit runs
#JUNIT_TESTS_SLOW1 = water.KVTest
#JUNIT_TESTS_SLOW2 = water.parser.ParseProgressTest
#JUNIT_TESTS_SLOW3 = water.fvec.WordCountBigTest
## All Tests minus the excludes
#JUNIT_TESTS0 = $(subst /,.,$(test_clazzes))
#JUNIT_TESTS1 = $(subst $(JUNIT_TESTS_BOOT) ,,$(JUNIT_TESTS0))
#JUNIT_TESTS2 = $(subst $(JUNIT_TESTS_SLOW1),,$(JUNIT_TESTS1))
#JUNIT_TESTS3 = $(subst $(JUNIT_TESTS_SLOW2),,$(JUNIT_TESTS2))
#JUNIT_TESTS4 = $(subst $(JUNIT_TESTS_SLOW3),,$(JUNIT_TESTS3))
#JUNIT_TESTS5 = $(subst water.TestUtil,,$(JUNIT_TESTS4))
## Tests
#JUNIT_TESTS  = $(JUNIT_TESTS_BOOT) $(JUNIT_TESTS5)

# Run 5 JVMs and check the basic tests work.  Output in sandbox/out.[01234]
check: $(classes)
	./testMultiNode.sh
#	@rm -rf sandbox ; mkdir sandbox
#	-($(JVM) water.H2O 1> sandbox/out.1 2>&1 & $(JVM) water.H2O 1> sandbox/out.2 2>&1 & $(JVM) water.H2O 1> sandbox/out.3 2>&1 & $(JVM) water.H2O 1> sandbox/out.4 2>&1 & (sleep 1; $(JVM) org.junit.runner.JUnitCore $(JUNIT_TESTS) 2>&1 ; echo $$? > sandbox/status.0) | tee sandbox/out.0 )
#	-@(pkill java; echo )
#	@exit `cat sandbox/status.0`

# Run 5 JVMs and check the basic tests work.  Output in sandbox/out.[01234]
check_slow: $(classes)
	./testMultiNode.sh
#	@rm -rf sandbox ; mkdir sandbox
#	-($(JVM) water.H2O 1> sandbox/out.1 2>&1 & $(JVM) water.H2O 1> sandbox/out.2 2>&1 & $(JVM) water.H2O 1> sandbox/out.3 2>&1 & $(JVM) water.H2O 1> sandbox/out.4 2>&1 & (sleep 1; $(JVM) org.junit.runner.JUnitCore $(JUNIT_TESTS) $(JUNIT_TESTS_SLOW) 2>&1 ; echo $$? > sandbox/status.0) | tee sandbox/out.0 )
#	@pkill java
#	@exit `cat sandbox/status.0`

# Compile just the out-of-date files
$(main_classes): build/classes/main/%class: src/main/java/%java
	@echo "compiling " $@ " because " $?
	@[ -d build/classes/main ] || mkdir -p build/classes/main
	@javac $(JAVAC_ARGS) -cp "build/classes/main$(SEP)$(jars)" -sourcepath src/main/java -d build/classes/main $(main_javas)

$(test_classes): build/classes/test/%class: src/test/java/%java $(filter-out %/AbstractBuildVersion.class,$(main_classes))
	@echo "compiling " $@ " because " $?
	@[ -d build/classes/test ] || mkdir -p build/classes/test
	@javac $(JAVAC_ARGS) -cp "build/classes/main$(SEP)build/classes/test$(SEP)$(jars)" -sourcepath src/main/java -d build/classes/test $(test_javas)

# Build the h2o-core.jar file
# All included jars are unpacked into a flat structure, then repacked into One Jar
# Name collisions amongst packages are quietly ignored.
# H2O names win over all other names.
build/libs/h2o-core.jar: $(main_classes) build/classes/main/water/init/BuildVersion.class
	@echo "jarring " $@ " because " $?
	@[ -d build/libs ] || mkdir -p build/libs
	@rm -rf build/tmp ; mkdir -p build/tmp
	@(cd build/tmp; for d in $(libs); do ( jar xf ../../$$d; rm -rf META-INF ); done )
	@jar -cfm build/libs/h2o-core.jar manifest.txt -C build/classes/main . -C build/tmp . -C src/main resources
	@rm -rf build/tmp

build/libs/h2o-core-test.jar: $(test_classes)
	@echo "jarring " $@ " because " $?
	@[ -d build/libs ] || mkdir -p build/libs
	@jar -cfm build/libs/h2o-core-test.jar manifest.txt -C build/classes/test .

# JavaDoc!
# Exclude the C*Chunk files, as they are specific kinds of compression.
chunk_javas = $(wildcard src/main/java/water/fvec/C*Chunk.java)
javadoc_javas = $(filter-out $(chunk_javas),$(main_javas))
javadoc_htmls = $(patsubst src/main/java/%java,build/docs/javadoc/%html,$(javadoc_javas))

build/docs/javadoc/%.html : src/main/java/%.java
	@if grep --quiet "public .*class " $? ; then echo $< >> build/tmp.javas; fi

# Build JUST the javadoc for changed javas
javadoc_fast: $(javadoc_htmls)
	@javadoc -quiet -source 1.7 -classpath "build/classes/main$(SEP)$(jars)" -sourcepath src/main/java -d build/docs/javadoc `cat build/tmp.javas`
	@rm -f build/tmp.javas

javadoc: $(javadoc_javas)
	@javadoc -quiet -source 1.7 -classpath "build/classes/main$(SEP)$(jars)" -sourcepath src/main/java -d build/docs/javadoc $(javadoc_javas)
	@rm -f build/tmp.javas


.PHONY: clean
clean:
	rm -rf build


# Called "conf" here, after auto-conf, but really just asks each sub-make to list tools
.PHONY: conf
conf:
	@echo $(CURDIR) requires java, jar
	java -version
	which jar
