setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

glm.objectiveFun.test<-
function(conn) {
	
    filepath = locate("smalldata/glm_test/marketing_naRemoved.csv")
    
    rr=read.csv(filepath)
    str(rr)
    dim(rr)
    
    mfrmr=h2o.uploadFile(conn,filepath,key = "mfrmr")
    str(mfrmr)
    myX = 2:13
    myY = 1 
    alpha = 1
    lambda = 1e-5
    
    #H2O GLM model  
    hh=h2o.glm(x=myX,y=myY,training_frame=mfrmr,family="gaussian",n_folds=0, alpha = alpha, lambda = lambda)

    res_dev = hh@model$residual_deviance
    obs = nrow(mfrmr)
    # lambda = hh@model$params$lambda
    alpha = hh@parameters$alpha
    cof = hh@model$coefficients_table$'Norm Coefficients'
    cof = cof[1:length(cof)-1] # drop the intercept!
    L1 = sum(abs(cof))
    L2 = sqrt(sum(cof^2)) 
    penalty = ( 0.5*(1-alpha)*L2^2 ) + ( alpha*L1 )
    objective = (res_dev/obs) + ( lambda * penalty )
    
    # GLMNET Model  
    gg=glmnet(x=as.matrix(rr[,2:13]),y=(rr[,1]),alpha = alpha,lambda=lambda)

	# Sanity Check whether comparing models built on the same dataset
	expect_equal( nrow(mfrmr), nrow(rr))
	expect_equal(gg$nulldev,hh@model$null_deviance)

	res_dev_R = deviance(gg)
	obs = nrow(mfrmr)
	cof_R = coef(gg,s= lambda)
	L1_R = sum(abs(cof_R[,1]))
	L2_R = sqrt(sum(cof_R[,1]^2))
	penalty_R = ( 0.5*(1-alpha)*L2_R^2 ) + ( alpha*L1_R )
	objective_R = (res_dev_R/obs) + ( lambda * penalty_R )

	print(paste("residual deviance from R:  ",res_dev_R, sep = ""))
	print(paste("residual deviance from H2O:  ",res_dev, sep = ""))
	print(paste("L1 norm of coefficients from R:  ",L1_R, sep = ""))
	print(paste("L1 norm of coefficients from H2O:  ",L1, sep = ""))
	print(paste("L2 norm of coefficients from R:  ",L2_R, sep = ""))
	print(paste("L2 norm of coefficients from H2O:  ",L2, sep = ""))
	print(paste("L2 norm of coefficients from R:  ",L2_R, sep = ""))
	print(paste("penalty on model from R:  ",penalty_R, sep = ""))
	print(paste("penalty on model from H2O:  ",penalty, sep = ""))
	print(paste("Objective function for model from R:  ",objective_R, sep = ""))
	print(paste("Objective function for model from H2O:  ",objective, sep = ""))

	expect_true(objective<=objective_R)

    testEnd()
}
doTest("Comapares objective function results from H2O-glm and glmnet: marketing data with no NAs Smalldata", glm.objectiveFun.test)

