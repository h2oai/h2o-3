
# Source code
main_javas = src/main/java/water/H2OApp.java
main_classes = $(patsubst src/main/java/%java,build/classes/%class,$(main_javas))
classes = $(main_classes)

# All the libraries; these are downloaded by gradle and slapped into the lib/ directory.
libs = $(wildcard ../lib/*jar)
jars = $(subst $(space),$(SEP),$(libs))$(SEP)../h2o-core/build/libs/h2o-core.jar$(SEP)../h2o-algos/build/libs/h2o-algos.jar$

# Fun Args to javac.  Mostly limit to java6 source definitions, and fairly
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

# Default build: the h2o-web jar file
default: build/libs/h2o-web.jar

# Just the classes, no jarring step
classes: $(classes)


JVM = java -ea -cp "build/classes$(SEP)$(jars)" 
JUNIT_TESTS = 

# Run 5 JVMs and check the basic tests work.  Output in sandbox/out.[01234]
check: $(classes)
	@rm -rf sandbox ; mkdir sandbox
	-($(JVM) water.H2O 1> sandbox/out.1 2>&1 & $(JVM) water.H2O 1> sandbox/out.2 2>&1 & $(JVM) water.H2O 1> sandbox/out.3 2>&1 & $(JVM) water.H2O 1> sandbox/out.4 2>&1 & (sleep 1; $(JVM) org.junit.runner.JUnitCore $(JUNIT_TESTS) 2>&1 ; echo $$? > sandbox/status.0) | tee sandbox/out.0 )
	-@(pkill java; echo )
	@exit `cat sandbox/status.0`

# Compile just the out-of-date files
$(main_classes): build/classes/%class: src/main/java/%java
	@echo "compiling " $@ " because " $?
	@[ -d build/classes ] || mkdir -p build/classes
	@javac $(JAVAC_ARGS) -cp "build/classes$(SEP)$(jars)" -sourcepath src/main/java -d build/classes $(main_javas)

# Build the h2o-web.jar file
# All included jars are unpacked into a flat structure, then repacked into One Jar
# Name collisions amongst packages are quietly ignored.
# H2O names win over all other names.
#	@(cd build/tmp; for d in $(libs); do ( jar xf ../../$$d ); done )
build/libs/h2o-web.jar: $(classes) ../h2o-algos/build/libs/h2o-algos.jar ../h2o-core/build/libs/h2o-core.jar ../h2o-core/build/libs/h2o-core-test.jar
	@echo "jarring " $@ " because " $?
	@[ -d build/libs ] || mkdir -p build/libs
	@rm -rf build/tmp ; mkdir -p build/tmp
	@(cd build/tmp; jar xf ../../../lib/junit*.jar; jar xf ../../../lib/hamcrest*.jar; jar xf ../../../lib/joda*.jar; jar xf ../../../lib/log4j*.jar; jar xf ../../../h2o-algos/build/libs/h2o-algos.jar; jar xf ../../../h2o-core/build/libs/h2o-core.jar; jar xf ../../../h2o-core/build/libs/h2o-core-test.jar; rm -rf META-INF )
	@cp -r build/classes/water build/tmp
	@jar -cfm build/libs/h2o-web.jar manifest.txt -C build/tmp .
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
