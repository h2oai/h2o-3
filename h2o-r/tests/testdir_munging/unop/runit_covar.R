setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
##
# Test out the var() functionality
##


test.var <- function() {
  
  
  #1 row case
  n <- 20
  g <- runif(n)
  h <- runif(n)
  run.var.tests(g,h,one_row=TRUE)
  g[4] <- NA
  run.var.tests(g,h,one_row=TRUE, has_nas=TRUE)
  
  #1 column case
  run.var.tests(g,h, has_nas = TRUE)
  g[4] <- runif(1)
  run.var.tests(g,h)
  
  #Matrices
  g <- matrix(runif(n),nrow=4)
  h <- matrix(runif(12),nrow=4)
  run.var.tests(g,h)
  g[2,3] <- NA
  g[1,4] <- NA
  h[2,3] <- NA
  run.var.tests(g,h,has_nas=TRUE)
  
}

run.var.tests <- function (g,h,one_row=FALSE,has_nas=FALSE) {
  uses <- c("everything", "all.obs", "complete.obs", "pairwise.complete.obs")
  if (has_nas) uses <- uses[-2]
  for (na.rm in c(FALSE, TRUE)) {
    for (use in uses) {
      if (one_row) {
        h2o_var <- var(as.h2o(t(g)),as.h2o(t(h)), na.rm = na.rm, use = use)
      } else {
        h2o_var <- var(as.h2o(g),as.h2o(h), na.rm = na.rm, use = use)
      }
      R_var <- var(g,h, na.rm = na.rm, use = use)
      h2o_and_R_equal(h2o_var, R_var)
    }
  }
}


doTest("Test out the var() functionality", test.var)
