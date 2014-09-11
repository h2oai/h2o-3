##
# Testing glm modeling performance with wide Arcene dataset with and without strong rules. 
# Test for JIRA PUB-853 
# 'Early termination in glm resulting in underfitting'
##


setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../findNSourceUtils.R')


test <- function(conn) {
    print("Reading in Arcene training data for binomial modeling.")
        arcene.train = h2o.uploadFile(conn, locate("smalldata/arcene/arcene_train.data"), key="arcene.train", header=FALSE)
        arcene.label = h2o.uploadFile(conn, locate("smalldata/arcene/arcene_train_labels.labels"), key="arcene.label", header=FALSE)
        arcene.train.label = h2o.assign(data=ifelse(arcene.label==1,1,0), key="arcene.train.label")
        arcene.train.full = h2o.assign(data=(cbind(arcene.train,arcene.train.label)),key="arcene.train.full")
    
    print("Reading in Arcene validation data.")
        arcene.valid = h2o.uploadFile(conn, locate("smalldata/arcene/arcene_valid.data"), key="arcene.valid", header=FALSE)
        arcene.label = h2o.uploadFile(conn, locate("smalldata/arcene/arcene_valid_labels.labels"), key="arcene.label", header=FALSE)
        arcene.valid.label = h2o.assign(data=ifelse(arcene.label==1,1,0), key="arcene.valid.label")
        arcene.valid.full = h2o.assign(data=(cbind(arcene.valid,arcene.valid.label)),key="arcene.valid.full")
    
    print("Run model on 3250 columns of Arcene with strong rules off.")
        time.noSR.3250 <- system.time(model.noSR.3250 <- h2o.glm(x=c(1:3250), y="arcene.train.label", data=arcene.train.full, family="binomial", lambda_search=FALSE, alpha=1, nfolds=0, use_all_factor_levels=TRUE, higher_accuracy=TRUE))
        
    print("Test model on validation set.")
        predict.noSR.3250 <- h2o.predict(model.noSR.3250, arcene.valid.full)

    print("Check performance of predictions.")
        perf.noSR.3250 <- h2o.performance(predict.noSR.3250$"1", arcene.valid.full$"arcene.valid.label")
         
    print("Check that prediction AUC better than guessing (0.5).")
        stopifnot(perf.noSR.3250@model$auc > 0.5)

  testEnd()
}

doTest("Testing glm modeling performance with wide Arcene dataset with and without strong rules", test)
