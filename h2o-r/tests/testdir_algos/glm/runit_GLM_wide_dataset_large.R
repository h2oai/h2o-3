setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
##
# Testing glm modeling performance with wide Arcene dataset with and without strong rules. 
# Test for JIRA PUB-853 
# 'Early termination in glm resulting in underfitting'
##






test <- function() {
    print("Reading in Arcene training data for binomial modeling.")
        arcene.train = h2o.uploadFile(h2oTest.locate("smalldata/arcene/arcene_train.data"), destination_frame="arcene.train")
        arcene.label = h2o.uploadFile(h2oTest.locate("smalldata/arcene/arcene_train_labels.labels"), destination_frame="arcene.label")
        arcene.train.label = h2o.assign(data=ifelse(arcene.label==1,1,0), key="arcene.train.label")
        colnames(arcene.train.label) <- 'arcene.train.label'
        arcene.train.full = h2o.assign(data=h2o.cbind(arcene.train,arcene.train.label),key="arcene.train.full")
    
    print("Reading in Arcene validation data.")
        arcene.valid = h2o.uploadFile(h2oTest.locate("smalldata/arcene/arcene_valid.data"), destination_frame="arcene.valid", header=FALSE)
        arcene.label = h2o.uploadFile(h2oTest.locate("smalldata/arcene/arcene_valid_labels.labels"), destination_frame="arcene.label", header=FALSE)
        arcene.valid.label = h2o.assign(data=ifelse(arcene.label==1,1,0), key="arcene.valid.label")
        colnames(arcene.valid.label) <- 'arcene.train.label' # have to have the same name as reponse in training!
        arcene.valid.full = h2o.assign(data=h2o.cbind(arcene.valid,arcene.valid.label),key="arcene.valid.full")
    
    print("Run model on 3250 columns of Arcene with strong rules off.")
            time.noSR.3250 <- system.time(model.noSR.3250 <- h2o.glm(x=c(1:3250), y="arcene.train.label", training_frame=arcene.train.full, family="binomial", lambda_search=FALSE, alpha=1, nfolds=0))
        
    print("Test model on validation set.")
        predict.noSR.3250 <- predict(model.noSR.3250, arcene.valid.full)

    print("Check performance of predictions.")
        perf.noSR.3250 <- h2o.performance(model.noSR.3250, arcene.valid.full)
         
    print("Check that prediction AUC better than guessing (0.5).")
        stopifnot(h2o.auc(perf.noSR.3250) > 0.5)

  
}

h2oTest.doTest("Testing glm modeling performance with wide Arcene dataset with and without strong rules", test)
