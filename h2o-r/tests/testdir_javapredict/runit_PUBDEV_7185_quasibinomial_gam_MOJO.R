setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.GAM.quasibinomial <- function() {
  # First calculate paper version
  # From TLMSE paper (Estimating Effects on Rare Outcomes: Knowledge is Power, Laura B. Balzer, Mark J. van der Laan)
  # Example: Data generating experiment for Simulation 1
  set.seed(123)
  n=2500
  W1<- rnorm(n, 0, .25)
  W2<- runif(n, 0, 1)
  W3<- rbinom(n, size=1, 0.5)
  A<- rbinom(n, size=1, prob= plogis(-.5+ W1+W2+W3) )
  pi<- plogis(-3+ 2*A + 1*W1+2*W2-4*W3 + .5*A*W1)/15
  Y<- rbinom(n, size=1, prob= pi)
  # Qbounds (l,u)= (0,0.065)
  l=0; u=0.065
  #create the design matrix
  X <- model.matrix(as.formula(Y~W1+W2+W3+A*W1))
  # transform Y to Y.tilde in between (l,u)
  Y.tilde<- (Y - l)/(u-l)
  summary(Y.tilde)

  # now H2O
  hf = as.h2o(cbind(Y.tilde,X))
  htest = as.h2o(cbind(Y.tilde,X))
  x = 2:7
  y = 1

  params                  <- list()
  params$missing_values_handling <- 'MeanImputation'
  params$training_frame <- hf
  params$x <- x
  params$y <- y
  params$family <- "quasibinomial"
  params$gam_columns <- c("W2")
  params$num_knots <- c(5)
  params$scale <- c(0.001)
  modelAndDir<-buildModelSaveMojoGAM(params) # build the model and save mojo
  filename = sprintf("%s/in.csv", modelAndDir$dirName) # save the test dataset into a in.csv file.
  h2o.downloadCSV(htest[1:100, x], filename)
  twoFrames<-mojoH2Opredict(modelAndDir$model, modelAndDir$dirName, filename, col.types=c("enum", "numeric", "numeric")) # perform H2O and mojo prediction and return frames
  h2o.downloadCSV(twoFrames$h2oPredict, sprintf("%s/h2oPred.csv", modelAndDir$dirName))
  h2o.downloadCSV(twoFrames$mojoPredict, sprintf("%s/mojoOut.csv", modelAndDir$dirName))
  twoFrames$h2oPredict[,1] <- h2o.asfactor(twoFrames$h2oPredict[,1])
  compareFrames(twoFrames$h2oPredict,twoFrames$mojoPredict, prob=1, tolerance = 1e-6)
}

doTest("GAM Test: quasibinomial GAM Mojo", test.GAM.quasibinomial)
