setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# test from Erin that failed.
infogramErinHdma <- function() {
    data_name <- "hdma"
    data <- h2o.importFile(locate("bigdata/laptop/admissibleml_test/hdma.csv"))
    dim(data)
    # [1] 92793    33
    # Response (1 = "bad", 0 = "good")
    y <- "high_priced"
    # Convert response column to a factor (to enable classification)
    data[,y] <- as.factor(data[,y])
    h2o.levels(data[[y]])
    # [1] "0" "1"
    
    
    ss <- h2o.splitFrame(data, ratios = 0.8, seed = 1)
    train <- ss[[1]]
    test <- ss[[2]]
    dim(train)
    # [1] 74229    33
    
    # Train the infogram
    protected_columns <- c("derived_ethnicity",
                           "derived_race",
                           "derived_sex",
                           "applicant_age",                      
                           "co_applicant_age",
                           "applicant_age_above_62",
                           "co_applicant_age_above_62")
    
    # this set of columns produces no error
    x1 <- c("derived_loan_product_type",
           "reverse_mortgage_desc",
           "loan_amount",
           "loan_to_value_ratio",
           "discount_points",
           "lender_credits",
           "loan_term",
           "prepayment_penalty_term",
           "intro_rate_period",
           "negative_amortization_desc",
           "interest_only_payment_desc",
           "balloon_payment_desc",
           "property_value",
           "income",
           "debt_to_income_ratio",
           "applicant_credit_score_type_desc")
    ig1 <- h2o.infogram(y = y, x = x1, 
                        training_frame = train,
                        protected_columns = protected_columns)
    # version with 2 bad columns included
    x2 <- c("derived_loan_product_type",
           "loan_purpose_desc",
           "reverse_mortgage_desc",
           "loan_amount",
           "loan_to_value_ratio",
           "discount_points",
           "lender_credits",
           "loan_term",
           "prepayment_penalty_term",
           "intro_rate_period",
           "negative_amortization_desc",
           "interest_only_payment_desc",
           "balloon_payment_desc",
           "property_value",
           "income",
           "debt_to_income_ratio",
           "applicant_credit_score_type_desc",
           "co_applicant_credit_score_type_desc")
    ig2 <- h2o.infogram(y = y, x = x2, 
                       training_frame = train,
                       protected_columns = protected_columns)
    ig2
    expect_true(sum(ig2@admissible_features==ig1@admissible_features)==length(ig1@admissible_features))
}

doTest("Infogram: core infogram Erin hdma", infogramErinHdma)
