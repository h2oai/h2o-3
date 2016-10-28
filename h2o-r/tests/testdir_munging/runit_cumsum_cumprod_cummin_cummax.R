setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.cumsumminprodmax <- function() {
   x = seq(0,9)
   y = seq(9,0)

   fr = as.h2o(cbind(x,y))

   cumsum1 = h2o.cumsum(fr[,1])
   cummin1 = h2o.cummin(fr[,1])
   cumprod1 = h2o.cumprod(fr[1:9,1])
   cummax1 = h2o.cummax(fr[,1])

   cumsum2 = h2o.cumsum(fr[,2])
   cummin2 = h2o.cummin(fr[,2])
   cumprod2 = h2o.cumprod(fr[1:9,1])
   cummax2 = h2o.cummax(fr[,2])

   expect_equal(cumsum1[10,1], cumsum2[10,1])
   expect_equal(cummin1[10,1], cummin2[10,1])
   expect_equal(cummax1[10,1], cummax2[10,1])
   expect_equal(cumprod1[9,1],cumprod2[9,1])

}

doTest("Test cumsum,cumprod,cummin, and cummax", test.cumsumminprodmax)