# This is a demo of H2O's PCA function
# It imports a data set, parses it, and prints a summary
# Then, it runs PCA on a subset of the features
# Note: This demo runs H2O on localhost:54321
library(h2o)
localH2O = h2o.init(ip = "localhost", port = 54321, startH2O = TRUE)

australia.hex = h2o.uploadFile(localH2O, system.file("extdata", "australia.csv", package="h2o"), "australia.hex")
summary(australia.hex)

australia.pca = h2o.prcomp(australia.hex)
print(australia.pca)
screeplot(australia.pca)

australia.pca2 = h2o.prcomp(australia.hex, tol = 0.5, standardize = FALSE)
print(australia.pca2)
