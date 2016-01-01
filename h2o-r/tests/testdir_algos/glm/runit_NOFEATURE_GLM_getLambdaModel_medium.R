setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
#Build glm model with lambda search on.
#Randomly choose a lambda and get the model specific to that lambda
#Change the 1st parameter of the getLambdaModel method and make sure you get the same model for the same lambda





test.GLM.getLambdaModel <- function() {
print("Read data")
pros.hex = h2o.importFile(normalizePath(h2oTest.locate("smalldata/logreg/prostate.csv")), destination_key="pros.hex")

myX = c("AGE","RACE","DPROS","DCAPS","PSA","VOL","GLEASON")
myY = "CAPSULE"
print("Choose distribution")
family = sample(c("gaussian","binomial"),1)
print(family)

print("Do lambda search and build models")
my.glm = h2o.glm(x=myX, y=myY, training_frame=pros.hex, family=family, standardize=T, lambda_search=T)

print("the models were built over the following lambda values  - ")
all_lambdas = my.glm@models[[1]]@model$params$lambda_all
print(all_lambdas)

for(i in 1:10){
	model_number = sample(seq(1,length(my.glm@models[[1]]@model$params$lambda_all),1),2)
	lambda = all_lambdas[sample(seq(1,length(all_lambdas),1),1)]
	print(paste("***************For lambda=",lambda," , we get this model- ",sep=''))
	m1 = h2o.getGLMLambdaModel(model=my.glm@models[[model_number[1]]],lambda=lambda)
	print(m1)
	print("***************this model should be same as the one above- ")
	m2 = h2o.getGLMLambdaModel(model=my.glm@models[[model_number[2]]],lambda=lambda)
	print(m2)
	expect_equal(m1,m2)
}


}

h2oTest.doTest("GLM get Model for each Lambda Test: Prostate", test.GLM.getLambdaModel)


