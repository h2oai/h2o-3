setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.ifelse.bug.fix <- function() {
  browser()
  
  df <- as.h2o(data.frame(a = c("a", "b", "c", "a", "b", "c"), stringsAsFactors = TRUE))
  
  c1 <- h2o.ifelse(df$a == "a", "d", df$a) # this works, d/b/c/d/b/c
  c2 <- h2o.ifelse(df$a == "a", df$a, "f") # this works too, a/f/f/a/f/f
  
  df <- as.h2o(data.frame(a = c("a", "a", "a"), stringsAsFactors = TRUE))
  
  c3 <- h2o.ifelse(df$a == "a", "d", df$a) # this works, d/d/d
  c4 <- h2o.ifelse(df$a == "a", df$a, "d") # this does not, 0/0/0
  
  
  df = as.h2o(data.frame(a = c("a", "b", "d")))
  c6 <- h2o.ifelse(df$a == "c", "d", df$a)

}

doTest("Test ifelse not to change column type", test.ifelse.bug.fix)
