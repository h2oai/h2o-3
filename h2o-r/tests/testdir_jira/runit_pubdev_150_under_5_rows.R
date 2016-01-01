setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test_one_file <- function(fnam, mins, maxs) {
  DF <- h2o.importFile(h2oTest.locate(paste0("smalldata/jira/pubdev-150/",fnam,".csv")), paste0(fnam,".hex"))
  raw_payload = .h2o.doSafeREST(urlSuffix = paste0("Frames.json/",fnam,".hex/columns/B/summary"), method = "GET")
  # print(raw_payload)
  json = .h2o.fromJSON(jsonlite::fromJSON(raw_payload,simplifyDataFrame=FALSE))
  expect_equal(as.vector(json$frames[[1]]$columns[[1]]$mins), mins)   # as.vector needs as type of $mins is matrix <5 but vector >=5
  expect_equal(as.vector(json$frames[[1]]$columns[[1]]$maxs), maxs)   #    since [,1] is type error on matrix, use as.vector instead.
}

# The problem was that +Inf was returned in maxs when nrow<5. Similarly -Inf in mins.
# Test the edge cases around 0-5 rows using pre-prepared tiny .csv files of just 2 columns
mytest = function() {
  # test_one_file("test0", mins=c(NA,NA,NA,NA,NA), maxs=c(NA,NA,NA,NA,NA))  # header only fails to read (likely correct behaviour)
  test_one_file("test1", mins=c(2L,NA,NA,NA,NA), maxs=c(2L,NA,NA,NA,NA))
  test_one_file("test2", mins=c(2L,5L,NA,NA,NA), maxs=c(5L,2L,NA,NA,NA))
  test_one_file("test4", mins=c(2L,5L,8L,11L,NA), maxs=c(11L,8L,5L,2L,NA))
  test_one_file("test5", mins=c(2L,5L,8L,11L,14L), maxs=c(14L,11L,8L,5L,2L))
  test_one_file("test6", mins=c(2L,5L,8L,11L,14L), maxs=c(17L,14L,11L,8L,5L))
}

h2oTest.doTest("PUBDEV-150: summary mins and maxs on files of 0 to 6 rows", mytest)

