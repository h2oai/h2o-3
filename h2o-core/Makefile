SHELL := /bin/bash
.DELETE_ON_ERROR:

DATE=`date +%Y%m%d`

# for printing variable values
# usage: make print-VARIABLE
#        > VARIABLE = value_of_variable
print-%  : ; @echo $* = $($*)

# literal space
space := $() $()

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
# Find a reasonable ctags.
CTAGS = $(shell which ctags)
# Hack for MacOS: /usr/bin/ctags is unfriendly, so look for ctags from brew
ifeq ($(UNAME),Darwin)
	CTAGS = $(shell brew list ctags 2> /dev/null | grep bin/ctags)
endif

# Fun Args to javac.  Mostly limit to java8 source definitions, and fairly
# aggressive lint warnings.
JAVAC_ARGS = -g -source 1.8 -target 1.8 -XDignore.symbol.file -Xlint:none 

# Source code
# Note that BuildVersion is not forced to be rebuilt here - so incremental
# makes in this directory will endlessly use the same BuildVersion.
FJ  := jsr166y
H2O := water
HEX := hex
SQL := org/cliffc/sql

SRC := src/main/java
TST := src/test/java

GEN := ../h2o-genmodel/$(SRC)
WEB := ../h2o-webserver-iface/$(SRC)
TSP := ../h2o-test-support/$(SRC)

CLZDIR:= build/classes
main_h2o_javas   := $(wildcard $(SRC)/$(H2O)/*java $(SRC)/$(H2O)/*/*java $(SRC)/$(H2O)/*/*/*java $(SRC)/$(H2O)/*/*/*/*java)
test_h2o_javas   := $(wildcard $(TST)/$(H2O)/*java $(TST)/$(H2O)/*/*java)
main_hex_javas   := $(wildcard $(SRC)/$(HEX)/*java $(SRC)/$(HEX)/*/*java  $(SRC)/$(HEX)/*/*/*java)
test_hex_javas   := $(wildcard $(TST)/$(HEX)/*java $(TST)/$(HEX)/*/*java)
main_sql_javas   := $(wildcard $(SRC)/$(SQL)/*java $(SRC)/$(SQL)/*/*java )
test_sql_javas   := $(wildcard $(TST)/$(SQL)/*java $(TST)/$(SQL)/*/*java)

main_gen_javas   := $(wildcard $(GEN)/$(HEX)/*java $(GEN)/$(HEX)/*/*java $(GEN)/$(HEX)/*/*/*java)
main_web_javas   := $(wildcard $(WEB)/water/webserver/iface/*java)
main_tsp_javas   := $(wildcard $(TSP)/water/*java $(TSP)/water/*/*java $(TSP)/water/*/*/*java)

main_h2o_classes := $(patsubst $(SRC)/%java,$(CLZDIR)/main/%class,$(main_h2o_javas))
test_h2o_classes := $(patsubst $(TST)/%java,$(CLZDIR)/test/%class,$(test_h2o_javas))
main_hex_classes := $(patsubst $(SRC)/%java,$(CLZDIR)/main/%class,$(main_hex_javas))
test_hex_classes := $(patsubst $(TST)/%java,$(CLZDIR)/test/%class,$(test_hex_javas))
main_sql_classes := $(patsubst $(SRC)/%java,$(CLZDIR)/main/%class,$(main_sql_javas))
test_sql_classes := $(patsubst $(TST)/%java,$(CLZDIR)/test/%class,$(test_sql_javas))

main_gen_classes := $(patsubst $(GEN)/%java,$(CLZDIR)/main/%class,$(main_gen_javas))
main_web_classes := $(patsubst $(WEB)/%java,$(CLZDIR)/main/%class,$(main_web_javas))
main_tsp_classes := $(patsubst $(TSP)/%java,$(CLZDIR)/main/%class,$(main_tsp_javas))

main_javas = $(main_h2o_javas) $(main_hex_javas) $(main_sql_javas)
test_javas = $(test_h2o_javas) $(test_hex_javas) $(main_sql_javas)
main_classes = $(main_h2o_classes) $(main_hex_classes) $(main_sql_classes)
test_classes = $(test_h2o_classes) $(test_hex_classes) $(test_sql_classes)


# All the libraries
libs = $(wildcard lib/*jar)
jars = $(subst $(space),$(SEP),$(libs))


default_targets := build/h2o.jar
# Optionally add ctags to the default target if a reasonable one was found.
ifneq ($(CTAGS),)
default_targets += tags
endif

default: $(default_targets)

# Just the classes, no jarring step
classes: $(main_classes) $(test_classes)

# Build the test classes
test:	$(test_classes)

# Compile just the out-of-date files
$(main_classes): $(CLZDIR)/main/%class: $(SRC)/%java
	@echo "compiling " $@ " because " $?
	@[ -d $(CLZDIR)/main ] || mkdir -p $(CLZDIR)/main
	@rm -f files.txt
	@echo $? > files.txt
	@javac $(JAVAC_ARGS) -cp "$(CLZDIR)/main$(SEP)$(jars)" -sourcepath "$(SRC)" -d $(CLZDIR)/main @files.txt
	@rm -f files.txt

# Compile just the out-of-date files
$(main_gen_classes): $(CLZDIR)/main/%class: $(GEN)/%java
	@echo "Gcompiling " $@ " because " $?
	@[ -d $(CLZDIR)/main ] || mkdir -p $(CLZDIR)/main
	javac $(JAVAC_ARGS) -cp "$(CLZDIR)/main$(SEP)$(jars)" -sourcepath "$(SRC)$(SEP)$(GEN)$(SEP)$(WEB)" -d $(CLZDIR)/main $?

# Compile just the out-of-date files
$(main_tsp_classes): $(CLZDIR)/main/%class: $(TSP)/%java
	@echo "compiling " $@ " because " $?
	@[ -d $(CLZDIR)/main ] || mkdir -p $(CLZDIR)/main
	@javac $(JAVAC_ARGS) -cp "$(CLZDIR)/main$(SEP)$(jars)" -sourcepath "$(SRC)$(SEP)$(GEN)$(SEP)$(TSP)" -d $(CLZDIR)/main $?

$(test_classes): $(CLZDIR)/test/%class: $(TST)/%java $(main_h2o_classes) $(main_tsp_classes)
	@echo "compiling " $@ " because " $?
	@[ -d $(CLZDIR)/test ] || mkdir -p $(CLZDIR)/test
	javac $(JAVAC_ARGS) -cp "$(CLZDIR)/test$(SEP)$(CLZDIR)/main$(SEP)$(jars)" -sourcepath "$(TST)" -d $(CLZDIR)/test $(test_javas)



# Note the tabs - not spaces - in the grep and cut commands
PROJECT_VERSION=0.0.1
BUILD_BRANCH=  git branch | grep '*' | sed 's/* //'
BUILD_HASH=    git log -1 --format="%H"
BUILD_DESCRIBE=git describe --always --dirty
BUILD_ON=      (TZ=UCT date)
BUILD_BY=      (whoami | cut -d\\ -f2-)

# Build the version file anytime anything would trigger the build/h2o.jar.
# i.e., identical dependencies to h2o.jar, except h2o.jar also includes the test
# files and the BuildVersion file.
$(CLZDIR)/main/$(H2O)/BuildVersion.java: $(main_h2o_classes) src/main/manifest.txt lib
	@echo "vrsioning " $@ " because " $?
	@rm -f $@
	@mkdir -p $(dir $@)
	@echo "package water;"                                                                >  $@
	@echo "import water.init.AbstractBuildVersion;"                                       >> $@
	@echo "public class BuildVersion extends AbstractBuildVersion {"                      >> $@
	@echo "    public String branchName()     { return \"$(shell $(BUILD_BRANCH))\"; }"   >> $@
	@echo "    public String lastCommitHash() { return \"$(shell $(BUILD_HASH))\"; }"     >> $@
	@echo "    public String describe()       { return \"$(shell $(BUILD_DESCRIBE))\"; }" >> $@
	@echo "    public String projectVersion() { return \"$(PROJECT_VERSION)\"; }"         >> $@
	@echo "    public String compiledOn()     { return \"$(shell $(BUILD_ON))\"; }"       >> $@
	@echo "    public String compiledBy()     { return \"$(shell $(BUILD_BY))\"; }"       >> $@
	@echo "}"                                                                             >> $@

$(CLZDIR)/main/$(H2O)/BuildVersion.class: $(CLZDIR)/main/$(H2O)/BuildVersion.java
	@javac $(JAVAC_ARGS) -cp "$(CLZDIR)/main$(SEP)$(jars)" -sourcepath $(SRC) -d $(CLZDIR)/main $(CLZDIR)/main/$(H2O)/BuildVersion.java

# Other Resources in h2o.jar:
JARBITS =
JARBITS += -C $(CLZDIR)/main .    # The java class files

build/h2o.jar: $(main_classes) $(main_gen_classes) $(test_h2o_classes) $(CLZDIR)/main/$(H2O)/BuildVersion.class src/main/manifest.txt lib
	@echo "  jarring " $@ " because " $?
	@[ -d build ] || mkdir -p build
# Build the h2o.jar file.  All included jars are unpacked into a flat structure,
# then repacked into One Jar.  Name collisions amongst packages are quietly
# ignored.  h2o names win over all other names.
	@jar -cfm build/h2o.jar src/main/manifest.txt $(JARBITS)

.PHONY: clean
clean:
	rm -rf build
	rm -rf out
	rm -f TAGS
	(find . -name "*~" -exec rm {} \; 2>/dev/null; exit 0)


# Download libs from maven
lib:	lib/gson-2.6.2.jar lib/joda-time-2.9.9.jar lib/commons-io-2.4.jar lib/javax.servlet-api-3.1.0.jar lib/commons-lang-2.6.jar lib/commons-math3-3.3.jar lib/javassist-3.25.0-GA.jar lib/JTransforms-3.1.jar lib/JLargeArrays-1.6.jar lib/opencsv-2.3.jar lib/commons-codec-1.9.jar lib/duke-1.2.jar lib/junit-4.12.jar lib/hamcrest-core-1.3.jar lib/system-rules-1.18.0.jar lib/httpclient-4.5.2.jar lib/httpcore-4.4.4.jar lib/mockito-core-2.18.0.jar lib/tyrus-standalone-client-1.12.jar

# GSON/JSON in Java
lib/gson-2.6.2.jar:
	@[ -d lib ] || mkdir -p lib
	@(cd lib; wget https://repo1.maven.org/maven2/com/google/code/gson/gson/2.6.2/gson-2.6.2.jar)

# All the Time Zones
lib/joda-time-2.9.9.jar:
	@[ -d lib ] || mkdir -p lib
	@(cd lib; wget https://repo1.maven.org/maven2/joda-time/joda-time/2.9.9/joda-time-2.9.9.jar)

# Some silly io shortcuts
lib/commons-io-2.4.jar:
	@[ -d lib ] || mkdir -p lib
	@(cd lib; wget https://repo1.maven.org/maven2/commons-io/commons-io/2.4/commons-io-2.4.jar)

# Servlets
lib/javax.servlet-api-3.1.0.jar:
	@[ -d lib ] || mkdir -p lib
	@(cd lib; wget https://repo1.maven.org/maven2/javax/servlet/javax.servlet-api/3.1.0/javax.servlet-api-3.1.0.jar)

# Silly array utilities
lib/commons-lang-2.6.jar:
	@[ -d lib ] || mkdir -p lib
	@(cd lib; wget https://repo1.maven.org/maven2/commons-lang/commons-lang/2.6/commons-lang-2.6.jar)

#
lib/commons-math3-3.3.jar:
	@[ -d lib ] || mkdir -p lib
	@(cd lib; wget https://repo1.maven.org/maven2/org/apache/commons/commons-math3/3.3/commons-math3-3.3.jar)

# Java-Assist bytecode rewriter
lib/javassist-3.25.0-GA.jar:
	@[ -d lib ] || mkdir -p lib
	@(cd lib; wget https://repo1.maven.org/maven2/org/javassist/javassist/3.25.0-GA/javassist-3.25.0-GA.jar)

lib/JTransforms-3.1.jar:
	@[ -d lib ] || mkdir -p lib
	@(cd lib; wget https://repo1.maven.org/maven2/com/github/wendykierp/JTransforms/3.1/JTransforms-3.1.jar)

lib/JLargeArrays-1.6.jar:
	@[ -d lib ] || mkdir -p lib
	@(cd lib; wget https://repo1.maven.org/maven2/pl/edu/icm/JLargeArrays/1.6/JLargeArrays-1.6.jar)

lib/opencsv-2.3.jar:
	@[ -d lib ] || mkdir -p lib
	@(cd lib; wget https://repo1.maven.org/maven2/net/sf/opencsv/opencsv/2.3/opencsv-2.3.jar)

lib/commons-codec-1.9.jar:
	@[ -d lib ] || mkdir -p lib
	@(cd lib; wget https://repo1.maven.org/maven2/commons-codec/commons-codec/1.9/commons-codec-1.9.jar)

lib/duke-1.2.jar:
	@[ -d lib ] || mkdir -p lib
	@(cd lib; wget https://repo1.maven.org/maven2/no/priv/garshol/duke/duke/1.2/duke-1.2.jar)

# Unit testing
lib/junit-4.12.jar lib/hamcrest-core-1.3.jar lib/system-rules-1.18.0.jar:
	@[ -d lib ] || mkdir -p lib
	@(cd lib; wget https://repo1.maven.org/maven2/junit/junit/4.12/junit-4.12.jar)
	@(cd lib; wget https://repo1.maven.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar)
	@(cd lib; wget https://repo1.maven.org/maven2/com/github/stefanbirkner/system-rules/1.18.0/system-rules-1.18.0.jar)

lib/httpclient-4.5.2.jar:
	@[ -d lib ] || mkdir -p lib
	@(cd lib; wget https://repo1.maven.org/maven2/org/apache/httpcomponents/httpclient/4.5.2/httpclient-4.5.2.jar)

lib/httpcore-4.4.4.jar:
	@[ -d lib ] || mkdir -p lib
	@(cd lib; wget https://repo1.maven.org/maven2/org/apache/httpcomponents/httpcore/4.4.4/httpcore-4.4.4.jar)

lib/mockito-core-2.18.0.jar:
	@[ -d lib ] || mkdir -p lib
	@(cd lib; wget https://repo1.maven.org/maven2/org/mockito/mockito-core/2.18.0/mockito-core-2.18.0.jar)

lib/tyrus-standalone-client-1.12.jar:
	@[ -d lib ] || mkdir -p lib
	@(cd lib; wget https://repo1.maven.org/maven2/org/glassfish/tyrus/bundles/tyrus-standalone-client/1.12/tyrus-standalone-client-1.12.jar)


# Build emacs tags (part of a tasty emacs ide experience)
tags:	$(main_h2o_javas) $(test_h2o_javas)
	@rm -f TAGS
	@$(CTAGS) -e --recurse=yes --extra=+q --fields=+fksaiS $(SRC) $(TST)
