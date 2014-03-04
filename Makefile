
# All the subdirs we might recursively make, in no particular order
SUBDIRS = h2o-core h2o-r h2o-scala h2o-hadoop h2o-docs assembly

# subdirs is a (phony) target we can make
.PHONY: subdirs $(SUBDIRS)
subdirs: $(SUBDIRS)

# Each subdir is its own (phony) target, using a recursive-make
$(SUBDIRS):
	$(MAKE) -C $@

# By default, make all subdirs
default: subdirs

# R-integration requires H2O to be built first
h2o-r: h2o-core

# Scala-integration requires H2O to be built first
h2o-scala: h2o-core

# Hadoop/Yarn-integration requires H2O to be built first
h2o-hadoop: h2o-core

# pkg needs other stuff built first
pkg: h2o-core h2o-r h2o-scala h2o-hadoop docs

# Recursive clean
.PHONY: clean
clean:
	rm -rf build
	-for d in $(SUBDIRS); do ($(MAKE) -C $$d clean ); done
