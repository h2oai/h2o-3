setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pubdev_1704 <- function() {
    df <- as.h2o(iris)
    df$fold <- as.h2o(as.factor(ceiling(runif(nrow(iris))*5)))

    m <- h2o.gbm(x=1:4,y=5,training_frame=df,fold_column="fold", ntrees=5)
    print(m)
    m <- h2o.gbm(x=1:4,y=5,training_frame=df,nfolds=5, ntrees=5, fold_assignment="Modulo")
    print(m)
    m <- h2o.gbm(x=1:4,y=5,training_frame=df,nfolds=5, ntrees=5, fold_assignment="Modulo")
    print(m)

    m <- h2o.randomForest(x=1:4,y=5,training_frame=df,fold_column="fold", ntrees=10)
    print(m)
    m <- h2o.randomForest(x=1:4,y=5,training_frame=df,nfolds=5, ntrees=10, fold_assignment="Modulo")
    print(m)
    m <- h2o.randomForest(x=1:4,y=5,training_frame=df,nfolds=5, ntrees=10, fold_assignment="Random")
    print(m)

    m <- h2o.deeplearning(x=1:4,y=5,training_frame=df,fold_column="fold", epochs=1, reproducible=T)
    print(m)
    m <- h2o.deeplearning(x=1:4,y=5,training_frame=df,nfolds=5, fold_assignment="Modulo", epochs=1, reproducible=T)
    print(m)
    m <- h2o.deeplearning(x=1:4,y=5,training_frame=df,nfolds=5, fold_assignment="Random", epochs=1, reproducible=T)
    print(m)

    #m <- h2o.glm(x=1:4,y=5,training_frame=df,fold_column="fold")
    #print(m)
    #m <- h2o.glm(x=1:4,y=5,training_frame=df,nfolds=5, fold_assignment="Modulo")
    #print(m)
    #m <- h2o.glm(x=1:4,y=5,training_frame=df,nfolds=5, fold_assignment="Random")
    #print(m)

  
}

h2oTest.doTest("PUBDEV-1704", test.pubdev_1704)
