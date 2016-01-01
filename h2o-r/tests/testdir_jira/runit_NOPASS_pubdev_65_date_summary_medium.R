setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pub.65 <- function() {
  fPath <- tryCatch({
    h2oTest.locate("bigdata/laptop/jira/pub_65.csv")
  }, warning= function(w) {
    print("File bigdata/laptop/jira/pub_65.csv could not be found. Please run ./gradlew syncBigdataLaptop (or gradlew.bat syncBigdataLaptop for Windows) to retrieve the file.")
  }, error= function(e) {
    print("File bigdata/laptop/jira/pub_65.csv could not be found.Please run ./gradlew syncBigdataLaptop (or gradlew.bat syncBigdataLaptop for Windows) to retrieve the file.")
  }, finally = {
    
  })
  fzPath <- h2oTest.locate("bigdata/laptop/jira/pub_65.csv.zip")
  
  h2oTest.logInfo("Import data to R and print summary")
  hexR <- read.csv(normalizePath(fPath), header = TRUE)
  hexR$Date <- strptime(hexR$Date, format = "%m/%d/%y %H:%M")
  hexR.sum <- summary(hexR)
  print(hexR.sum)
  
  h2oTest.logInfo("Import data to H2O with key = 'p65' and print summary")
  hex <- h2o.importFile(normalizePath(fzPath), "p65")
  hex.sum <- summary(hex)
  print(hex.sum)
  
  h2oTest.logInfo("Import data to H2O with key = 'p65_dupe' and print summary")
  hex2 <- h2o.importFile(normalizePath(fzPath), "p65_dupe")
  hex2.sum <- summary(hex2)
  print(hex2.sum)
  
  h2oTest.logInfo("Check that H2O summaries are exactly the same")
  expect_equal(hex.sum, hex2.sum)
  
  h2oTest.logInfo("Check that H2O summaries match R")
  h2oTest.checkSummary(hex.sum, hexR.sum)
  
}

h2oTest.doTest("PUBDEV-65: H2O gives inconsistent summaries of Date cols", test.pub.65)
