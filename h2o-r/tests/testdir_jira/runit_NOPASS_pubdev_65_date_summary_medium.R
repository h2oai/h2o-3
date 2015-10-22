


test.pub.65 <- function() {
  fPath <- tryCatch({
    locate("bigdata/laptop/jira/pub_65.csv")
  }, warning= function(w) {
    print("File bigdata/laptop/jira/pub_65.csv could not be found. Please run ./gradlew syncBigdataLaptop (or gradlew.bat syncBigdataLaptop for Windows) to retrieve the file.")
  }, error= function(e) {
    print("File bigdata/laptop/jira/pub_65.csv could not be found.Please run ./gradlew syncBigdataLaptop (or gradlew.bat syncBigdataLaptop for Windows) to retrieve the file.")
  }, finally = {
    
  })
  fzPath <- locate("bigdata/laptop/jira/pub_65.csv.zip")
  
  Log.info("Import data to R and print summary")
  hexR <- read.csv(normalizePath(fPath), header = TRUE)
  hexR$Date <- strptime(hexR$Date, format = "%m/%d/%y %H:%M")
  hexR.sum <- summary(hexR)
  print(hexR.sum)
  
  Log.info("Import data to H2O with key = 'p65' and print summary")
  hex <- h2o.importFile(normalizePath(fzPath), "p65")
  hex.sum <- summary(hex)
  print(hex.sum)
  
  Log.info("Import data to H2O with key = 'p65_dupe' and print summary")
  hex2 <- h2o.importFile(normalizePath(fzPath), "p65_dupe")
  hex2.sum <- summary(hex2)
  print(hex2.sum)
  
  Log.info("Check that H2O summaries are exactly the same")
  expect_equal(hex.sum, hex2.sum)
  
  Log.info("Check that H2O summaries match R")
  checkSummary(hex.sum, hexR.sum)
  
}

doTest("PUBDEV-65: H2O gives inconsistent summaries of Date cols", test.pub.65)
