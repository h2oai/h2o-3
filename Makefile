
# All the subdirs we might recursively make, in no particular order
SUBDIRS = h2o-core integ-r integ-scala integ-hadoop

# subdirs is a (phony) target we can make
.PHONY: subdirs $(SUBDIRS)
subdirs: $(SUBDIRS)

# Each subdir is its own (phony) target, using a recursive-make
$(SUBDIRS):
	$(MAKE) -C $@

# By default, make all subdirs
default: subdirs

# R-integration requires H2O to be built first
integ-r: h2o-core

# Scala-integration requires H2O to be built first
integ-scala: h2o-core

# Hadoop/Yarn-integration requires H2O to be built first
integ-hadoop: h2o-core


# Recursive clean
.PHONY: clean
clean:
	rm -rf build
	-for d in $(SUBDIRS); do ($(MAKE) -C $$d clean ); done
