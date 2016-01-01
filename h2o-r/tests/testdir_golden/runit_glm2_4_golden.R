setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.glm2Ridge.golden <- function() {
	
#RT's solver:
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

#Import data: 
h2oTest.logInfo("Importing HANDMADE data...") 
hmR<- read.csv(h2oTest.locate("smalldata/glm_test/handmade.csv"), header=T)


#fit R model in glmnet and RT's solver
hmR[,8]<- hmR[,2]-mean(hmR[,2])
hmR[,9]<- hmR[,3]-mean(hmR[,3])
hmR[,10]<- hmR[,4]-mean(hmR[,4])
hmR[,11]<- hmR[,5]-mean(hmR[,5])
hmR[,12]<- hmR[,6]-mean(hmR[,6])
hmR[,13]<- hmR[,7]-mean(hmR[,7])

x<- as.matrix(hmR[,8:12])
y<- as.matrix(hmR[,13])
L=10/nrow(hmR)
hmH2O<- as.h2o(hmR)
fitRglmnet<-glmnet(x=x, y=y, family="gaussian", alpha=0, lambda=L, nlambda=1, standardize=F)
RT1<- ridgeLinear(x, y, L)

#fit corresponding H2O model

fitH2O<- h2o.glm(x=c("V8", "V9", "V10", "V11", "V12"), y="V13", family="gaussian", nfolds=0, alpha=0, lambda=0.01, training_frame=hmH2O)

#test that R coefficients and basic descriptives are equal
Rcoeffsglmnet<- sort(as.matrix(coefficients(fitRglmnet)))
print(Rcoeffsglmnet)
H2Ocoeffs<- sort(fitH2O@model$coefficients_table$coefficients)
H2Ocoeffs<- as.data.frame(H2Ocoeffs)
print(H2Ocoeffs)

RTcoeffs<- sort(as.matrix(RT1)) 

#h2oTest.logInfo(paste("H2O Coeffs  : ", H2Ocoeffs,  "\t\t\t", "R GLMNET Coeffs  :", Rcoeffsglmnet))

expect_equal(H2Ocoeffs[1,1], Rcoeffsglmnet[1], tolerance = 0.1)
expect_equal(H2Ocoeffs[2,1], Rcoeffsglmnet[2], tolerance = 0.1)
expect_equal(H2Ocoeffs[3,1], Rcoeffsglmnet[3], tolerance = 0.9)
expect_equal(H2Ocoeffs[4,1], Rcoeffsglmnet[4], tolerance = 0.1)
expect_equal(H2Ocoeffs[5,1], Rcoeffsglmnet[5], tolerance = 0.1)
expect_equal(H2Ocoeffs[6,1], Rcoeffsglmnet[6], tolerance = 0.1)
expect_equal(H2Ocoeffs[1,1], RTcoeffs[1], tolerance = 0.1)
expect_equal(H2Ocoeffs[2,1], RTcoeffs[2], tolerance = 0.1)
expect_equal(H2Ocoeffs[3,1], RTcoeffs[3], tolerance = 0.1)
expect_equal(H2Ocoeffs[4,1], RTcoeffs[4], tolerance = 0.1)
expect_equal(H2Ocoeffs[5,1], RTcoeffs[5], tolerance = 0.1)
expect_equal(H2Ocoeffs[6,1], RTcoeffs[6], tolerance = 0.1)


H2Oratio<- 1-(fitH2O@model$training_metrics@metrics$residual_deviance/fitH2O@model$training_metrics@metrics$null_deviance)
h2oTest.logInfo(paste("H2O Deviance  : ", fitH2O@model$training_metrics@metrics$residual_deviance,      "\t\t\t", "R Deviance   : ", fitRglmnet$deviance))
h2oTest.logInfo(paste("H2O Null Dev  : ", fitH2O@model$training_metrics@metrics$null_deviance, "\t\t", "R Null Dev   : ", fitRglmnet$nulldev))
h2oTest.logInfo(paste("H2O Dev Ratio  : ", H2Oratio, "\t\t", "R Dev Ratio   : ", fitRglmnet$dev.ratio))
expect_equal(fitH2O@model$training_metrics@metrics$null_deviance, fitRglmnet$nulldev, tolerance = 0.01)
expect_equal(H2Oratio, fitRglmnet$dev.ratio, tolerance = 0.01)


   
}

h2oTest.doTest("GLM2 SimpleRidge", test.glm2Ridge.golden)
