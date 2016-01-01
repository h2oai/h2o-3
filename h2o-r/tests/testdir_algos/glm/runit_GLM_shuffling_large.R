setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
##
# The following bug is associated with JIRA PUB-857
# 'GLM models differ on data after shuffling'
# Testing glm consistency on 1 chunk dataset with and without shuffling rows.
##





test <- function() {
    print("Reading in Arcene training data for binomial modeling.")
        arcene.train.full = h2o.uploadFile(h2oTest.locate("smalldata/arcene/shuffle_test_version/arcene.csv"), destination_frame="arcene.train.full", header=FALSE)
        arcene.train.full_shuffled = h2o.uploadFile(h2oTest.locate("smalldata/arcene/shuffle_test_version/arcene_shuffled.csv"), destination_frame="arcene.train.full_shuffled", header=FALSE)
    
    #print("Shuffle rows of dataset.")
    #    arcene.train.full_shuffled = h2o.assign(arcene.train.full[sample(nrow(arcene.train.full),replace=F),],"arcene.train.full_shuffled")
    
    print("Create model on original Arcene dataset.")
        h2o.model <- h2o.glm(x=c(1:1000), y=1001, training_frame=arcene.train.full, family="binomial", lambda_search=TRUE, alpha=0.5, nfolds=0)

    print("Create second model on original Arcene dataset.")
        h2o.model2 <- h2o.glm(x=c(1:1000), y=1001, training_frame=arcene.train.full, family="binomial", lambda_search=TRUE, alpha=0.5, nfolds=0)

    print("Create model on shuffled Arcene dataset.")
        h2o.model.s <- h2o.glm(x=c(1:1000), y=1001, training_frame=arcene.train.full_shuffled, family="binomial", lambda_search=TRUE, alpha=0.5, nfolds=0)

    print("Assert that number of predictors remaining and their respective coefficients are equal.")
        print("Comparing 2 models from original dataset")
            diff = h2o.model@model$coefficients - h2o.model2@model$coefficients
            stopifnot(diff < 5e-10)
        print("Comparing models from original and shuffled dataset")
            diff = h2o.model@model$coefficients - h2o.model.s@model$coefficients
            stopifnot(diff < 5e-10)

    
}

h2oTest.doTest("Testing glm consistency on 1 chunk dataset with and without shuffling rows.", test)
