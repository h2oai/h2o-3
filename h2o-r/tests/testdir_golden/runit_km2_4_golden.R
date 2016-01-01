setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.kmsplit.golden <- function() {
  library(flexclust)
  h2oTest.logInfo("Importing ozone.csv data...\n")
  ozoneR <- read.csv(h2oTest.locate("smalldata/glm_test/ozone.csv"), header = TRUE)
  ozoneH2O <- h2o.uploadFile(h2oTest.locate("smalldata/glm_test/ozone.csv"))
  
  # to get deterministic results, don't randomly split. use full dataset for test/train
  # h2oTest.logInfo("Split into test and training sets\n")
  # trainIdx <- sort(sample(nrow(ozoneR), round(0.75*nrow(ozoneR))))
  # testIdx <- sort(setdiff(1:nrow(ozoneR), trainIdx))
  # trainR <- ozoneR[trainIdx,]; testR <- ozoneR[testIdx,]
  # trainH2O <- ozoneH2O[trainIdx,]; testH2O <- ozoneH2O[testIdx,]
  trainR <- ozoneR; testR <- ozoneR
  trainH2O <- ozoneH2O; testH2O <- ozoneH2O

  # a random sample here, is no different than random init to kmeans
  # h2o might not get the desired centers with random init
  # startIdx <- sort(sample(1:nrow(trainR), 3))

  # was getting "close" center agreement, but one or two predict miscompared
  # switched to fixed random init
  # dataset has 111 data rows. seem randomly ordered
  startIdx <- c(1,20,100)
    
  h2oTest.logInfo("Initial cluster centers:"); print(trainR[startIdx,])
  # fitR <- kmeans(trainR, centers = trainR[startIdx,], iter.max = 1000, algorithm = "Lloyd")
  fitR <- kcca(trainR, k = as.matrix(trainR[startIdx,], family = kccaFamily("kmeans"), control = list(iter.max = 1000)))
  fitH2O <- h2o.kmeans(trainH2O, init = trainH2O[startIdx,], standardize = FALSE)
  
  h2oTest.logInfo("R Final Clusters:"); print(fitR@centers)
  h2oTest.logInfo("H2O Final Clusters:"); print(getCenters(fitH2O))
  expect_equivalent(as.matrix(getCenters(fitH2O)), fitR@centers)
  
  h2oTest.logInfo("Compare Predicted Classes on Test Data between R and H2O\n")
  classR <- predict(fitR, testR)
  # FIXME: predict directly on sliced H2O frame breaks
  # classH2O <- predict(fitH2O, testH2O)
  classH2O <- predict(fitH2O, as.h2o(testR))
  # expect_equivalent(as.numeric(as.matrix(classH2O))+1, classR)
  # H2O indexes from 0, but R indexes from 1
  forCompareH2O <- as.matrix(classH2O)+1
  forCompareR <- as.matrix(classR)
  notMatchingH2O <- forCompareH2O[forCompareH2O != forCompareR]
  notMatchingR <- forCompareR[forCompareH2O != forCompareR]

  h2oTest.logInfo("dim/head forCompareH2O:")
  h2oTest.logInfo(dim(forCompareH2O))
  h2oTest.logInfo(head(forCompareH2O))

  h2oTest.logInfo("dim/head forCompareR:")
  h2oTest.logInfo(dim(forCompareR))
  h2oTest.logInfo(head(forCompareR))

  h2oTest.logInfo("dim/head notMatchingH2O:")
  h2oTest.logInfo(dim(notMatchingH2O))
  h2oTest.logInfo(head(notMatchingH2O))

  h2oTest.logInfo("dim/head notMatchingR:")
  h2oTest.logInfo(dim(notMatchingR))
  h2oTest.logInfo(head(notMatchingR))

  h2oTest.logInfo(all.equal(forCompareH2O, forCompareR, check.attributes=FALSE))

  # one has dim names, the other doesn't. will get length error unless..
  # default tolerance is close to 1.5e-8. but should be comparing integers
  expect_true(all.equal(forCompareH2O, forCompareR, check.attributes=FALSE))
  
  
}

h2oTest.doTest("KMeans Test: Golden Kmeans - Ozone Test/Train Split without Standardization", test.kmsplit.golden)
