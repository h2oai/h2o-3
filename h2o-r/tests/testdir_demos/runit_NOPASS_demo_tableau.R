##
# Test out the h2o.gbm R demo
# It imports a dataset, parses it, and prints a summary
# Then, it runs h2o.gbm on a subset of the dataset
##

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.tableau <- function(conn) {
  Log.info ('Check cluster status')
  h2o.clusterInfo(conn)
  Log.info ('Importing data into H2O')
  data.hex <- h2o.importFile(conn, normalizePath(locate('smalldata/airlines/allyears2k_headers.zip')))
  
  Log.info ('Grouping flights by months...')
  numFlights <- h2o.ddply(data.hex, 'Month', nrow)
  numFlights.R <- as.data.frame(numFlights)

  Log.info ('Grouping number of cancellations by months...')
  fun2 <- function(df) {sum(df[22])}         # must be numeric
  cancelledFlights <- h2o.ddply(data.hex, 'Month', fun2)
  cancelledFlights.R <- as.data.frame(cancelledFlights)
  
  Log.info ('Grouping flights by airport...')
  originFlights <- h2o.ddply(data.hex, 'Origin', nrow)
  originFlights.R <- as.data.frame(originFlights)
  
  Log.info ('Grouping number of cancellations by airport...')
  origin_cancelled <- h2o.ddply(data.hex, 'Origin', fun2)
  origin_cancelled.R <- as.data.frame(origin_cancelled)
  
  .arg2 <- 'Origin,Dest,UniqueCarrier'
  xvars <- unlist( strsplit( .arg2, split = ',' , fixed = TRUE ) )
  data.glm <- h2o.glm(x = xvars , y = 'Cancelled', training_frame = data.hex, family = 'binomial', n_folds = 0, standardize=TRUE)
  
  glmModelTemp <- eval(parse(text = 'data.glm' ))
  originFactors <- levels(data.hex$Origin)
  ## Tableau grab coefficients corresponding to predictor variable
  .arg1 <- originFactors
  tableau_catFormat <- function( modelKey , variableStr, predictorVariable) {
    if( typeof(modelKey) != 'S4') print('Model Key is not in expected format of S4')
    if( is.character(variableStr) != TRUE) print('Input column is not in expected format of string')
    if( is.character(predictorVariable) != TRUE) print('Input variables is not in expected format of string')
    glmModelTemp        <- modelKey
    modelCoeff          <- modelKey@model$coefficients
    modelCoeff          <- modelKey@model$coefficients
    idx                 <- grep( variableStr , names(modelCoeff))
    modelCoeff2         <- modelCoeff[idx]
    variableNames       <- unlist(strsplit(names(modelCoeff2),split='.',fixed=TRUE))
    variableNamesMatrix <- matrix(variableNames, ncol=2, byrow=TRUE)
    variableList        <- variableNamesMatrix[,2]
    names(modelCoeff2)  <- variableList
    setDiff             <- setdiff(.arg1,variableList)
    nullVec             <- rep(0,length(setDiff))
    names(nullVec)      <- setDiff
    newCoefficientList  <- c(modelCoeff2, nullVec)
    tableau_input       <- newCoefficientList[predictorVariable]
    tableau_input}
  
  Log.info ('Finish setting up for Tableau function')
  sapply(originFactors, function(factor) tableau_catFormat( glmModelTemp, 'Origin' , factor) )
  
  testEnd()
}

doTest("Test out the script used in tableau worksheet", test.tableau)
