setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

glmMultinomial <- function() {
  tot <- 1e-5
  D <- h2o.uploadFile(locate("smalldata/covtype/covtype.20k.data"), destination_frame="covtype.hex")  
  D[,55] <- as.factor(D[,55])
  Y <- 55
  X   <- 1:54  
  startval <- c(1:371)*0
  startval[53] <- -1.9166426513747452
  startval[106] <-  -1.200312843494449
  startval[159] <-  -2.2256240518579173
  startval[212] <- -2.2256240518579173
  startval[265] <-  -2.121931593300788
  startval[318] <- -2.2256240518579173
  startval[371] <- -2.2256240518579173
  Log.info("Build the model")
  m1 <- h2o.glm(y = Y, x = X, training_frame = D, family = "multinomial", alpha = 0.99, solver='IRLSM')
  m1coeff <- h2o.coef(m1)
  m2 <- h2o.glm(y = Y, x = X, training_frame = D, family = "multinomial", alpha = 0.99, solver='IRLSM', startval=startval)
  m2coeff <- h2o.coef(m2)
  outerLen <- length(m1coeff)
  innerLen <- length(m1coeff[[1]])
  for (ind in c(2:outerLen)) {
    for (ind2 in c(1:innerLen)) {
      print(m1coeff[[ind]][ind2])
      print(m2coeff[[ind]][ind2])
      expect_true(abs(m1coeff[[ind]][ind2]-m2coeff[[ind]][ind2])<tot)
    }
  }
}

doTest("GLM: Multinomial startval", glmMultinomial)
