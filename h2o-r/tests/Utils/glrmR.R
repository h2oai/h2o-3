# Misclassification error on categorical columns
compareCats <- function(predH2O, predR) {
  expect_equal(dim(predH2O), dim(predR))
  predH2O <- data.frame(predH2O)
  predR <- data.frame(predR)
  
  misclass <- 0
  for(i in 1:ncol(predH2O)) {
    if(length(levels(predH2O[,i])) != length(levels(predR[,i]))) {
      if(all(levels(predH2O[,i]) %in% levels(predR[,i])))
        levels(predH2O[,i]) <- levels(predR[,i])
      else if(all(levels(predR[,i]) %in% levels(predH2O[,i])))
        levels(predR[,i]) <- levels(predH2O[,i])
      else
        stop("Levels are mismatched! H2O levels: ", paste(levels(predH2O[,i]), collapse = ", "), "\tR levels: ", paste(levels(predR[,i]), collapse = ", "))
    }
    isMissing <- sapply(predH2O[,i], is.na)
    misclass <- misclass + sum(predH2O[!isMissing,i] != predR[!isMissing,i])
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

checkGLRMPredErr <- function(fitH2O, fitR, testH2O, testR, tolerance = 1e-6) {
  NUM <- function(x) { x[,sapply(x, is.numeric)] }
  FAC <- function(x) { x[,sapply(x, is.factor)]  }
  
  Log.info("Impute XY and check error metrics")
  pred <- predict(fitH2O, testH2O)
  pred.df <- as.data.frame(pred)
  Log.info("GLRM Imputation:"); print(head(pred))
  
  testR_trans <- transformData(testR, transform = fitH2O@parameters$transform)
  numerrR <- sum((NUM(testR_trans) - NUM(pred.df))^2, na.rm = TRUE)
  caterrR <- compareCats(FAC(pred.df), FAC(testR_trans))
  expect_equal(fitH2O@model$training_metrics@metrics$numerr, numerrR, tolerance = tolerance)
  expect_equal(fitH2O@model$training_metrics@metrics$caterr, caterrR)
  return(pred)
}
