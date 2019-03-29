setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


test_4659 <- function() {
    prostate = read.csv(locate("smalldata/logreg/prostate.csv"))
    prostate$our_factor<-as.factor(paste0("Q",c(rep(c(1:380),1))))

    prostate<-as.h2o(prostate)
    prostate$weight<-1
    
    prostate_train<-prostate[1:300,]
    prostate_test<-prostate[301:380,]

    # delete response variable from test data
    prostate_test<-prostate_test[,-3]
    
    print("GLM 1")
    model<-h2o.glm(y = "AGE", x = c("our_factor"), training_frame = prostate_train,offset_column="weight")
    predict(model,newdata=prostate_train)
    predict(model,newdata=prostate_test)

    # Failed when response column was missing and there is only one feature 
    print("GLM 2")
    model<-h2o.glm(y = "AGE", x = c("our_factor"), training_frame = prostate_train)
    predict(model,newdata=prostate_train)
    predict(model,newdata=prostate_test)
    
    # Failed when original columns names does not fit with edited frame columns with dummy NA column for response column
    print("GBM 1")
    model<-h2o.gbm(y = "AGE", x = c("our_factor"), training_frame = prostate_train,categorical_encoding = "OneHotExplicit")
    prediction_response <- predict(model,newdata=prostate_train)
    predict(model,newdata=prostate_test)

    print("Prediction check")
    # Delete response variable from train data to compare result predictions
    prediction_without_response <- predict(model, newdata=prostate_train[,-3])
    # Result predictions should be the same 
    expect_equal(prediction_response, prediction_without_response)
}

doTest("PUBDEV-4659 test predict on data without response column", test_4659)
