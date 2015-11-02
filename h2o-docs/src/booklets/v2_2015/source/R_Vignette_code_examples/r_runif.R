prosPath <- system.file("extdata", "prostate.csv", package="h2o")
prostate.hex <- h2o.importFile(path = prosPath)

## Creates object for uniform distribution on prostate data set
s <- h2o.runif(prostate.hex)
summary (s)  ## Summarize the results of h2o.runif

## Create training set with threshold of 0.8
prostate.train <- prostate.hex[s <= 0.8,]
##Assign name to training set
prostate.train <- h2o.assign(prostate.train, "prostate.train")
## Create test set with threshold to filter values greater than 0.8
prostate.test <- prostate.hex[s > 0.8,]
## Assign name to test set
prostate.test <- h2o.assign(prostate.test, "prostate.test")
## Combine results of test & training sets, then display result
nrow(prostate.train) + nrow(prostate.test)

nrow(prostate.hex) ## Matches the full set
