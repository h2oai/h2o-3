
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../findNSourceUtils.R')

# use this for interactive setup
#       library(h2o)
#       library(testthat)
#       h2o.setLogPath(getwd(), "Command")
#       h2o.setLogPath(getwd(), "Error")
#       h2o.startLogging()
#       conn = h2o.init()

test.cbind <- function(conn) {

    a = c(0,0,0)
    b = a
    h = cbind(a,b)
    a.h2o <- c(0,0,0)
    b.h2o = a.h2o
    h.h2o = cbind(a.h2o,b.h2o)
    h.h2o.R = as.matrix(h.h2o)
    expect_that(all(h == h.h2o.R), equals(T))


    a = c(0,0,0)
    b = c(0,0,0)
    h = cbind(a,b)
    a.h2o = c(0,0,0)
    b.h2o = c(0,0,0)
    h.h2o = cbind(a.h2o,b.h2o)
    h.h2o.R = as.matrix(h.h2o)
    expect_that(all(h == h.h2o.R), equals(T))


    h <- cbind(c(0,0,0), c(1,1,1))
    a=c(0,0,0); b=c(0,0,0); d=c(0,0,0); e=c(0,0,0); f=c(0,0,0); g= c(0,0,0);
    b=a; d=a; f=a; g=a;
    h <- cbind(a, b)
    h <- cbind(a, b, d)
    h <- cbind(a, b, d, e)
    h <- cbind(a, b, d, e, f)
    h <- cbind(a, b, d, e, f, g)
    h.h2o <- cbind(c(0,0,0), c(1,1,1))
    a.h2o=c(0,0,0); b.h2o=c(0,0,0); d.h2o=c(0,0,0); e.h2o=c(0,0,0); f.h2o=c(0,0,0); g.h2o= c(0,0,0);
    b.h2o=a.h2o; d.h2o=a.h2o; f.h2o=a.h2o; g.h2o=a.h2o;
    h.h2o <- cbind(a.h2o, b.h2o)
    h.h2o <- cbind(a.h2o, b.h2o, d.h2o)
    h.h2o <- cbind(a.h2o, b.h2o, d.h2o, e.h2o)
    h.h2o <- cbind(a.h2o, b.h2o, d.h2o, e.h2o, f.h2o)
    h.h2o <- cbind(a.h2o, b.h2o, d.h2o, e.h2o, f.h2o, g.h2o)
    h.h2o.R = as.matrix(h.h2o)
    expect_that(all(h == h.h2o.R), equals(T))


    testEnd()
}

doTest("Test cbind.", test.cbind)


