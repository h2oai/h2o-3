# This is a demo of H2O's K-Means function
# It imports a data set, parses it, and prints a summary
# Then, it runs K-Means with k = 5 centers on a subset of characteristics
# Note: This demo runs H2O on localhost:54321
library(h2o)
h2o.init()

prostate.hex = h2o.uploadFile(path = system.file("extdata", "prostate.csv", package="h2o"), destination_frame = "prostate")
summary(prostate.hex)
prostate.km = h2o.kmeans(prostate.hex, k = 10, x = c("AGE","RACE","GLEASON","CAPSULE","DCAPS"))
print(prostate.km)

prostate.data = as.data.frame(prostate.hex)
# prostate.clus = as.data.frame(prostate.km@model$cluster)

# Plot categorized data
# if(!"fpc" %in% rownames(installed.packages())) install.packages("fpc")
# if("fpc" %in% rownames(installed.packages())) {
#  library(fpc)

#  par(mfrow=c(1,1))
#  plotcluster(prostate.data, prostate.clus[,1])
#  title("K-Means Classification for k = 10")
# }

# if(!"cluster" %in% rownames(installed.packages())) install.packages("cluster")
# if("cluster" %in% rownames(installed.packages())) {
#  library(cluster)
#  clusplot(prostate.data, prostate.clus[,1], color = TRUE, shade = TRUE)
# }
# pairs(prostate.data[,c(2,3,7,8)], col=prostate.clus[,1])

# Plot k-means centers
par(mfrow = c(1,2))
prostate.ctrs = as.data.frame(prostate.km@model$centers)
plot(prostate.ctrs[,1:2])
plot(prostate.ctrs[,3:4])
title("K-Means Centers for k = 10", outer = TRUE, line = -2.0)
