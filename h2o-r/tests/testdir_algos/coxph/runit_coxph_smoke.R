setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.CoxPH.smoke <- function() {
  heart <- h2o.importFile(locate("smalldata/coxph_test/heart.csv"))
  coxph.model <- h2o.coxph(x="age", event_column="event", start_column="start", stop_column="stop", training_frame=heart)
  expect_equal(as.character(class(coxph.model)), "H2OCoxPHModel")
}

doTest("CoxPH: Smoke Test", test.CoxPH.smoke)