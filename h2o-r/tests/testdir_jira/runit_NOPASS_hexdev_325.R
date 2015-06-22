setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.parse.mismatching.col.length <- function(conn){

  df <- h2o.importFile(locate("bigdata/laptop/jira/hexdev_325.csv"), header = TRUE)
  print(head(df, 2))

  testEnd()
}

doTest("Testing Parsing of Mismatching Header and Data length", test.parse.mismatching.col.length)