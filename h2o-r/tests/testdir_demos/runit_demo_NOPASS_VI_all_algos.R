# This Demo shows how to access Variable Importance from different H2O algorithms namely, GBM, Random Forest, GLM, Deeplearning.
# Data source: Data is obtained from -https://archive.ics.uci.edu/ml/datasets/Bank+Marketing
# Expectation:  The predictor "duration" should be picked as the most important variable by all algos

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test <- function(h) {

# If you want to cut and paste code from this test, you can just create the connection yourself up front.
# h = h2o.init()

# Parse data into H2O
print("Parsing data into H2O")
# From an h2o git workspace.
data.hex <- h2o.importFile(h, locate("smalldata/bank-additional-full.csv"), key="data")
# Or directly from github.
# data.hex = h2o.importFile(h, path = "https://raw.github.com/0xdata/h2o/master/smalldata/bank-additional-full.csv", key="data.hex")

print("Expectation: All Algos should pick the predictor - 'duration' as the most important variable")

# Run summary
summary(data.hex)

#Print Column names
colnames(data.hex)

# Specify predictors and response
myX <- 1:20
myY <- "y"

# Run GBM with variable importance
my.gbm <- h2o.gbm(x = myX, y = myY, distribution = "bernoulli", training_frame = data.hex, ntrees =100,
                  max_depth = 2, learn_rate = 0.01, variable_importance = T) 

# Access Variable Importance from the built model
gbm.VI <- my.gbm@model$varimp
print("Variable importance from GBM")
print(gbm.VI)

par(mfrow=c(2,2))
# Plot variable importance from GBM
barplot(t(gbm.VI[1]),las=2,main="VI from GBM")

#--------------------------------------------------
# Run random Forest with variable importance
my.rf <- h2o.randomForest(x=myX,y=myY,data=data.hex,classification=T,ntree=100,importance=T)

# Access Variable Importance from the built model
rf.VI <- my.rf@model$varimp
print("Variable importance from Random Forest")
print(rf.VI)

rf.VI <- rf.VI[order(rf.VI[1,],decreasing=T)]
# RF variable importance Without normalization, i.e scale =T
print("Variable importance from Random Forest without normalization")
print(t(rf.VI[1,]))

# RF variable importance With normalization, i.e scale =T (divide mean decrease accuracy by standard deviation)
norm_rf.VI <- my.rf@model$varimp[1,]/my.rf@model$varimp[2,]
# Sort in decreasing order
nrf.VI <- norm_rf.VI[order(norm_rf.VI[1,],decreasing=T)]
print("Variable importance from Random Forest with normalization")
print(t(nrf.VI[1,]))

# Plot variable importance from Random Forest
barplot(t(nrf.VI[1,]),beside=T,names.arg=row.names(t(nrf.VI[1,])),las=2,main="VI from RF")

#--------------------------------------------------
# Run GLM with variable importance, lambda search and using all factor levels
my.glm <- h2o.glm(x=myX, y=myY, training_frame=data.hex, family="binomial",standardize=T,use_all_factor_levels=T,lambda_search=T,return_all_lambda=T)

# Select the best model picked by glm
best_model <- my.glm@best_model

# Get the normalized coefficients of the best model 
n_coeff <- abs(my.glm@models[[best_model]]@model$normalized_coefficients)  

# Access Variable Importance by removing the intercept term
VI <- abs(n_coeff[-length(n_coeff)])                                     

glm.VI <- VI[order(VI,decreasing=T)]
print("Variable importance from GLM")
print(glm.VI)

# Plot variable importance from glm
barplot(glm.VI[1:20],las=2,main="VI from GLM") 

#--------------------------------------------------
# Run deeplearning with variable importance
my.dl <- h2o.deeplearning(x=myX,y=myY,data=data.hex,classification=T,activation="Tanh",hidden=c(10,10,10),epochs=12,variable_importances=T)

# Access Variable Importance from the built model
dl.VI <- my.dl@model$varimp
print("Variable importance from Deep Learning")
print(dl.VI)

# Plot variable importance from deeplearing
barplot(t(dl.VI[1:20]),las=2,main="VI from Deep Learning")


testEnd()
}

doTest("Plot to compare the Variable Importance as predicted by different algorithms on the bank-marketing dataset", test)

