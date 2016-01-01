setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.string.concat_to_date <- function(){
  h2oTest.logInfo("Loading in weather data...")
  wthr1 <- h2o.importFile(path = h2oTest.locate("bigdata/laptop/citibike-nyc/31081_New_York_City__Hourly_2013.csv"))

  wthr2 <- wthr1[,c("Year Local","Month Local","Day Local","Hour Local")]
  wthr2$msec <- as.Date(paste(wthr2$"Year Local", wthr2$"Month Local",
                              wthr2$"Day Local", wthr2$"Hour Local",
                              sep = "."), format = "%Y.%m.%d.%h")
  print(wthr2$msec)

  
}

h2oTest.doTest("Turning Separate Columns into a Single Date Columns", test.string.concat_to_date)
