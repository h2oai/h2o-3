checkNaiveBayesModel <- function(fitH2O, fitR, tolerance = 1e-6) {
  aprioriR <- fitR$apriori
  aprioriH2O <- fitH2O@model$apriori
  Log.info("Compare A-priori Probabilities between R and H2O\n")
  Log.info(paste("R A-priori: ", paste(aprioriR, collapse = ",")))
  Log.info(paste("H2O A-priori: ", paste(aprioriH2O, collapse = ",")))
  expect_equal(as.numeric(aprioriH2O), as.numeric(aprioriR), tolerance = tolerance)
  
  tablesR <- fitR$tables
  tablesH2O <- fitH2O@model$pcond
  Log.info("Compare Conditional Probabilities between R and H2O\n")
  Log.info("R Conditional Probabilities:"); print(tablesR)
  Log.info("H2O Conditional Probabilities:"); print(tablesH2O)
  expect_equal(length(tablesH2O), length(tablesR))
  
  # Need to reformat H2OTable since first column is actually rownames
  tmp <- mapply(function(probR, probH2O) {
    compH2O <- data.matrix(probH2O[,-1])
    dimnames(compH2O) <- list(probH2O[,1], NULL)
    expect_equal(compH2O, probR, tolerance = tolerance) }, 
  tablesR, tablesH2O )
}

checkNaiveBayesPrediction <- function(predH2O, predR, tolerance = 1e-6) {
  predH2O.df <- as.data.frame(predH2O)
  
  classR <- predR$class
  classH2O <- predH2O.df[,1]
  Log.info("Compare Class Assignments between R and H2O\n")
  expect_equal(classH2O, classR)
  
  postR <- predR$posterior
  postH2O <- predH2O.df[,-1]
  Log.info("Compare Posterior Probabilities between R and H2O\n")
  expect_equal(as.matrix(postH2O), postR, tolerance = 1e-6)
}