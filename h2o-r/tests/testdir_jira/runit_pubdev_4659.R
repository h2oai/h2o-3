setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


test.4659 <- function() {
    prostate.hex = read.csv(locate("smalldata/logreg/prostate.csv"))
    prostate.hex$our_factor<-as.factor(paste0("Q",c(rep(c(1:380),1))))

    prostate.hex<-as.h2o(prostate.hex)
    prostate.hex$weight<-1

    print("prepare data")
    prostate_train<-prostate.hex[1:300,]
    prostate_test<-prostate.hex[301:380,]

    # delete response variable from test data
    prostate_test<-prostate_test[,-3]

    print("GLM 1")
    model<-h2o.glm(y = "AGE", x = c("our_factor"),
    training_frame = prostate_train,offset_column="weight")
    print("Predict train")
    predict(model,newdata=prostate_train)
    print("Predict train")
    predict(model,newdata=prostate_test)

    print("GLM 2")
    model<-h2o.glm(y = "AGE", x = c("our_factor"),
    training_frame = prostate_train)
    print("Predict train")
    predict(model,newdata=prostate_train)
    print("Predict test")
    # Failed assert 
    predict(model,newdata=prostate_test)

    print("GBM 1")
    model<-h2o.gbm(y = "AGE", x = c("our_factor"),
    training_frame = prostate_train,categorical_encoding = "OneHotExplicit")
    print("Predict train")
    predict(model,newdata=prostate_train)
    print("Predict test")
    # Failed Test/Validation dataset has no columns in common with the training set
    predict(model,newdata=prostate_test)
}

doTest("PUBDEV-4659", test.4659)
