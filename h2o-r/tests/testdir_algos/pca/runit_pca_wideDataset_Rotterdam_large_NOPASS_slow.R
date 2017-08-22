setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# This test aims to test that our PCA works with wide datasets for the following PCA methods:
# 1. GramSVD: PUBDEV-3694;
# 2. Power: PUBDEV-3858
#
# It will compare the eigenvalues and eigenvectors obtained with the various methods and they should agree
# to within certain tolerance.
#
# To cut down on execution time, I will only compare GramSVD with one of the following models built at random
#  at each test: R, Power or Randomized

check.pca.widedata <- function() {
    df <- h2o.importFile(locate("bigdata/laptop/jira/rotterdam.csv.zip"), destination_frame="df")

    y <- "relapse"
    x <- setdiff(names(df), y)
    df[,y] <- as.factor(df[,y])  #Convert to factor (for binary classification)

    # remove NAs and compare our accuracy with R's PCA
    df <- na.omit(df)

    runEachModel = c(FALSE, FALSE, FALSE)
    runEachModel[sample(1:length(runEachModel),1,replace=F)] = TRUE
    expNum=1

    ranks = 8
    h2o_pca <- h2o.prcomp(df, k = ranks, x = x, transform = 'STANDARDIZE', seed=12345)      # train H2O PCA GramSVD

    if (runEachModel[expNum]) {
        # generate a R data frame from h2o dataframe for R to use
        dfR = as.data.frame(df)
        dfR = dfR[x]
        pcaR <- prcomp(dfR, center = TRUE, scale. = TRUE, rank.=ranks)      # train R PCA
        # the eigenvectors and eigvalues calculated from R and H2O are close but not equal, R use random methods.
        isFlipped1 <- checkPCAModel(h2o_pca, pcaR, tolerance=1, compare_all_importance=FALSE)
    }
    expNum=expNum+1

    if (runEachModel[expNum]) {
        h2o_power <- h2o.prcomp(df, k = ranks, x = x, transform = 'STANDARDIZE', seed=12345, pca_method="Power")
        # compare eigen values and vectors from GramSVD and Power
        Log.info("****** Comparing GramSVD PCA model and Power PCA model...")
        isFlipped1 <- checkPCAModelWork(ranks, h2o_pca@model$importance, h2o_power@model$importance,
        h2o_pca@model$eigenvectors, h2o_power@model$eigenvectors,
        "Compare importance between PCA GramSVD and PCA Power",
        "PCA GramSVD Importance of Components:",
        "PCA Power Importance of Components:", tolerance=1, compare_all_importance=TRUE)
        h2o.rm(h2o_power)
    }
    expNum = expNum+1

    if (runEachModel[expNum]) {
        h2o_randomized <- h2o.prcomp(df, k = ranks, x = x, max_iterations=10,  transform = 'STANDARDIZE', seed=12345,
        pca_method="Randomized")
        # compare eigen values and vectors from GramSVD and Randomized
        Log.info("****** Comparing GramSVD PCA model and Randomized PCA model...")
        isFlipped1 <- checkPCAModelWork(ranks, h2o_pca@model$importance, h2o_randomized@model$importance,
        h2o_pca@model$eigenvectors, h2o_randomized@model$eigenvectors,
        "Compare importance between PCA GramSVD and PCA Randomized",
        "PCA GramSVD Importance of Components:",
        "PCA Randomized Importance of Components:", tolerance=1,
        compare_all_importance=TRUE)
        h2o.rm(h2o_randomized)
    }
    h2o.rm(h2o_pca)
    h2o.rm(df)
}

doTest("PUBDEV-3694 and PUBDEV-3858: PCA with wide dataset for GramSVD, Power", check.pca.widedata)