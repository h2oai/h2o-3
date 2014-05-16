
PROJECT_VERSION = 999999

# All the subdirs we might recursively make, in no particular order
SUBDIRS = h2o-core h2o-algos h2o-web #h2o-r h2o-scala h2o-hadoop h2o-docs assembly

# subdirs is a (phony) target we can make
.PHONY: subdirs $(SUBDIRS)
subdirs: $(SUBDIRS)

# Each subdir is its own (phony) target, using a recursive-make
$(SUBDIRS):
	$(MAKE) -C $@

# By default, make all subdirs
default: subdirs

# Build, then run simple tests
check: subdirs
	@-for d in $(SUBDIRS); do ($(MAKE) -C $$d check ); done

# h2o-core wants build info to get backed into the jar
h2o-core: build/BuildVersion.java

# h2o-algos needs h2o-core
h2o-algos: h2o-core

# h2o-algo neess h2o-core
h2o-web: h2o-core h2o-algos

# R-integration requires H2O to be built first
#h2o-r: h2o-core

# Scala-integration requires H2O to be built first
#h2o-scala: h2o-core

# Hadoop/Yarn-integration requires H2O to be built first
#h2o-hadoop: h2o-core

# pkg needs other stuff built first
pkg: h2o-core h2o-r h2o-scala h2o-hadoop docs

# Recursive clean
.PHONY: clean
clean:
	rm -rf build
	-for d in $(SUBDIRS); do ($(MAKE) -C $$d clean ); done

# Recursive tool discovery.
# Called "config" here, after auto-conf, but really just asks each sub-make to list tools
.PHONY: conf
conf:
	@which git
	@which cut
	@which date
	@which grep
	@which set
	@which whoami
	@-for d in $(SUBDIRS); do ($(MAKE) -C $$d conf ); done


# Build a Java Version file.  Note that these next lines are all *text* to
# Makefile, the actual execution is delayed until the dependent file is built
# and the recipe runs.
BUILD_BRANCH=  git branch | grep '*' | sed 's/* //'
BUILD_HASH=    git log -1 --format="%H"
BUILD_DESCRIBE=git describe --always --dirty
BUILD_ON=      date
BUILD_BY=      (whoami | cut -d\\ -f2-)

FORCE:
build/BuildVersion.java: FORCE
	@mkdir -p $(dir $@)
	@echo "package water.init;"                                                           >  $@
	@echo "public class BuildVersion extends AbstractBuildVersion {"                      >> $@
	@echo "    public String branchName()     { return \"$(shell $(BUILD_BRANCH))\"; }"   >> $@
	@echo "    public String lastCommitHash() { return \"$(shell $(BUILD_HASH))\"; }"     >> $@
	@echo "    public String describe()       { return \"$(shell $(BUILD_DESCRIBE))\"; }" >> $@
	@echo "    public String projectVersion() { return \"$(PROJECT_VERSION)\"; }"         >> $@
	@echo "    public String compiledOn()     { return \"$(shell $(BUILD_ON))\"; }"       >> $@
	@echo "    public String compiledBy()     { return \"$(shell $(BUILD_BY))\"; }"       >> $@
	@echo "}"                                                                             >> $@
