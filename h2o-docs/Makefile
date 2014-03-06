
default: build/foo

check: build/foo

build/foo:
	echo "Touching foo"
	mkdir -p build
	touch build/foo

.PHONY: clean
clean:
	rm -rf build
