#############################################
## H2O Uplift Random Forest vs. uplift.upliftRF test
#############################################

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


test.uplift <- function() {
    library(uplift)
    ### simulate data for uplift modeling

    set.seed(123)
    train <- sim_pte(n = 100, p = 6, rho = 0, sigma = sqrt(2), beta.den = 4)
    ntrees <- 10

    print("Train data summary")
    print(summary(train))

    print("Uplift fit model")
    # fit upliftRF
    modelUplift <- upliftRF(y ~ X1 + X2 + X3 + X4 + X5 + X6 + trt(treat),
        data = train,
        split_method = "KL",
        mtry = 6,
        ntree = 1000,
        #interaction.depth = 10,
        minsplit = 10,
        min_bucket_ct0 = 10,
        min_bucket_ct1 = 10,
        verbose = TRUE)
    
    print("Uplift model summary")
    print(summary(modelUplift))

    # predict upliftRF on new data
    print("Uplift predict on test data")
    test <- sim_pte(n = 200, p = 20, rho = 0, sigma =  sqrt(2), beta.den = 4)
    test$treat <- ifelse(test$treat == 1, 1, 0)

    predUplift <- predict(modelUplift, test)
    print(head(predUplift))

    #print("Uplift performance")
    #perf <- performance(predUplift[, 1], predUplift[, 2], test$y, test$treat, direction = 1)
    #print(perf)
    #plot(perf[, 8] ~ perf[, 1], type ="l", xlab = "Decile", ylab = "uplift")

    # fit h2o RF
    train$treat <- as.factor(train$treat)
    train$y <- as.factor(train$y)
    trainH2o <- as.h2o(train)
    modelH2o <- h2o.randomForest(x = c("X1", "X2", "X3", "X4", "X5", "X6"), y = "y",
        training_frame = trainH2o,
        uplift_column = "treat",
        uplift_metric = "KL",
        distribution = "bernoulli",
        ntrees = 1000,
        max_depth = 10,
        min_rows = 10,
        nbins = 100,
        seed = 42)

    # predict upliftRF on new data for treatment group
    print("H2O uplift predict on test data")
    testH2o <- as.h2o(test)
    testH2o <- trainH2o
    predH2o <- predict(modelH2o, testH2o)

    #print h2o
    print("Head predictions h2o")
    print(head(predH2o))

    # print upliftRF
    print("Head predictions upliftRF")
    print(head(predUplift))

    res <- as.data.frame(predH2o)
    res$pr.y1_ct1 <- predUplift[,1]
    res$pr.y1_ct0 <- predUplift[,2]
    res$div_ct1 <- res$p_y1_ct1 - res$pr.y1_ct1
    res$div_ct0 <- res$p_y1_ct0 - res$pr.y1_ct0
    print(res)
    print(summary(res))

    #print("H2O gains lift")
    #print(h2o.gainsLift(modelH2o))

    print("UpliftRF performance")
    upliftPerf <- performance(res$pr.y1_ct1, res$pr.y1_ct0, train$y, train$treat, direction=1)
    upliftQini <- qini(upliftPerf)
    print(upliftPerf)
    print(upliftQini)
    print(upliftQini$Qini)


    print("H2O Uplift RF performance")
    h2oPerf <- performance(res$p_y1_ct1, res$p_y1_ct0, train$y, train$treat, direction=1)
    h2oPerfQini <- qini(h2oPerf)
    h2oPerfO <- h2o.performance(modelH2o, trainH2o)
    h2oQini <- h2o.auuc(h2oPerfO)
    print(h2oPerfO)
    print(h2oPerf)
    print(h2oPerfQini)
    print(h2oQini)

    print("Qini AUUC")
    print(paste("UpliftRF: ", upliftQini$Qini))
    print(paste("H2O Uplift perf: ", h2oPerfQini$Qini))
    print(paste("H2O Uplift: ", h2oQini))
    expect_true((h2oPerfQini$Qini - upliftQini$Qini) < 10e-5)

    plot(h2oPerfO, metric='gain')


    # paper Qini AUUC = Area under Qini curve - area under random curve
    # random overall gain = sum(ntc1y1) / sum(ntc1) - sum(ntc0y1) / sum(ntc0) 
    # random incremental gains = cumsum(rep(overall gain / groups, groups))
    testH2oTreat <- as.h2o(test)
    predH2oTreat <- predict(modelH2o, testH2oTreat)
    print(head(predH2oTreat))
    
}

doTest("Random Forest Test: Test H2O RF uplift against uplift.upliftRF", test.uplift)
