#   This test is to check bernoulli gbm implementation, 
#   It creates a synthetic dataset, runs gbm grid in H2O and R and compares aucs

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.GBM.bernoulli.SyntheticData <- function(conn) {

    #   Generate dataset
    # http://www.stat.missouri.edu/~speckman/stat461/boost.R
    set.seed(3719)

    n <- 2000
    #  Generate variables V1, ... V10
    X <- matrix(rnorm(10*n), n, 10)
    #  y = +1 if sum_i x_{ij}^2 > chisq median on 10 df
    y <- rep(-1, n)
    y[apply(X*X, 1, sum) > qchisq(.5, 10)] <- 1

    #  Assign names to the columns of X:
    dimnames(X)[[2]] <- c("V1", "V2", "V3", "V4", "V5", "V6", "V7", "V8", "V9", "V10")

    #  Convert to data.frame
    train.data <- as.data.frame(X)
    #  Add y
    train.data$y <- y

    #  Now repeat for 10000 test data
    n <- 10000
    X <- matrix(rnorm(10*n), n, 10)
    y <- rep(-1, n)
    y[apply(X*X, 1, sum) > qchisq(.5, 10)] <- 1
    dimnames(X)[[2]] <- c("V1", "V2", "V3", "V4", "V5", "V6", "V7", "V8", "V9", "V10")
    test.data <- as.data.frame(X)
    test.data$y <- y

    #  Need to put training and test data together for gbm below and convert
    #  to 0-1 data

    train.data2 <- train.data
    train.data2$y[train.data2$y < 0] <- 0
    test.data2 <- test.data
    test.data2$y[test.data2$y < 0] <- 0
    all.data2 <- rbind(train.data2, test.data2)

    #  Parse data to H2O
    print("Parse data to H2O")
    system.time(alldata <- as.h2o(all.data2, destination_frame = "alldata"))
    system.time(test <- as.h2o(test.data2, destination_frame = "test"))

    str(alldata)

    myX <- c("V1", "V2", "V3", "V4", "V5", "V6", "V7", "V8", "V9", "V10")

    myY <- "y"

    #  Run H2O-GBM grid job
    print("H2O GBM grid search")
    system.time(tru.gbm <- h2o.gbm(x = myX, y = myY, training_frame = alldata, distribution = "bernoulli", ntrees = c(150), min_rows = 1, max_depth = c(1,2,3,4), learn_rate = c(1,.1,.01), nbins = c(20)) )

    num_models <- length(tru.gbm@sumtable)
    print(paste("Number of gbm models created:", num_models,sep ='') )
    expect_equal(num_models,12)
    print("GBM models summary")
    print(tru.gbm)
    
    for(i in 1:num_models){
        model <- tru.gbm@model[[i]]
        gg<-gbm(y~., data=all.data2, distribution="bernoulli", n.trees=tru.gbm@sumtable[[i]]$n.trees,
                      interaction.depth=tru.gbm@sumtable[[i]]$interaction.depth,n.minobsinnode=tru.gbm@sumtable[[i]]$n.minobsinnode, 
                      shrinkage=tru.gbm@sumtable[[i]]$shrinkage,bag.fraction=1)                # R gbm model             
        mm_y <- predict.gbm(gg,newdata=test.data2,n.trees=tru.gbm@sumtable[[i]]$n.trees,type='response')  # R Predict
        R_auc <- round(gbm.roc.area(test.data2$y,mm_y), digits=3)
        pred <- predict(model,test)                                                                #H2O Predict
        H2O_perf <- h2o.performance(pred$'1',test$y,measure="F1")
        H2O_auc <- round(H2O_perf@model$AUC, digits=3)
        print(paste ( tru.gbm@sumtable[[i]]$model_key,
                " trees:", tru.gbm@sumtable[[i]]$ntrees,
                " depth:",tru.gbm@sumtable[[i]]$max_depth,
                " shrinkage:",tru.gbm@sumtable[[i]]$learn_rate,
                " min row: ",tru.gbm@sumtable[[i]]$min_rows,
                " bins:",tru.gbm@sumtable[[i]]$nbins,
                " H2O_auc:", H2O_auc, 
                " R_auc:", R_auc, sep=''),quote=F)
                expect_that(H2O_auc >= (R_auc-.01), is_true())                     # Compare H2O and R auc's; here tolerance is 0.01
    }
    testEnd()
}
doTest("GBM Grid Test: Synthetic dataset with Bernoulli distribution H2O vs R", test.GBM.bernoulli.SyntheticData)
