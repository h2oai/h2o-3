h2oTest.checkNaiveBayesModel <- function(fitH2O, fitR, num_rows, tolerance = 1e-6) {
  aprioriR <- fitR$apriori / num_rows
  aprioriH2O <- fitH2O@model$apriori
  Log.info("Compare A-priori Probabilities between R and H2O\n")
  Log.info(paste("R A-priori: ", paste(aprioriR, collapse = ",")))
  Log.info(paste("H2O A-priori: ", paste(aprioriH2O, collapse = ",")))
  expect_equal(as.numeric(aprioriH2O), as.numeric(aprioriR), tolerance = tolerance)
  
  Log.info("Compare Conditional Probabilities between R and H2O\n")
  tablesR <- fitR$tables
  tablesH2O <- fitH2O@model$pcond
  expect_equal(length(tablesH2O), length(tablesR))
  Log.info("R Conditional Probabilities:"); print(tablesR)
  Log.info("H2O Conditional Probabilities:"); print(tablesH2O)
  
  # Reshuffle conditional probability tables so predictors in same order as R
  headersH2O <- sapply(tablesH2O, function(x) { attr(x, "header") })
  expect_true(all(headersH2O %in% names(tablesR)))
  tablesR <- tablesR[order(names(tablesR))]
  tablesH2O <- tablesH2O[order(headersH2O)]
  
  tmp <- mapply(function(probR, probH2O) {
    # First col of H2OTable is rownames
    h2oTest.checkNumMatrixVals(as.matrix(probH2O[,-1]), probR, tolerance = tolerance) },
  tablesR, tablesH2O )
}

h2oTest.checkNaiveBayesPrediction <- function(predH2O, predR, type = "class", tolerance = 1e-6) {
  predH2O.df <- as.data.frame(predH2O)
  
  if(type == "class") {
    classH2O <- predH2O.df[,1]
    if(!is.factor(classH2O)) classH2O <- as.factor(classH2O)
    Log.info("Compare Class Assignments between R and H2O\n")
    expect_equivalent(classH2O, predR)
  } else if(type == "raw") {
    postH2O <- predH2O.df[,-1]
    Log.info("Compare Posterior Probabilities between R and H2O\n")
    h2oTest.checkNumMatrixVals(as.matrix(postH2O), predR, tolerance)
  } else
    stop("type must be either 'class' or 'raw'")
}

h2oTest.checkNumMatrixVals <- function(object, predicted, tolerance = 1e-6) {
  expect_equal(dim(object), dim(predicted))
  for(i in 1:nrow(predicted)) {
    for(j in 1:ncol(predicted))
      expect_equal(as.numeric(object[i,j]), as.numeric(predicted[i,j]), tolerance = tolerance)
  }
}
