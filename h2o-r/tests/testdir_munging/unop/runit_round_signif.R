setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
##
# Test out the round() and signif() functionality
##


test.round.signif <- function() {

  for (g in c(-231.38523412, 3.1,0,NA)) { 
    run.round.tests(g)
    run.signif.tests(g)
  }
}

run.signif.tests <- function (g) {
  h2o_g <- as.h2o(g)
  #test default
  h2o_and_R_equal(signif(h2o_g), signif(g))
  for (digits in c(-3.5,-7:7,3.5)) {
    h2o_and_R_equal(signif(h2o_g,digits=digits), signif(g, digits=digits))
  }
}

run.round.tests <- function (g) {
  h2o_g <- as.h2o(g)
  #test default
  h2o_and_R_equal(round(h2o_g), round(g))
  for (digits in seq(0,8,by=.5)) {
    h2o_and_R_equal(round(h2o_g,digits=digits), round(g, digits=digits))
  }
}



doTest("Test out the round() and signif() functionality", test.round.signif)
