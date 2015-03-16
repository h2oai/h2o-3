# This is a demo of H2O's PCA function
# It imports a data set, parses it, and prints a summary
# Then, it runs PCA on a subset of the features
library(h2o)
myIP = readline("Enter IP address of H2O server: ")
myPort = readline("Enter port number of H2O server: ")
remoteH2O = h2o.init(ip = myIP, port = as.numeric(myPort), startH2O = FALSE)

australia.hex = h2o.uploadFile(remoteH2O, system.file("extdata", "australia.csv", package="h2o"), "australia.hex")
summary(australia.hex)

australia.pca = h2o.prcomp(australia.hex, k = 8)
print(australia.pca)

australia.pca2 = h2o.prcomp(australia.hex, k = 4, gamma = 0.5, center = TRUE, scale. = TRUE)
print(australia.pca2)