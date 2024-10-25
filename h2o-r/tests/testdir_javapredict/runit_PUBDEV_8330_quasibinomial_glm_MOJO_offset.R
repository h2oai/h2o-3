setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.GLM.offset.quasibinomial <- function() {
  n=2500
  W1<- rnorm(n, 0, .25)
  W2<- runif(n, 0, 1)
  W3<- rbinom(n, size=1, 0.5)
  A<- rbinom(n, size=1, prob= plogis(-.5+ W1+W2+W3) )
  pi<- plogis(-3+ 2*A + 1*W1+2*W2-4*W3 + .5*A*W1)/15
  Y<- rbinom(n, size=1, prob= pi)
  sum(Y)
  # 29
  # Qbounds (l,u)= (0,0.065)
  l=0; u=0.065
  #create the design matrix
  X <- model.matrix(as.formula(Y~W1+W2+W3+A*W1))
  # transform Y to Y.tilde in between (l,u)
  Y.tilde<- (Y - l)/(u-l)
  
  # now H2O
  hf <- as.h2o(cbind(Y.tilde, X))
  offset <- random_dataset_real_only(h2o.nrow(hf), 1, realR = 3)
  hf <- h2o.cbind(hf, offset)
  x <- 2:7
  y <- 1
  xOffset <- 2:8

  params                  <- list()
  params$training_frame <- hf
  params$x <- x
  params$y <- y
  params$family <- "quasibinomial"
  params$offset_column <- "C1"
  modelAndDir<-buildModelSaveMojoGLM(params) # build the model and save mojo
  filename <- sprintf("%s/in.csv", modelAndDir$dirName) # save the test dataset into a in.csv file.
  h2o.downloadCSV(hf[1:100, xOffset], filename)
  twoFrames<-mojoH2Opredict(modelAndDir$model, modelAndDir$dirName, filename, col.types=c("numeric", "numeric", "numeric")) # perform H2O and mojo prediction and return frames
  h2o.downloadCSV(twoFrames$h2oPredict, sprintf("%s/h2oPred.csv", modelAndDir$dirName))
  h2o.downloadCSV(twoFrames$mojoPredict, sprintf("%s/mojoOut.csv", modelAndDir$dirName))
  predictFrame <- twoFrames$h2oPredict[, 2:3]
  mojoFrame <- twoFrames$mojoPredict[, 2:3]
  compareFrames(predictFrame, mojoFrame, prob=1, tolerance = 1e-6)
}

doTest("GLM Test: quasibinomial GLM Mojo with offset", test.GLM.offset.quasibinomial)
