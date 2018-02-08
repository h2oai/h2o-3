setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.glm.interactions_valid <- function() {

    df <- h2o.importFile(locate("smalldata/airlines/allyears2k_headers.zip"))
    df[is.na(df$Distance), "Distance"] <- h2o.mean(df$Distance, na.rm = TRUE)
    df[is.na(df$CRSElapsedTime), "CRSElapsedTime"] <- h2o.mean(df$CRSElapsedTime, na.rm = TRUE)

    XY <- names(df)[c(1,2,3,4,6,8,9,13,17,19,31)]
    interactions <- XY[c(7,9)]

    # Expand all features (not just interactions)
    df.expanded.1 <- h2o.cbind(.getExpanded(df[,c(XY)], interactions = c(interactions), T, F, F), df$IsDepDelayed)

    # Expand just interactions and use a "trick" to expand to 1-hot encode the categoricals
    df.trick <- df
    df.trick$trick <- "A"
    df.trick$trick <- as.factor(df.trick$trick)
    df.expanded.2 <- h2o.cbind(.getExpanded(df.trick[, c("trick", interactions)], interactions = c("trick", interactions), T, F, T), df.trick[, c("trick", XY)])
    df.expanded.2$trick <- NULL
    df.expanded.2$trick.A <- NULL
    df.expanded.2$Origin <- NULL
    df.expanded.2$UniqueCarrier <- NULL
    colnames(df.expanded.2) <- sub("trick_", "", colnames(df.expanded.2), fixed = TRUE)
    colnames(df.expanded.2) <- sub('\\.A_', ".", colnames(df.expanded.2))

    # Dimensions are ok
    expect_equal(dim(df.expanded.1), dim(df.expanded.2))
    # Column content matches
    for (col in colnames(df.expanded.1)) {
        expect_true(h2o.all(df.expanded.1[col] == df.expanded.2[col]))
    }
}

doTest("Tests GLM interactions with a validation frame", test.glm.interactions_valid)