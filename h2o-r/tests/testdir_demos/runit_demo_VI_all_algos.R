# This Demo shows how to access Variable Importance from different H2O algorithms namely, GBM, Random Forest, GLM, Deeplearning.
# Data source: Data is obtained from -https://archive.ics.uci.edu/ml/datasets/Bank+Marketing
# Expectation:  The predictor "duration" should be picked as the most important variable by all algos

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test <- function() {

# If you want to cut and paste code from this test, you can just create the connection yourself up front.
# h = h2o.init()

# Parse data into H2O
print("Parsing data into H2O")
# From an h2o git workspace.
data.hex <- h2o.importFile( locate("smalldata/demos/bank-additional-full.csv"), destination_frame="data")
# Or directly from github.
# data.hex = h2o.importFile( path = "https://raw.github.com/0xdata/h2o/master/smalldata/bank-additional-full.csv", destination_frame="data.hex")

print("Expectation: All Algos should pick the predictor - 'duration' as the most important variable")

# Run summary
summary(data.hex)

#Print Column names
colnames(data.hex)

# Specify predictors and response
myX <- 1:20
myY <- "y"

#--------------------------------------------------
# Run GBM with variable importance
my.gbm <- h2o.gbm(x = myX, y = myY, distribution = "bernoulli", training_frame = data.hex,
                  ntrees =100, max_depth = 2, learn_rate = 0.01)

# Access Variable Importance from the built model
print("Variable importance from GBM")
gbm.VI <- h2o.varimp(my.gbm)
print(gbm.VI)


# Plot variable importance from GBM
par(mfrow=c(2,2))
barplot(gbm.VI$scaled_importance, names.arg = gbm.VI$variable, las=2,main="VI from GBM")

#--------------------------------------------------
# Run random Forest with variable importance
my.rf <- h2o.randomForest(x=myX,y=myY,training_frame=data.hex,ntrees=100)

# Access Variable Importance from the built model
print("Variable importance from Random Forest")
rf.VI <- h2o.varimp(my.rf)
print(rf.VI)

# RF variable importance Without normalization, i.e scale = F
print("Variable importance from Random Forest without normalization")
print(t(rf.VI[,1:2]))

# RF variable importance With normalization, i.e scale =T (divide mean decrease accuracy by standard deviation)
norm_rf.VI <- rf.VI$relative_importance/max(rf.VI$relative_importance)
print("Variable importance from Random Forest with normalization")
print(t(rf.VI[,c(1,3)]))
checkEqualsNumeric(norm_rf.VI, rf.VI$scaled_importance)

# Plot variable importance from Random Forest
barplot(rf.VI$scaled_importance,beside=T,names.arg=rf.VI$variable,las=2,main="VI from RF")

#--------------------------------------------------
# Run GLM with variable importance, lambda search and using all factor levels
my.glm <- h2o.glm(x=myX, y=myY, training_frame=data.hex, family="binomial",standardize=T,
  lambda_search=T)

# Select the best model picked by glm
# best_model <- my.glm@best_model

# Get the normalized coefficients of the best model
#n_coeff <- abs(my.glm@models[[best_model]]@model$normalized_coefficients)
glm.VI <- my.glm@model$standardized_coefficient_magnitudes
print("Variable importance from GLM")
print(glm.VI)

# Plot variable importance from glm
barplot(glm.VI[1:20,]$coefficients, names.arg = glm.VI[1:20,]$names, las=2,main="VI from GLM")

#--------------------------------------------------
# Run deeplearning with variable importance
my.dl <- h2o.deeplearning(x = myX, y = myY, training_frame = data.hex,
                          activation = "Tanh", hidden = c(10,10,10),
                          epochs = 12, variable_importances = T)

# Access Variable Importance from the built model
print("Variable importance from Deep Learning")
dl.VI <- my.dl@model$variable_importances
print(dl.VI)

# Plot variable importance from deeplearing
barplot(dl.VI$scaled_importance[1:20], names.arg = dl.VI$variable[1:20], las=2,main="VI from Deep Learning")


}

doTest("Plot to compare the Variable Importance as predicted by different algorithms on the bank-marketing dataset", test)

