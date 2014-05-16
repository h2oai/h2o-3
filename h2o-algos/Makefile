
# Source code
main_javas = $(wildcard src/main/java/hex/*java src/main/java/hex/*/*java)
test_javas = $(wildcard src/test/java/hex/*java src/test/java/hex/*/*java)
main_classes = $(patsubst src/main/java/%java,build/classes/%class,$(main_javas))
test_classes = $(patsubst src/test/java/%java,build/classes/%class,$(test_javas))
classes = $(main_classes) $(test_classes)

# All the libraries; these are downloaded by gradle and slapped into the lib/ directory.
libs = $(wildcard ../lib/*jar)
jars = $(subst $(space),$(SEP),$(libs))$(SEP)../h2o-core/build/h2o-core.jar

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

# Default build: the h2o-algos jar file
default: build/h2o-algos.jar

# Just the classes, no jarring step
classes: $(classes)


JVM = java -ea -cp "build/classes$(SEP)$(jars)" 
JUNIT_TESTS = hex.deeplearning.DeepLearningIrisTest hex.deeplearning.DeepLearningProstateTest hex.deeplearning.DropoutTestNeuronsTest

# Run 5 JVMs and check the basic tests work.  Output in sandbox/out.[01234]
check: $(classes)
	@rm -rf sandbox ; mkdir sandbox
	-($(JVM) water.H2O 1> sandbox/out.1 2>&1 & $(JVM) water.H2O 1> sandbox/out.2 2>&1 & $(JVM) water.H2O 1> sandbox/out.3 2>&1 & $(JVM) water.H2O 1> sandbox/out.4 2>&1 & (sleep 1; $(JVM) org.junit.runner.JUnitCore $(JUNIT_TESTS) 2>&1 ; echo $$? > sandbox/status.0) | tee sandbox/out.0 )
	@pkill java
	@exit `cat sandbox/status.0`

# Compile just the out-of-date files
$(main_classes): build/classes/%class: src/main/java/%java
	@echo "compiling " $?
	@[ -d build/classes ] || mkdir -p build/classes
	@javac $(JAVAC_ARGS) -cp "build/classes$(SEP)$(jars)" -sourcepath src/main/java -d build/classes $(main_javas)

$(test_classes): build/classes/%class: src/test/java/%java
	@echo "compiling " $?
	@[ -d build/classes ] || mkdir -p build/classes
	@javac $(JAVAC_ARGS) -cp "build/classes$(SEP)$(jars)" -sourcepath src/main/java -d build/classes $(test_javas)

# Build the h2o-algos.jar file
build/h2o-algos.jar: $(classes)
	@jar -cf build/h2o-algos.jar -C build/classes .


.PHONY: clean
clean:
	rm -rf build


# Called "conf" here, after auto-conf, but really just asks each sub-make to list tools
.PHONY: conf
conf:
	@echo $(CURDIR) requires java, jar
	java -version
	which jar
