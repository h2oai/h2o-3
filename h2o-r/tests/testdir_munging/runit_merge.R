setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
##
# Test out the merge() functionality
##


test.merge <- function() {

  g <- as.data.frame(list(A=c(1,1,2,2,3,3),B=rep(8,6)))
  h <- as.data.frame(list(A=c(1)))
  run.merge.tests(g,h)
  h <- as.data.frame(list(A=c(2:4)))
  run.merge.tests(g,h)
  
}

run.merge.tests <- function (g,h) {
  h2o_g <- as.h2o(g)
  h2o_h <- as.h2o(h)
  for (all.x in c(TRUE,FALSE)) {
    for (all.y in c(TRUE,FALSE)) {
      if (all.x && all.y) next
      h2o_merge <- h2o.merge(h2o_g, h2o_h,all.x=all.x,all.y=all.y)
      R_merge <- merge(g,h,all.x=all.x,all.y=all.y)
      print("")
      print("")
      print(h2o_merge)
      print(R_merge)
      #h2o_and_R_equal(h2o_merge, R_merge[names(h2o_merge)])
    }
  }
}


doTest("Test out the merge() functionality", test.merge)
