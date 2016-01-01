setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.km.empty <- function() {
  h2oTest.logInfo("Importing ozone.csv data...\n")
  ozoneR <- read.csv(h2oTest.locate("smalldata/glm_test/ozone.csv"), header = TRUE)
  ozoneH2O <- h2o.uploadFile( h2oTest.locate("smalldata/glm_test/ozone.csv"))
  ozoneScale <- scale(ozoneR, center = TRUE, scale = TRUE)
  
  ncent <- 10
  nempty <- sample(1:(ncent/2), 1)
  initCent <- ozoneScale[1:ncent,]
  for(i in sample(1:ncent, nempty))
    initCent[i,] = rep(100*i, ncol(ozoneScale))
  h2oTest.logInfo("Initial cluster centers:"); print(initCent)
  
  # H2O can handle empty clusters, while R throws an error
  h2oTest.logInfo("Check that H2O can handle badly initialized centers")
  expect_error(kmeans(ozoneScale, init = initCent, iter.max = 1000, algorithm = "Lloyd"))
  fitKM <- h2o.kmeans(ozoneH2O, init = initCent, standardize = TRUE)
  print(fitKM)
  
}

h2oTest.doTest("KMeans Test: Handle multiple empty clusters", test.km.empty)
