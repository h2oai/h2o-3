


get.eval.result <- function(expr) {
  res <-  .h2o.__exec2(expr)
  return(new("H2OFrame", h2o=conn, key=res$dest_key))
}


# UNIMPL: round, trunc, signif

# use this for interactive setup
#        library(h2o)
#        library(testthat)
#        h2o.startLogging()
#        conn <- h2o.init()

test.round_prec <- function() {
    a <- c(5.5, 2.5, 1.6, 1.1, 1.0, -1.0, -1.1, -1.6, -2.5, -5.5)
    b <- pi * 100^(-1:3)
    
    A <- as.h2o(a, "A")
    B <- as.h2o(b, "B")
    
    s1_t <- trunc(a); s1_r <- round(a); s1_s <- signif(a)
    s2_t <- trunc(b); s2_r <- round(b,3); s2_s <- signif(b,3)
    
    Log.info(paste("A =", paste(a, collapse <- ", ")))
    Log.info("Check trunc(A) matches R")
    S1_t <- as.data.frame(trunc(A))
    expect_true(all(S1_t == s1_t))
    
    Log.info("Check round(A, 0) matches R")
    S1_r <- as.data.frame(round(A, 0))
    expect_true(all(S1_r == s1_r))
    
    Log.info("Check signif(A, 6) matches R")
    S1_s <- as.data.frame(signif(A, 6))
    expect_true(all(S1_s == s1_s))
    
    Log.info(paste("B =", paste(b, collapse <- ", ")))
    Log.info("Check trunc(B) matches R")
    S2_t <- as.data.frame(trunc(B))
    expect_true(all(S2_t == s2_t))
    
    Log.info("Check round(B, 3) matches R")
    S2_r <- as.data.frame(round(B, 3))
    expect_true(all(S2_r == s2_r))
    
    Log.info("Check signif(B, 3) matches R")
    S2_s <- as.data.frame(signif(B, 3))
    expect_true(all(S2_s == s2_s))
    
}

doTest("Test trunc, round and signif", test.round_prec)
