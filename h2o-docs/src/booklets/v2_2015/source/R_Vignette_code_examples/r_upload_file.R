irisPath = system.file("extdata", "iris.csv", package="h2o")
iris.hex = h2o.uploadFile(path = irisPath, destination_frame = "iris.hex")