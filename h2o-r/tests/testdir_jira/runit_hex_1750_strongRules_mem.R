setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
##
# Testing memory performance of Strong Rules. 
# Affirm that strong rules sucessfully lowers memory usage when running GLM
##






test <- function() {
  if( !h2o.is_client() ) {
    print("Reading in Arcene data.")
      arcene.train = h2o.importFile(h2oTest.locate("smalldata/arcene/arcene_train.data"), destination_frame="arcene.train", header=FALSE)
      label = h2o.importFile(h2oTest.locate("smalldata/arcene/arcene_train_labels.labels"), destination_frame="label", header=FALSE)
      arcene.train.label = h2o.assign(data=ifelse(label==1,1,0), key="arcene.train.label")
      colnames(arcene.train.label) <- "arcene.train.label"
      arcene.train.full = h2o.assign(data=(h2o.cbind(arcene.train,arcene.train.label)),key="arcene.train.full")
    print("Head of arcene data: ")
      head(arcene.train.full)
    print("Dimension of arcene data: ")
      dim(arcene.train.full)
    
    #print("Set memory benchmark with strong rules off.")
    #print("Model successfully created for arcene data with 5000 columns")
      #H2OModel.noSR.pass <- h2o.glm(x=c(1:5000), y="arcene.train.label", data=arcene.train.full, family="binomial", lambda_search=FALSE,alpha=1, nfolds=0, use_all_factor_levels=1)
    #print("Model fails to be created for arcene data with 7000 columns due to memory error, without strong rules.")
     # assertError(H2OModel.noSR.fail <- h2o.glm(x=c(1:7000), y="arcene.train.label", data=arcene.train.full, family="binomial", lambda_search=FALSE,alpha=1, nfolds=0, use_all_factor_levels=1))
    
    print("Model successfully created for arcene data with 7000 columns with strong rules on.")
      H2OModel.SR.pass <- h2o.glm(x=c(1:7000), y="arcene.train.label", training_frame=arcene.train.full, family="binomial", lambda_search=T,alpha=1, nfolds=0)
  } else {
    print("skipping test in client mode...")
  }
  
}

h2oTest.doTest("Testing memory performance of Strong Rules", test)
