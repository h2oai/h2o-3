setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.glm2RidgeObjective.golden <- function() {
	
#IMPORT DATA:
h2oTest.logInfo("Importing handmade data...") 
handmadeH2O<- h2o.uploadFile(h2oTest.locate("smalldata/glm_test/handmade.csv"), destination_frame="handmade")
handmadeR<- read.csv(h2oTest.locate("smalldata/glm_test/handmade.csv"))

Xvars<- as.matrix(cbind(handmadeR$a, handmadeR$b, handmadeR$c, handmadeR$d, handmadeR$e))
Xvars.sc<- scale(Xvars)
Yvar<- as.matrix(handmadeR$f)
hX=c("a", "b", "c", "d", "e")
hy="f"

#GLMNET RIDGE
ridgeGLMNet <- function (X,y,L){
        junk=glmnet(X,y,family='gaussian',alpha=0, lambda=L, standardize=F)
        res=predict(junk,type="coef",s=L)
	beta <- c(res[-1],res[1])
	beta;
}

#H2O RIDGE
ridgeH2O <- function (X,y,L){
  fitH2O=h2o.glm(X, y, training_frame=handmadeH2O, nfolds=0, alpha=0, lambda=L, family="gaussian", standardize=T)        
  betah <- fitH2O@model$coefficients_table$'Norm Coefficients'[hX]
  
  betah <- c(betah, fitH2O@model$coefficients_table$'Norm Coefficients'[length(fitH2O@model$coefficients_table$'Norm Coefficients')])
  names(betah) <- c(hX,"Intercept")    
 

  print("DEBUG CHECKKING")
  print(fitH2O@model$coefficients_table$'Norm Coefficients')
  print(t(betah))
 
  t(betah)
}

#RT made ridge reg function
#assumes cols of X are centered and does not penalize int
# if L >10e9, I assume that L=infty
#   with X centered, est of intcp is just ybar
ridgeLinear<-
function(x, y, L)
{
ybar=mean(y)
coef=c(ybar,rep(0,ncol(x)))
if(L< 10e9){
       m <- t(x) %*% x + L * diag(ncol(x))
       coef <- solve(m, t(x) %*% (y-ybar))
       coef=c(coef,ybar)
names(coef)=c(paste("b",as.character(1:ncol(x)),sep=""),"b0")
}
coef
}
 
ridgeAnalytical  <- function(X,y,L){
	nobs <- dim(X)[1];
	ncols <- dim(X)[2];
	intercept <- rep(1, nobs)
	lMatrx <- diag(ncols+1);
	lMatrx[ncols+1,ncols+1] <- 0; # do not penalize intercept
	x <- cbind(as.matrix(X),intercept);
	gram <- t(x) %*% x;
	xy <- t(x) %*% y;
	beta <- solve(gram/nobs + L*lMatrx,xy/nobs);
	beta;
}

#OBJECTIVE FUNS
sqerr <- function(x,y,beta){
	reziduals <- x %*% beta - y;
	rss <- t(reziduals) %*% (reziduals);
	rss;
}

l2pen <- function(beta)(t(beta[1:length(beta)-1])%*%beta[1:length(beta)-1]);

glmnetObjective <- function(beta,X,y,lambda){
	nobs <- dim(X)[1];	
	ncols<- dim(X)[2]; 
	x <- cbind(as.matrix(X),1);
	0.5*(sqerr(x,y,beta)/nobs + l2pen(beta)*lambda); 
}

ridgeObjective <- function(beta,X,y,lambda){
    t(X)
	nobs <- dim(X)[1];	
	ncols<- dim(X)[2]; 
	x <- cbind(as.matrix(X),1);
	0.5*(sqerr(x,y,beta) + l2pen(beta)*lambda); 
}

#BUILDING MODELS:

glmnet.fit<- ridgeGLMNet(X=Xvars.sc, y=Yvar, L=0)
ridgelin.fit<- ridgeLinear(x=Xvars.sc, y=Yvar, L=0)
ridgeAnal.fit<- ridgeAnalytical(X=Xvars.sc, y=Yvar, L=0)
ridgeH2O.fit<- ridgeH2O(X=hX, y=hy, L=0)

glmBeta<- as.matrix(glmnet.fit)
ridgelinBeta<- as.matrix(ridgelin.fit)
ridgeAnalBeta<- as.matrix(ridgeAnal.fit)
ridgeH2Obeta<- as.matrix(ridgeH2O.fit)

#TESTING OBJECTIVE FUNCTIONS
glmnetO<- glmnetObjective(glmnet.fit, X=Xvars.sc, y=Yvar, lambda=0)
ridgeO<- ridgeObjective(ridgelinBeta, X=Xvars.sc, y=Yvar, lambda=0)
h2oO<- ridgeObjective(ridgeH2Obeta,X=Xvars.sc, y=Yvar, lambda=0)

glmnet1<- glmnetObjective(glmnet.fit, X=Xvars.sc, y=Yvar, lambda=1)
ridge1<- ridgeObjective(ridgelinBeta, X=Xvars.sc, y=Yvar, lambda=1)
h2o1<- ridgeObjective(ridgeH2Obeta,X=Xvars.sc, y=Yvar, lambda=1)
glmnet1
ridge1
h2o1

glmnet2<- glmnetObjective(glmnet.fit, X=Xvars.sc, y=Yvar, lambda=10)
ridge2<- ridgeObjective(ridgelinBeta, X=Xvars.sc, y=Yvar, lambda=10)
h2o2<- ridgeObjective(ridgeH2Obeta,X=Xvars.sc, y=Yvar, lambda=10)


 #h2oTest.logInfo("Compare model statistics in R to model statistics in H2O")
    expect_equal(h2oO, ridgeO, tolerance = 0.1)
    expect_equal(h2o1, ridge1, tolerance = 0.1)
    expect_equal(h2o2, ridge2, tolerance = 0.1)
    
    
    
}

h2oTest.doTest("GLM Test: GLM2 - RidgeObjective", test.glm2RidgeObjective.golden)
