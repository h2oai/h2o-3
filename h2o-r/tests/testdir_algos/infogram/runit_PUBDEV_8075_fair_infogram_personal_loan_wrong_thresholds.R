setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# tests that infogram building generates warning messages when wrong thresholds are used
infogramPersonalLoanWrongThresholds <- function() {
    bhexFV <- h2o.importFile(locate("smalldata/admissibleml_test/Bank_Personal_Loan_Modelling.csv"))
    bhexFV["Personal Loan"]<- h2o.asfactor(bhexFV["Personal Loan"])
    Y <- "Personal Loan"
    X <- c("Experience","Income","Family","CCAvg","Education","Mortgage",  "Securities Account","CD Account","Online","CreditCard")
    expect_warning(mFV <- h2o.infogram(y = Y, x = X, training_frame = bhexFV, distribution = "bernoulli", net_information_threshold=0.2, 
                        protected_columns=c("Age","ZIP Code")))
    expect_warning(mFV <- h2o.infogram(y = Y, x = X, training_frame = bhexFV, distribution = "bernoulli", total_information_threshold=0.2, 
                        protected_columns=c("Age","ZIP Code")))
}

doTest("Infogram warning: Personal Load fair infogram with wrong thresholds", infogramPersonalLoanWrongThresholds)
