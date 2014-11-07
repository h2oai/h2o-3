.DELETE_ON_ERROR:

# Source code
main_javas = $(wildcard src/main/java/hex/*java src/main/java/hex/*/*java src/main/java/hex/*/*/*java)
test_javas = $(wildcard src/test/java/hex/*java src/test/java/hex/*/*java src/test/java/hex/*/*/*java)
main_classes = $(patsubst src/main/java/%java,build/classes/main/%class,$(main_javas))
test_classes = $(patsubst src/test/java/%java,build/classes/test/%class,$(test_javas))
classes = $(main_classes) $(test_classes)

# All the libraries; these are downloaded by gradle and slapped into the lib/ directory.
libs = $(wildcard ../lib/*jar)
jars = $(subst $(space),$(SEP),$(libs))$(SEP)../h2o-core/build/libs/h2o-core.jar$(SEP)../h2o-core/build/libs/h2o-core-test.jar

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
default: build/libs/h2o-algos.jar build/libs/h2o-algos-test.jar

# Just the classes, no jarring step
classes: $(classes)


#JVM = java -ea -cp "build/classes/main$(SEP)build/classes/test$(SEP)$(jars)" 
# deep learning tests turned off as they are currently not working
#JUNIT_TESTS = hex.deeplearning.DeepLearningIrisTest hex.deeplearning.DeepLearningProstateTest hex.deeplearning.DropoutTest hex.deeplearning.NeuronsTest hex.kmeans.KMeansTest hex.example.ExampleTest hex.tree.gbm.GBMTest

# Run 5 JVMs and check the basic tests work.  Output in sandbox/out.[01234]
check: $(classes)
	./testMultiNode.sh
#	@rm -rf sandbox ; mkdir sandbox
#	-($(JVM) water.H2O 1> sandbox/out.1 2>&1 & $(JVM) water.H2O 1> sandbox/out.2 2>&1 & $(JVM) water.H2O 1> sandbox/out.3 2>&1 & $(JVM) water.H2O 1> sandbox/out.4 2>&1 & (sleep 1; $(JVM) org.junit.runner.JUnitCore $(JUNIT_TESTS) 2>&1 ; echo $$? > sandbox/status.0) | tee sandbox/out.0 )
#	-@(pkill java; echo )
#	@exit `cat sandbox/status.0`

# Compile just the out-of-date files
$(main_classes): build/classes/main/%class: src/main/java/%java
	@echo "compiling " $@ " because " $?
	@[ -d build/classes/main ] || mkdir -p build/classes/main
	@javac $(JAVAC_ARGS) -cp "build/classes/main$(SEP)$(jars)" -sourcepath src/main/java -d build/classes/main $(main_javas)

$(test_classes): build/classes/test/%class: src/test/java/%java
	@echo "compiling " $@ " because " $?
	@[ -d build/classes/test ] || mkdir -p build/classes/test
	@javac $(JAVAC_ARGS) -cp "build/classes/main$(SEP)build/classes/test$(SEP)$(jars)" -sourcepath src/main/java -d build/classes/test $(test_javas)

# Build the h2o-algos.jar file
build/libs/h2o-algos.jar: $(main_classes)
	@echo "jarring " $@ " because " $?
	@[ -d build/libs ] || mkdir -p build/libs
	@jar -cf build/libs/h2o-algos.jar -C build/classes/main .

build/libs/h2o-algos-test.jar: $(test_classes)
	@echo "jarring " $@ " because " $?
	@[ -d build/libs ] || mkdir -p build/libs
	@jar -cf build/libs/h2o-algos-test.jar -C build/classes/test .


.PHONY: clean
clean:
	rm -rf build


# Called "conf" here, after auto-conf, but really just asks each sub-make to list tools
.PHONY: conf
conf:
	@echo $(CURDIR) requires java, jar
	java -version
	which jar
