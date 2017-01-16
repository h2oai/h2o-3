setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


LogLikelihood<- function(beta, Y, X){
  pi<- plogis( X%*%beta )
  pi[pi==0] <- .Machine$double.neg.eps
  pi[pi==1] <- 1-.Machine$double.neg.eps
  logLike<- sum( Y*log(pi)  + (1-Y)*log(1-pi)  )
  return(-logLike)
}
###########
# grad- corresponding function to calculate the gradient
# other optimization routines (e.g. Nelder-Mead) do not use the gradient
######
grad<- function(beta, Y, X){
  pi<- plogis( X%*%beta )        # P(Y|A,W)= expit(beta0 + beta1*X1+beta2*X2...)
  pi[pi==0] <- .Machine$double.neg.eps        # for consistency with above
  pi[pi==1] <- 1-.Machine$double.neg.eps
  gr<- crossprod(X, Y-pi)        # gradient is -residual*covariates
  return(-gr)
}


test.GLM.quasi_binomial <- function() {
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
  sum(Y)
  # 29
  # Qbounds (l,u)= (0,0.065)
  l=0; u=0.065
  #create the design matrix
  X <- model.matrix(as.formula(Y~W1+W2+W3+A*W1))
  # transform Y to Y.tilde in between (l,u)
  Y.tilde<- (Y - l)/(u-l)
  summary(Y.tilde)
  # Min. 1st Qu.  Median    Mean 3rd Qu.    Max.
  # 0.0000  0.0000  0.0000  0.1785  0.0000 15.3800
  # call to the optim function.
  # par: initial parameter estimates; f:function to minimize; gr: gradient
  # arguments to LogLikelihood() & grad() are Y and X
  optim.out <- optim(par=rep(0, ncol(X)), fn=LogLikelihood, gr=grad,
                     Y=Y.tilde, X=X, method="BFGS")
  # see optim help files for more details and other optimization routines
  # get parameter estimates
  beta<- optim.out$par

  # now H2O
  hf = as.h2o(cbind(Y.tilde,X))
  x = 2:7
  y = 1
  m_h2o = h2o.glm(training_frame = hf,x=x,y=y,family='quasibinomial',standardize=F,lambda=0)
  m_h2o2 = h2o.glm(training_frame = hf,x=x,y=y,family='quasibinomial',standardize=F,lambda=0,solver='L_BFGS')
  beta_h2o_1 = m_h2o@model$coefficients
  beta_h2o_2 = m_h2o2@model$coefficients
  betas = cbind(beta,beta_h2o_1,beta_h2o_2)
  colnames(betas) <- c("R","H2O-IRLSM","H2O-L_BFGS")
  print(betas)

  l0 = LogLikelihood(beta,Y.tilde,X)
  l1 = LogLikelihood(beta_h2o_1,Y.tilde,X)
  l2 = LogLikelihood(beta_h2o_2,Y.tilde,X)
  ls = c(l0,l1,l2)
  names(ls) <- colnames(betas)
  print(l2)
  expect_equal(l0,l1,tolerance=1e-4)
  expect_equal(l0,l2,tolerance=1e-4)
}

doTest("GLM Test: quasi binomial", test.GLM.quasi_binomial)
