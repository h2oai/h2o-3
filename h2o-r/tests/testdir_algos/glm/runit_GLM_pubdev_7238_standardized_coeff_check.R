setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

glmStandardizeCoeffsCheck <- function() {
  # check binomial
  print("Checking standardized coefficients for binomials....")
  checkCoeffs("smalldata/glm_test/binomial_20_cols_10KRows.csv", "binomial")
  
  # check multinomial
  print("Checking standardized coefficients for multinomials....")
  checkCoeffs("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv", "multinomial")
  
  # check gaussian
  print("Checking standardized coefficients for regression....")
  checkCoeffs("smalldata/glm_test/gaussian_20cols_10000Rows.csv", "gaussian")
}

checkCoeffs <- function(filename, family) {
  training_frame <- h2o.uploadFile(locate(filename))
  numCols <- ncol(training_frame)
  colNames <- names(training_frame)
  Y <- numCols
  totPredCols <- Y-1
  x <- c(1:totPredCols)
  if ((family=="multinomial") || (family=="binomial")) {
    training_frame[colNames[Y]] <- h2o.asfactor(training_frame[colNames[Y]])
  }
  lastEnumCol <- totPredCols/2
  for (index in c(1:lastEnumCol)) {
    training_frame[colNames[index]] <- h2o.asfactor(training_frame[colNames[index]])
  }
  m1 <- h2o.glm(y = Y, x = x, training_frame = training_frame, family = family, standardize=TRUE) 
  coeff1 <- h2o.coef_norm(m1)
  # standardize numerical columns
  startIndex <- totPredCols/2+1
  for (index in c(startIndex:totPredCols)) {
    aver <- h2o.mean(training_frame[colNames[index]])
    sig <- 1.0/sqrt(h2o.var(training_frame[colNames[index]]))
    training_frame[colNames[index]] <- (training_frame[colNames[index]]-aver)*sig
  }
  m2 <- h2o.glm(y = Y, x = x, training_frame = training_frame, family = family, standardize=FALSE)
  coeff2 <- h2o.coef_norm(m2)
  coeff2Coef <- h2o.coef(m2)
 if (family == "multinomial") {
   numCompares = length(coeff1)
   for (index in c(2:numCompares)) {
     # skip the coefficient names
     numCoeff = length(coeff1[[index]])
     for (index2 in c(2:numCoeff)) {
       # skip the class name which is different
       expect_equal(coeff1[[index]][index2], coeff2[[index]][index2])
       expect_equal(coeff2[[index]][[index2]], coeff2Coef[[index]][[index2]]) # test R API coef() and coef_norm(), should equal here
     }
   }
 } else {
    expect_equal(coeff1, coeff2)
    expect_equal(coeff2, coeff2Coef) # test R API coef() and coef_norm().  They should be equal in this case
  }
}

doTest("GLM: Check standardized coefficients when standardize = FALSE.", glmStandardizeCoeffsCheck)
