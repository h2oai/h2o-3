setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

# Test k-means clustering on prostate.csv
test.km.centroid.stats <- function(conn) {
    prostate.h2o <- h2o.importFile(conn, locate("smalldata/logreg/prostate.csv"))
    prostate.km.h2o <- h2o.kmeans(training_frame = prostate.h2o, k = 3, x = colnames(prostate.h2o)[-1])
    print(h2o.centroid_stats(prostate.km.h2o))

    testEnd()
}

doTest("KMeans Test: Centroid Stats", test.km.centroid.stats)