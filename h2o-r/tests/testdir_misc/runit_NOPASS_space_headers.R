setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.bad.headers <- function(conn) {
  citibikePath <- locate("smalldata/jira/citibike_head.csv")
  # summary use to fail on datasets that had spaces in the headers during import.
  f <- h2o.importFile(conn, citibikePath , key = "citibike.hex")  
  f.df <- read.csv(citibikePath)
  r.median <- median(f.df$start.station.id)
  if(!class(f[,"start station id"])=="H2OFrame") stop("Didn't subset column correctly")
  h2o.median <- median(f[,"start station id"])
  checkEqualsNumeric(r.median, h2o.median)
  testEnd()
}

doTest("Run summary on dataset with spaces : ", test.bad.headers)