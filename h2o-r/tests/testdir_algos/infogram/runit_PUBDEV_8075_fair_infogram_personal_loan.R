setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# tests that infogram build the correct model for fair infogram.  Make sure 
# 1. it gets the correct result compared to deep's code.
# 2. the relevance and cmi frame contains the correct values
infogramPersonalLoan <- function() {
    bhexFV <- h2o.importFile(locate("smalldata/admissibleml_test/Bank_Personal_Loan_Modelling.csv"))
    bhexFV["Personal Loan"]<- h2o.asfactor(bhexFV["Personal Loan"])
    Y <- "Personal Loan"
    X <- c("Experience","Income","Family","CCAvg","Education","Mortgage",  "Securities Account","CD Account","Online","CreditCard")
    deepCMI <- sort(c(0.018913757, 1.000000000, 0.047752382, 0.646021834, 0.087924437, 0.126791480,
                      0.012771638, 0.203651610, 0.007879079, 0.014035872))
    deepRel <- sort(c(0.035661238, 0.796097276, 0.393246039, 0.144327761, 1.000000000, 0.002905239,
                      0.002187174, 0.046872455, 0.004976263, 0.004307822))
    Log.info("Build the model")
    mFV <- h2o.infogram(y = Y, x = X, training_frame = bhexFV, distribution = "bernoulli", seed = 12345, 
                        protected_columns=c("Age","ZIP Code"))
    relCMIFrame <- mFV@admissible_score # get frames containing relevance and cmi
    frameCMI <- sort(as.vector(t(relCMIFrame[,5])))
    frameRel <- sort(as.vector(t(relCMIFrame[,4])))
    
    expect_equal(deepCMI, frameCMI, tolerance=1e-6) # check result agrees with Deep's
    expect_equal(deepRel, frameRel, tolerance=1e-6) 
}

doTest("Infogram: Personal Load fair infogram", infogramPersonalLoan)
