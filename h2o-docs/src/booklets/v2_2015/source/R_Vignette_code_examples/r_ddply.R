library(h2o)
h2o.init(nthreads = -1)

# Import iris dataset to H2O
irisPath = system.file("extdata", "iris_wheader.csv", package = "h2o")
iris.hex = h2o.importFile(path = irisPath, destination_frame = "iris.hex")

# Apply function to groups by class of flower
# uses h2o's ddply, since iris.hex is an H2OFrame object
res = h2o.ddply(iris.hex, "class", function(df) { sum(df[,1], na.rm = T)/nrow(df) })
head(res)