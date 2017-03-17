setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# This test aims to test that our PCA works with wide datasets for the following PCA methods:
# 1. GramSVD: PUBDEV-3694;
# 2. Power: PUBDEV-3858
#
# It will compare the eigenvalues and eigenvectors obtained with the various methods and they should agree
# to within certain tolerance.

check.pca.widedata <- function() {
    df <- h2o.importFile(locate("bigdata/laptop/jira/rotterdam.csv.zip"), destination_frame="df")

    y <- "relapse"
    x <- setdiff(names(df), y)
    df[,y] <- as.factor(df[,y])  #Convert to factor (for binary classification)
    
    # show that pca can run with wide dataset and not throw an error or fit
    h2o_pca <- h2o.prcomp(df, k = 8, x = x, transform = 'STANDARDIZE', impute_missing = TRUE);

    # remove NAs and compare our accuracy with R's PCA
    df <- na.omit(df)
    # generate a R data frame from h2o dataframe for R to use
    dfR = as.data.frame(df)
    dfR = dfR[x]

    ranks = 8
    pcaR <- prcomp(dfR, center = TRUE, scale. = TRUE, rank.=ranks)      # train R PCA
    h2o_pca <- h2o.prcomp(df, k = ranks, x = x, transform = 'STANDARDIZE', seed=12345)      # train H2O PCA
    h2o_power <- h2o.prcomp(df, k = ranks, x = x, transform = 'STANDARDIZE', seed=12345)

    # the eigenvectors and eigvalues calculated from R and H2O are close but not equal, R use random methods too.
    isFlipped1 <- checkPCAModel(h2o_pca, pcaR, tolerance=2e-1, compare_all_importance=FALSE)

    Log.info("Compare Projections into PC space")
    predR <- predict(pcaR, dfR)
    predH2O <- predict(h2o_pca, df)

    reConstructedR <- genReconstructedData(pcaR$rotation[,1:ranks], predR[, 1:ranks])
    reConstructedH2O <- genReconstructedData(h2o_pca@model$eigenvectors, predH2O)

    # scale the original data frame and do comparison
    ndfR = scale(as.matrix(dfR))
    maxDiffR = max(abs(ndfR-reConstructedR))
    maxDiffH2O = max(abs(ndfR-reConstructedH2O))

    # This is the metric we use to figure out if our PCA is performing.  We compare the difference of the
    # maximum error of the reconstructed dataset to the original data set.  If the maximum error from
    # our PCA and R PCA are close, we call it a day and declare that our PCA wide dataset is working.
    expect_true(abs(maxDiffR-maxDiffH2O) < 1e-10, "R and H2O PCA reconstructed dataset differs too much!")

    # compare eigen values and vectors from GramSVD and Power
    Log.info("****** Comparing GramSVD PCA model and Power PCA model...")
    isFlipped1 <- checkPCAModelWork(ranks, h2o_pca@model$importance, h2o_power@model$importance,
    h2o_pca@model$eigenvectors, h2o_power@model$eigenvectors,
    "Compare importance between PCA GramSVD and PCA Power",
    "PCA GramSVD Importance of Components:",
    "PCA Power Importance of Components:", tolerance=1e-6,
    compare_all_importance=TRUE)

}

doTest("PUBDEV-3694 and PUBDEV-3858: PCA with wide dataset for GramSVD, Power", check.pca.widedata)