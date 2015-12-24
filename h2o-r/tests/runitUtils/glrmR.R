# Misclassification error on categorical columns
compareCats <- function(pred, actual) {
  expect_equal(dim(pred), dim(actual))
  pred.df <- data.frame(pred)
  actual.df <- data.frame(actual)
  if(ncol(pred.df) == 0 || nrow(pred.df) == 0) return(0)
  
  misclass <- 0
  for(i in 1:ncol(pred.df)) {
    pstr <- as.character(pred.df[,i])
    astr <- as.character(actual.df[,i])
    isMissing <- sapply(actual.df[,i], is.na)
    misclass <- misclass + sum(pstr[!isMissing] != astr[!isMissing])
  }
  misclass
}

transformData <- function(df, transform = "NONE") {
  NUM <- function(x) { x[,sapply(x, is.numeric)] }
  if(missing(transform) || is.null(transform) || transform == "NONE")
    return(df)
  if(transform == "DEMEAN")
    df_num_trans <- scale(NUM(df), center = TRUE, scale = FALSE)
  else if(transform == "DESCALE")
    df_num_trans <- scale(NUM(df), center = FALSE, scale = apply(NUM(df), 2, sd, na.rm = TRUE))
  else if(transform == "STANDARDIZE")
    df_num_trans <- scale(NUM(df), center = TRUE, scale = TRUE)
  else
    stop("Unrecognized transformation type ", transform)
  
  df_trans <- df
  df_trans[,sapply(df, is.numeric)] <- df_num_trans
  return(df_trans)
}

checkError <- function(dataR, imputeR, metricsH2O, transform = "NONE", impute_original = FALSE, tolerance = 1e-6) {
  NUM <- function(x) { x[,sapply(x, is.numeric)] }
  FAC <- function(x) { x[,sapply(x, is.factor)]  }
  
  # Transform data in R to match what H2O processed and compute errors
  dataR_trans <- transformData(dataR, transform = ifelse(impute_original, "NONE", transform))
  numerrR <- sum((NUM(dataR_trans) - NUM(imputeR))^2, na.rm = TRUE)
  caterrR <- compareCats(FAC(imputeR), FAC(dataR_trans))
  
  # Compare with H2O's generated training and validation metrics
  expect_equal(metricsH2O$numerr, numerrR, tolerance = tolerance)
  expect_equal(metricsH2O$caterr, caterrR)
}

checkTrainErr <- function(fitH2O, trainR, imputeR, tolerance = 1e-6) {
  checkError(trainR, imputeR, fitH2O@model$training_metrics@metrics, fitH2O@allparameters$transform, fitH2O@allparameters$impute_original, tolerance)
}

checkValidErr <- function(fitH2O, testR, imputeR, tolerance = 1e-6) {
  checkError(testR, imputeR, fitH2O@model$validation_metrics@metrics, fitH2O@allparameters$transform, fitH2O@allparameters$impute_original, tolerance)
}

checkGLRMPredErr <- function(fitH2O, trainH2O, validH2O = NULL, tolerance = 1e-6) {
  Log.info("Impute original data from XY decomposition")
  pred <- predict(fitH2O, trainH2O)   # TODO: Don't need trainH2O for pure imputation
  print(head(pred))
  print(fitH2O)
  
  Log.info("Check training and validation error metrics")
  pred.df <- as.data.frame(pred)
  trainR <- as.data.frame(trainH2O)
  checkTrainErr(fitH2O, trainR, pred.df, tolerance)
  if(!is.null(validH2O) && !is.null(fitH2O@model$validation_metrics)) {
    validR <- as.data.frame(validH2O)
    checkValidErr(fitH2O, validR, pred.df, tolerance)
  }
  return(pred)
}
