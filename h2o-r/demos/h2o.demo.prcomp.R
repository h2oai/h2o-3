library(h2o)
h2o.init()

ausPath <- h2o:::.h2o.locate("smalldata/junit/australia.csv")
print(paste("Uploading", ausPath))
australia.hex <- h2o.uploadFile(path = ausPath, destination_frame = "australia.hex")

print("Print out summary of australia.csv")
print(summary(australia.hex))

print("Run PCA with k = 8, gamma = 0, transform = 'DEMEAN'")
australia.pca = h2o.prcomp(australia.hex, k = 8, transform = "DEMEAN")
print(australia.pca)

print("Run PCA with k = 4, transform = 'STANDARDIZE'")
australia.pca2 = h2o.prcomp(australia.hex, k = 4, transform = "STANDARDIZE")
print(australia.pca2)