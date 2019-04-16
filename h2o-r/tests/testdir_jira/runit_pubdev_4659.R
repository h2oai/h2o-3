setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


# These test check problems with a prediction on testing data which are not the same as training data.
# For example, the response column or the weights column is missing in testing data.
test_4659 <- function() {
    prostate = read.csv(locate("smalldata/logreg/prostate.csv"))
    prostate$our_factor <-as.factor(paste0("Q",c(rep(c(1:380),1))))
    prostate$offset <- runif(nrow(prostate), 0, 1)

    prostate<-as.h2o(prostate)
    prostate_train<-prostate[1:300,]
    prostate_test<-prostate[301:380,]

    # delete response variable from test data
    prostate_test<-prostate_test[,-3]

    print("GLM predicts NA")
    # Pass without problems 
    model<-h2o.glm(y = "AGE", x = c("our_factor"), training_frame = prostate_train,offset_column="offset")
    prediction <- predict(model,newdata=prostate_train)
    prediction_test <- predict(model,newdata=prostate_test)
    expect_false(all(is.na(prediction_test)))

    print("GLM problem when test data has only one data column without response")
    # Failed when response column was missing and there is only one feature
    model<-h2o.glm(y = "AGE", x = c("our_factor"), training_frame = prostate_train)
    predict(model,newdata=prostate_train)
    predict(model,newdata=prostate_test)

    print("GLM problem when original columns names does not fit with edited frame columns")
    # Failed when original columns names does not fit with edited frame columns with dummy NA column for response column
    model<-h2o.gbm(y = "AGE", x = c("our_factor"), training_frame = prostate_train,categorical_encoding = "OneHotExplicit")
    prediction_response <- predict(model,newdata=prostate_train)
    predict(model,newdata=prostate_test)

    print("Compare predictions")
    # Delete response variable from train data to compare result predictions
    prediction_without_response <- predict(model, newdata=prostate_train[,-3])
    # Result predictions should be the same 
    expect_equal(prediction_response, prediction_without_response)
}

doTest("PUBDEV-4659 test predict on data without response column", test_4659)
