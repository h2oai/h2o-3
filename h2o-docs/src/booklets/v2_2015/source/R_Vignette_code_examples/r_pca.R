ausPath = system.file("extdata", "australia.csv", package="h2o")
australia.hex = h2o.importFile(path = ausPath)

australia.pca <- h2o.prcomp(training_frame = australia.hex, transform = "STANDARDIZE",k = 3)
australia.pca