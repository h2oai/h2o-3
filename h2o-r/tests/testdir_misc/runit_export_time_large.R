setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


#The below function will test if the exporting of a file in H2O is NOT asynchronous. Meaning the h2o.export_file()
#waits for the entire process to finish before exiting.

test.export.time <- function() {

  fr = h2o.importFile(locate("bigdata/laptop/citibike-nyc/2013-07.csv"))
  
  t = system.time(h2o.exportFile(fr,paste0(sandbox(),"foo",sep=.Platform$file.sep),force=T))
  
  print(paste("Time to export is",t[3],"seconds"))
  expect_true(t[3]>2)
  
}

doTest("Testing Exporting File Time", test.export.time)
