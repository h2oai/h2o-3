setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.pub.696 <- function(conn) {

  a <- h2o.exec(a <- rep_len(0,1000000), h2o = conn)
  b <- h2o.exec(b <- runif(a, -1), h2o = conn)
  
  d <- h2o.exec(d <- cbind(a,b), h2o = conn)

  print(d)
  print(dim(d))

  a2 <- h2o.exec(a2 <- rep_len(0,1000000000))
  b2 <- h2o.exec(b2 <- runif(a, -1))

  testEnd()
}

doTest("cbind of something created by rep_len", test.pub.696 )
