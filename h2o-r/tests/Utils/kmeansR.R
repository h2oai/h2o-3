checkKMeansModel <- function(myKM.h2o, myKM.r, training_rows, tol = 0.01) {
  Log.info("R Final Clusters:"); print(myKM.r$centers)
  Log.info("H2O Final Clusters:"); print(getCenters(myKM.h2o))
  expect_equivalent(as.matrix(getCenters(myKM.h2o)), myKM.r$centers)
  
  wmseR <- sort.int(myKM.r$withinss/myKM.r$size)
  wmseH2O <- sort.int(getWithinMSE(myKM.h2o))
  totssR <- myKM.r$totss
  totssH2O <- getAvgSS(myKM.h2o)*training_rows
  btwssR <- myKM.r$betweenss
  btwssH2O <- getAvgBetweenSS(myKM.h2o)*training_rows
  
  Log.info(paste("H2O WithinMSE : ", wmseH2O, "\t\t", "R WithinMSE : ", wmseR))
  Log.info("Compare Within-Cluster MSE between R and H2O\n")  
  expect_equal(wmseH2O, wmseR, tolerance = tol)
  
  Log.info(paste("H2O TotalSS : ", totssH2O, "\t\t", "R TotalSS : ", totssR))
  Log.info("Compare Total SS between R and H2O\n")
  expect_equal(totssH2O, totssR)
  
  Log.info(paste("H2O BtwSS : ", btwssH2O, "\t\t", "R BtwSS : ", btwssR))
  Log.info("Compare Between-Cluster SS between R and H2O\n")
  expect_equal(btwssH2O, btwssR, tolerance = tol)
}
