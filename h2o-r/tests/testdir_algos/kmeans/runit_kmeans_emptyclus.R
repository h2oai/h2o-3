setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.km.empty <- function() {
  Log.info("Importing ozone.csv data...\n")
  ozoneR <- read.csv(locate("smalldata/glm_test/ozone.csv"), header = TRUE)
  ozoneH2O <- h2o.uploadFile( locate("smalldata/glm_test/ozone.csv"))
  ozoneScale <- scale(ozoneR, center = TRUE, scale = TRUE)
  
  ncent <- 10
  nempty <- sample(1:(ncent/2), 1)
  initCent <- ozoneScale[1:ncent,]
  for(i in sample(1:ncent, nempty))
    initCent[i,] = rep(100*i, ncol(ozoneScale))
  Log.info("Initial cluster centers:"); print(initCent)
  
  # H2O can handle empty clusters, while R throws an error
  Log.info("Check that H2O can handle badly initialized centers")
  expect_error(kmeans(ozoneScale, init = initCent, iter.max = 1000, algorithm = "Lloyd"))
  fitKM <- h2o.kmeans(ozoneH2O, init = initCent, standardize = TRUE)
  print(fitKM)
  
}

doTest("KMeans Test: Handle multiple empty clusters", test.km.empty)
