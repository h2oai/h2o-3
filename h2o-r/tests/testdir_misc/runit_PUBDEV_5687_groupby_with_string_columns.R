setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
##
# Test groupby functions on a frame with String columns and make sure a warning
# message was received.  
# 
# When groupby operations are performed on numeric columns, make sure it still returns the
# correct output.a 
##

test <- function(conn) {
  Log.info("Upload dataset with String columns into H2O...")
  df.hex <- h2o.uploadFile(locate("smalldata/jira/test_groupby_with_strings.csv"))

  Log.info("Test method = nrow and median with string columns") # check warning is generated
  expect_warning(gp_nrow <- h2o.group_by(data = df.hex, by = "C1", nrow("C2")))
  expect_warning(gp_nrow <- h2o.group_by(data = df.hex, by = "C1", median("C3")))
  
  Log.info("Test method = nrow and median with numeric columns") # check groupby still works
  gp_nrow <- h2o.group_by(data = df.hex, by = "C1", nrow("C4"))
  gp_nrow <- as.data.frame(gp_nrow)[,2]
  r_nrow  <- c(1,2,3,4,5,6)
  print(r_nrow)
  print(gp_nrow)
  checkEqualsNumeric(gp_nrow, r_nrow)
}

doTest("Testing different methods for groupby for dataset with String columns:", test)

