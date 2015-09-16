##
# Test out the h2o.glm R demo
# It imports a dataset, parses it, and prints a summary
# Then, it runs h2o.glm on a subset of the dataset
##

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.tableau <- function() {
  Log.info ('Importing data into H2O')
  data.hex <- h2o.importFile(path = normalizePath(locate('smalldata/airlines/allyears2k_headers.zip')))
  
  Log.info ('Grouping flights by months...')
  f1 <- h2o.group_by(data.hex, "Month", nrow("Month"), sum("Cancelled"))
  f1.df <- as.data.frame(f1)
  
  Log.info ('Grouping flights by airport...')
  f2 <- h2o.group_by(data.hex, "Origin", nrow("Origin"), sum("Cancelled"))
  f2.df <- as.data.frame(f2)
  
  .arg2 <- 'Origin,Dest,UniqueCarrier'
  xvars <- unlist( strsplit( .arg2, split = ',' , fixed = TRUE ) )
  data.glm <- h2o.glm(x = xvars , y = 'Cancelled', training_frame = data.hex, family = 'binomial', nfolds = 0, standardize=TRUE)
  
  glmModelTemp <- eval(parse(text = 'data.glm' ))
  .arg1 <- h2o.levels(data.hex$Origin)
  if(!(length(.arg1) > 0)) stop("Didn't grab all the factor levels in the Origin column.")
  ## Tableau grab coefficients corresponding to predictor variable
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
  coeff <- sapply(.arg1, function(factor) tableau_catFormat( glmModelTemp, 'Origin' , factor) )
  if(!(length(coeff)>0)) stop("There are no coefficients filter back out!")
  
  testEnd()
}

doTest("Test out the script used in tableau worksheet", test.tableau)
