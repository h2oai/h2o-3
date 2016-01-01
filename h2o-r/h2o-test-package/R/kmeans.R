h2oTest.checkKMeansModel <- function(myKM.h2o, myKM.r, tol = 0.01) {
  Log.info("R Final Clusters:"); print(myKM.r$centers)
  Log.info("H2O Final Clusters:"); print(getCenters(myKM.h2o))
  expect_equivalent(as.matrix(getCenters(myKM.h2o)), myKM.r$centers)
  
  wssR <- sort.int(myKM.r$withinss)
  wssH2O <- sort.int(getWithinSS(myKM.h2o))
  totssR <- myKM.r$totss
  totssH2O <- getTotSS(myKM.h2o)
  btwssR <- myKM.r$betweenss
  btwssH2O <- getBetweenSS(myKM.h2o)
  
  Log.info(paste("H2O WithinSS : ", wssH2O, "\t\t", "R WithinSS : ", wssR))
  Log.info("Compare Within-Cluster SS between R and H2O\n")  
  expect_equal(wssH2O, wssR, tolerance = tol)
  
  Log.info(paste("H2O TotalSS : ", totssH2O, "\t\t", "R TotalSS : ", totssR))
  Log.info("Compare Total SS between R and H2O\n")
  expect_equal(totssH2O, totssR)
  
  Log.info(paste("H2O BtwSS : ", btwssH2O, "\t\t", "R BtwSS : ", btwssR))
  Log.info("Compare Between-Cluster SS between R and H2O\n")
  expect_equal(btwssH2O, btwssR, tolerance = tol)
}
