irisPath = system.file("extdata", "iris_wheader.csv", package="h2o")
iris.hex = h2o.importFile(path = irisPath)
h2o.anyFactor(iris.hex)