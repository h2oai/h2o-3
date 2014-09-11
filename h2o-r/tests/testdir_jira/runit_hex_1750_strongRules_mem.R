##
# Testing memory performance of Strong Rules. 
# Affirm that strong rules sucessfully lowers memory usage when running GLM
##


setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../findNSourceUtils.R')


test <- function(conn) {
  print("Reading in Arcene data.")
    arcene.train = h2o.uploadFile(conn, locate("smalldata/arcene/arcene_train.data"), key="arcene.train", header=FALSE)
    label = h2o.uploadFile(conn, locate("smalldata/arcene/arcene_train_labels.labels"), key="label", header=FALSE)
    arcene.train.label = h2o.assign(data=ifelse(label==1,1,0), key="arcene.train.label")
    arcene.train.full = h2o.assign(data=(cbind(arcene.train,arcene.train.label)),key="arcene.train.full")
  print("Head of arcene data: ")
    head(arcene.train.full)
  print("Dimension of arcene data: ")
    dim(arcene.train.full)
  
  #print("Set memory benchmark with strong rules off.")
  #print("Model successfully created for arcene data with 5000 columns")
    #h2o.model.noSR.pass <- h2o.glm(x=c(1:5000), y="arcene.train.label", data=arcene.train.full, family="binomial", lambda_search=FALSE,alpha=1, nfolds=0, use_all_factor_levels=1)
  #print("Model fails to be created for arcene data with 7000 columns due to memory error, without strong rules.")
   # assertError(h2o.model.noSR.fail <- h2o.glm(x=c(1:7000), y="arcene.train.label", data=arcene.train.full, family="binomial", lambda_search=FALSE,alpha=1, nfolds=0, use_all_factor_levels=1))
  
  print("Model successfully created for arcene data with 7000 columns with strong rules on.")
    h2o.model.SR.pass <- h2o.glm(x=c(1:7000), y="arcene.train.label", data=arcene.train.full, family="binomial", lambda_search=T,alpha=1, nfolds=0, use_all_factor_levels=TRUE)
  
  testEnd()
}

doTest("Testing memory performance of Strong Rules", test)
