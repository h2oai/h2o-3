all: clean build

.PHONY: clean
clean:
	./gradlew clean


.PHONY: build
build:
	./gradlew --parallel build -x test -x :h2o-assemblies:minimal:shadowJar -x :h2o-assemblies:steam:shadowJar

.PHONY: minimal
minimal:
	./gradlew -PmainAssemblyName=minimal --parallel build -x test -x :h2o-assemblies:main:shadowJar -x :h2o-assemblies:steam:shadowJar
