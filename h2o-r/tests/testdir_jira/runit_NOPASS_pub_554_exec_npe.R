setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.ifce<- function(conn) {

  r.hex <- as.h2o(conn, iris)
  #ifelse(1, r.hex, r.hex + 1)[1,2]
  r.hex[2+4,-4] + 5
  h2o.exec(ifelse(1, r.hex, (r.hex + 1))[1,2])
  h2o.exec(r.hex[2+4,-4] + 5)

  testEnd()
}

doTest("test ifce", test.ifce)
