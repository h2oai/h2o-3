setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
######################################################################
# Test for HEX-850
######################################################################

# setwd("/Users/tomk/0xdata/ws/h2o/R/tests/testdir_jira")


options(echo=TRUE)


h2oTest.heading("BEGIN TEST")
check.hex_850 <- function() {

  path = h2oTest.locate("smalldata/jira/850.csv")
  j.fv = h2o.importFile(path, destination_frame="jira850.hex")
  h2o.ls()

  if (nrow(j.fv) != 4) {
      stop("j.fv should have 4 rows")
  }

  if (ncol(j.fv) != 3) {
      stop ("j.fv should have 3 cols")
  }

  summary(j.fv)

  rj.fv = as.data.frame(j.fv)
  j.fv$age = j.fv$age + 1
  head(rj.fv)

  coolname = j.fv[2,2]
  rcoolname = (as.data.frame(coolname))
  print(rcoolname)
  if (rcoolname != "orange'jello") {
      stop ("rcoolname mismatch")
  }

  
}

h2oTest.doTest("HEX-850 test", check.hex_850)
