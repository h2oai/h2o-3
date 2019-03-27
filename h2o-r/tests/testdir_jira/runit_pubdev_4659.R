setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


test.4659 <- function() {
    prostate.hex = read.csv(locate("smalldata/logreg/prostate.csv"))
    prostate.hex$our_factor<-as.factor(paste0("Q",c(rep(c(1:380),1))))

    prostate.hex<-as.h2o(prostate.hex)
    prostate.hex$weight<-1
    
    prostateTrain<-prostate.hex[1:300,]
    prostateTest<-prostate.hex[301:380,]

    # delete response variable from test data
    prostateTest<-prostateTest[,-3]
    
    model<-h2o.glm(y = "AGE", x = c("our_factor"),
    training_frame = prostateTrain,offset_column="weight")
    predict(model,newdata=prostateTrain)
    predict(model,newdata=prostateTest)

    # Failed when response column was missing and there is only one feature 
    model<-h2o.glm(y = "AGE", x = c("our_factor"),
    training_frame = prostateTrain)
    predict(model,newdata=prostateTrain)
    predict(model,newdata=prostateTest)
    
    # Failed when original columns names does not fit with edited frame columns with dummy NA column for response column
    model<-h2o.gbm(y = "AGE", x = c("our_factor"), training_frame = prostateTrain,categorical_encoding = "OneHotExplicit")
    predictionResponse <- predict(model,newdata=prostateTrain)
    predict(model,newdata=prostateTest)
    
    # Delete response variable from train data to compare result predictions
    predictionWithoutResponse <- predict(model, newdata=prostateTrain[,-3])
    # Result predictions should be the same 
    expect_equal(predictionResponse, predictionWithoutResponse)
}

doTest("PUBDEV-4659", test.4659)
