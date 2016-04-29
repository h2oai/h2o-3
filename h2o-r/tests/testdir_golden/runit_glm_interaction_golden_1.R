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
if( FALSE ) locate <- function(s) s
#Import data:
Log.info("Importing HANDMADE data...")
hmR<- read.csv(locate("smalldata/glm_test/handmade.csv"), header=T)


#fit R model in glmnet and RT's solver
hmR[,8]  <- hmR[,2]-mean(hmR[,2])
hmR[,9]  <- hmR[,3]-mean(hmR[,3])
hmR[,10] <- hmR[,4]-mean(hmR[,4])
hmR[,11] <- hmR[,5]-mean(hmR[,5])
hmR[,12] <- hmR[,6]-mean(hmR[,6])
hmR[,13] <- hmR[,8]*hmR[,11]       # interact 5 & 6
hmR[,14] <- hmR[,8]*hmR[,12]       # interact 2 & 5
hmR[,15] <- hmR[,11]*hmR[,12]       # interact 2 & 6
hmR[,16] <- hmR[,7]-mean(hmR[,7])

x<- as.matrix(hmR[,8:15])
y<- as.matrix(hmR[,16])
L=10/nrow(hmR)
hmH2O<- as.h2o(hmR)
fitRglmnet<-glmnet(x=x, y=y, family="gaussian", alpha=0, lambda=L, nlambda=1, standardize=F)
RT1<- ridgeLinear(x, y, L)

#fit corresponding H2O model

fitH2O  <- h2o.glm(x=c("V8", "V9", "V10", "V11", "V12", "V13","V14","V15"), y="V16", family="gaussian", nfolds=0, alpha=0, lambda=0.001, training_frame=hmH2O, standardize=FALSE)



hmR2<- read.csv(locate("smalldata/glm_test/handmade.csv"), header=T)


#fit R model in glmnet and RT's solver
hmR2[,8]  <- hmR2[,2]-mean(hmR2[,2])
hmR2[,9]  <- hmR2[,3]-mean(hmR2[,3])
hmR2[,10] <- hmR2[,4]-mean(hmR2[,4])
hmR2[,11] <- hmR2[,5]-mean(hmR2[,5])
hmR2[,12] <- hmR2[,6]-mean(hmR2[,6])
hmR2[,13] <- hmR2[,7]-mean(hmR2[,7])

hm2H2O <- as.h2o(hmR2)
fitH2O2  <- h2o.glm(x=c("V8", "V9", "V10", "V11", "V12"), y="V13", interactions=c("V8","V11","V12"), family="gaussian", nfolds=0, alpha=0, lambda=0.001, training_frame=hm2H2O, standardize=FALSE)

#test that R coefficients and basic descriptives are equal
Rcoeffsglmnet<- sort(as.matrix(coefficients(fitRglmnet)))
print(Rcoeffsglmnet)
H2Ocoeffs  <- sort(fitH2O@model$coefficients_table$coefficients)
H2Ocoeffs2 <- sort(fitH2O2@model$coefficients_table$coefficients)
H2Ocoeffs<- as.data.frame(H2Ocoeffs)
H2Ocoeffs2<- as.data.frame(H2Ocoeffs2)
print(H2Ocoeffs)
print(H2Ocoeffs2)

RTcoeffs<- sort(as.matrix(RT1))

#Log.info(paste("H2O Coeffs  : ", H2Ocoeffs,  "\t\t\t", "R GLMNET Coeffs  :", Rcoeffsglmnet))

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


expect_equal(H2Ocoeffs2[1,1], Rcoeffsglmnet[1], tolerance = 0.1)
expect_equal(H2Ocoeffs2[2,1], Rcoeffsglmnet[2], tolerance = 0.1)
expect_equal(H2Ocoeffs2[3,1], Rcoeffsglmnet[3], tolerance = 0.9)
expect_equal(H2Ocoeffs2[4,1], Rcoeffsglmnet[4], tolerance = 0.1)
expect_equal(H2Ocoeffs2[5,1], Rcoeffsglmnet[5], tolerance = 0.1)
expect_equal(H2Ocoeffs2[6,1], Rcoeffsglmnet[6], tolerance = 0.1)
expect_equal(H2Ocoeffs2[1,1], RTcoeffs[1], tolerance = 0.1)
expect_equal(H2Ocoeffs2[2,1], RTcoeffs[2], tolerance = 0.1)
expect_equal(H2Ocoeffs2[3,1], RTcoeffs[3], tolerance = 0.1)
expect_equal(H2Ocoeffs2[4,1], RTcoeffs[4], tolerance = 0.1)
expect_equal(H2Ocoeffs2[5,1], RTcoeffs[5], tolerance = 0.1)
expect_equal(H2Ocoeffs2[6,1], RTcoeffs[6], tolerance = 0.1)


H2Oratio<- 1-(fitH2O@model$training_metrics@metrics$residual_deviance/fitH2O@model$training_metrics@metrics$null_deviance)
Log.info(paste("H2O Deviance  : ", fitH2O@model$training_metrics@metrics$residual_deviance,      "\t\t\t", "R Deviance   : ", fitRglmnet$deviance))
Log.info(paste("H2O Null Dev  : ", fitH2O@model$training_metrics@metrics$null_deviance, "\t\t", "R Null Dev   : ", fitRglmnet$nulldev))
Log.info(paste("H2O Dev Ratio  : ", H2Oratio, "\t\t", "R Dev Ratio   : ", fitRglmnet$dev.ratio))
expect_equal(fitH2O@model$training_metrics@metrics$null_deviance, fitRglmnet$nulldev, tolerance = 0.01)
expect_equal(H2Oratio, fitRglmnet$dev.ratio, tolerance = 0.01)


H2Oratio2<- 1-(fitH2O2@model$training_metrics@metrics$residual_deviance/fitH2O2@model$training_metrics@metrics$null_deviance)
Log.info(paste("H2O Deviance  : ", fitH2O2@model$training_metrics@metrics$residual_deviance,      "\t\t\t", "R Deviance   : ", fitRglmnet$deviance))
Log.info(paste("H2O Null Dev  : ", fitH2O2@model$training_metrics@metrics$null_deviance, "\t\t", "R Null Dev   : ", fitRglmnet$nulldev))
Log.info(paste("H2O Dev Ratio  : ", H2Oratio2, "\t\t", "R Dev Ratio   : ", fitRglmnet$dev.ratio))
expect_equal(fitH2O2@model$training_metrics@metrics$null_deviance, fitRglmnet$nulldev, tolerance = 0.01)
expect_equal(H2Oratio2, fitRglmnet$dev.ratio, tolerance = 0.01)


}

doTest("GLM2 SimpleRidge", test.glm2Ridge.golden)
