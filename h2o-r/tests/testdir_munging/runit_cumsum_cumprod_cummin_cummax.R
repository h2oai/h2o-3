setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.cumsumminprodmax <- function() {
   #Test when axis = 0, columnar
   x = seq(0,9)
   y = seq(9,0)

   fr = as.h2o(cbind(x,y))

   cumsum1 = h2o.cumsum(fr[,1])
   cummin1 = h2o.cummin(fr[,1])
   cumprod1 = h2o.cumprod(fr[1:9,1])
   cummax1 = h2o.cummax(fr[,1])

   cumsum2 = base::cumsum(as.data.frame(fr[,2]))
   cummin2 = base::cummin(as.data.frame(fr[,2]))
   cumprod2 = base::cumprod(as.data.frame(fr[1:9,1]))
   cummax2 = base::cummax(as.data.frame(fr[,2]))

   expect_equal(cumsum1[10,1], cumsum2[10,1])
   expect_equal(cummin1[10,1], cummin2[10,1])
   expect_equal(cummax1[10,1], cummax2[10,1])
   expect_equal(cumprod1[9,1],cumprod2[9,1])

   #Test when axis = 1, row wise
   a = seq(0,9)
   b = seq(9,0)
   c = seq(0,9)
   d = seq(9,0)

   fr = as.h2o(cbind(a,b,c,d))

   cumsum1 = h2o.cumsum(fr[1,],axis=1)
   cummin1 = h2o.cummin(fr[2,],axis=1)
   cumprod1 = h2o.cumprod(fr[3,],axis=1)
   cummax1 = h2o.cummax(fr[4,],axis=1)

   cumsum2 = base::cumsum(as.data.frame(t(fr[1,])))
   cummin2 = base::cummin(as.data.frame(t(fr[2,])))
   cumprod2 = base::cumprod(as.data.frame(t(fr[3,])))
   cummax2 = base::cummax(as.data.frame(t(fr[4,])))

   expect_equal(as.data.frame(t(cumsum1)), cumsum2)
   expect_equal(as.data.frame(t(cummin1)), cummin2)
   expect_equal(as.data.frame(t(cummax1)), cummax2)
   expect_equal(as.data.frame(t(cumprod1)),cumprod2)

}

doTest("Test cumsum,cumprod,cummin, and cummax", test.cumsumminprodmax)