
# All the subdirs we might recursively make
SUBDIRS = h2o r-integ

# subdirs is a (phony) target we can make
.PHONY: subdirs $(SUBDIRS)
subdirs: $(SUBDIRS)

# Each subdir is its own (phony) target, using a recursive-make
$(SUBDIRS):
	$(MAKE) -C $@

# By default, make all subdirs
default: subdirs

# R-integration requires H2O to be built first
r-integ: h2o

# Recursive clean
.PHONY: clean
clean:
	-for d in $(SUBDIRS); do ($(MAKE) -C $$d clean ); done
