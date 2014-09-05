
# Source code
main_scalas = $(wildcard src/main/scala/water/*scala src/main/scala/water/*/*scala)
test_scalas = $(wildcard src/test/scala/water/*scala src/test/scala/water/*/*scala src/test/scala/water/*/*/*scala)
main_classes = $(patsubst src/main/scala/%scala,build/classes/%class,$(main_scalas))
test_classes = $(patsubst src/test/scala/%scala,build/classes/%class,$(test_scalas))
test_clazzes = $(patsubst src/test/scala/%.scala,%,$(wildcard src/test/scala/water/*scala src/test/scala/water/*/*scala))
classes = $(main_classes) $(test_classes)

# All the libraries; these are downloaded by gradle and slapped into the lib/ directory.
libs = $(wildcard ../lib/*jar)

# Fun Args to scalac.  Mostly limit to scala6 source definitions, and fairly
# agressive lint warnings.
SCALAC_ARGS =

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

# Jars, with OS-specific separators
jars = $(subst $(space),$(SEP),$(libs))$(SEP)../h2o-core/build/classes/main$(SEP)../h2o-core/build/classes/test

# Default build: the h2o-scala jar file
default: build/h2o-scala.jar

# Just the classes, no jarring step
classes: $(classes)


JVM = nice java -cp "build/classes$(SEP)$(jars)" 
# Too slow for normal junit runs
# All Tests minus the excludes
JUNIT_TESTS0 = $(subst /,.,$(test_clazzes))
JUNIT_TESTS5 = $(subst water.TestUtil,,$(JUNIT_TESTS0))
# Tests
JUNIT_TESTS  = $(JUNIT_TESTS5)

# Run 5 JVMs and check the basic tests work.  Output in sandbox/out.[01234]
check: $(classes)
	@rm -rf sandbox ; mkdir sandbox
#	-($(JVM) water.H2O 1> sandbox/out.1 2>&1 & $(JVM) water.H2O 1> sandbox/out.2 2>&1 & $(JVM) water.H2O 1> sandbox/out.3 2>&1 & $(JVM) water.H2O 1> sandbox/out.4 2>&1 & (sleep 1; $(JVM) org.junit.runner.JUnitCore $(JUNIT_TESTS) 2>&1 ; echo $$? > sandbox/status.0) | tee sandbox/out.0 )
	-($(JVM) water.H2O 1> sandbox/out.1 2>&1 & $(JVM) water.H2O 1> sandbox/out.2 2>&1 & (sleep 1; $(JVM) org.junit.runner.JUnitCore $(JUNIT_TESTS) 2>&1 ; echo $$? > sandbox/status.0) | tee sandbox/out.0 )
#	-($(JVM) org.junit.runner.JUnitCore $(JUNIT_TESTS) 2>&1 ; echo $$? > sandbox/status.0) | tee sandbox/out.0
	-@(pkill java; echo )
	@exit `cat sandbox/status.0`

# Run 5 JVMs and check the basic tests work.  Output in sandbox/out.[01234]
check_slow: $(classes)
	@rm -rf sandbox ; mkdir sandbox
	-($(JVM) water.H2O 1> sandbox/out.1 2>&1 & $(JVM) water.H2O 1> sandbox/out.2 2>&1 & $(JVM) water.H2O 1> sandbox/out.3 2>&1 & $(JVM) water.H2O 1> sandbox/out.4 2>&1 & (sleep 1; $(JVM) org.junit.runner.JUnitCore $(JUNIT_TESTS) $(JUNIT_TESTS_SLOW) 2>&1 ; echo $$? > sandbox/status.0) | tee sandbox/out.0 )
	pkill java
	@exit `cat sandbox/status.0`

# Compile just the out-of-date files
$(main_classes): build/classes/%class: src/main/scala/%scala
	@echo "compiling " $@ " because " $?
	@[ -d build/classes ] || mkdir -p build/classes
	@scalac $(SCALAC_ARGS) -cp "build/classes$(SEP)$(jars)" -sourcepath src/main/scala -d build/classes $(main_scalas)

$(test_classes): build/classes/%class: src/test/scala/%scala $(main_classes)
	@echo "compiling " $@ " because " $?
	@[ -d build/classes ] || mkdir -p build/classes
	@scalac $(SCALAC_ARGS) -cp "build/classes$(SEP)$(jars)" -sourcepath src/test/scala -d build/classes $(test_scalas)

# Build the h2o-scala.jar file
build/h2o-scala.jar: $(classes)
	@echo "jarring " $@ " because " $?
	@jar -cf build/h2o-scala.jar -C build/classes .


.PHONY: clean
clean:
	rm -rf build


# Called "conf" here, after auto-conf, but really just asks each sub-make to list tools
.PHONY: conf
conf:
	@echo $(CURDIR) requires java, scala, scalac
	scala -version
	scalac -version

