setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



# Test k-means clustering on prostate.csv
test.km.centroid.stats <- function() {
    prostate.h2o <- h2o.importFile( h2oTest.locate("smalldata/logreg/prostate.csv"))
    prostate.km.h2o <- h2o.kmeans(training_frame = prostate.h2o, k = 3, x = colnames(prostate.h2o)[-1])
    print(h2o.centroid_stats(prostate.km.h2o))

    
}

h2oTest.doTest("KMeans Test: Centroid Stats", test.km.centroid.stats)
