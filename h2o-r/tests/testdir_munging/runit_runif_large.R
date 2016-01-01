setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.runif <- function() {
    uploaded_frame <- h2o.uploadFile(h2oTest.locate("bigdata/laptop/mnist/train.csv.gz"))
    r_u <- h2o.runif(uploaded_frame, seed=1234)

    imported_frame <- h2o.importFile(h2oTest.locate("bigdata/laptop/mnist/train.csv.gz"))
    r_i <- h2o.runif(imported_frame, seed=1234)

    print(paste0("This demonstrates that seeding runif on identical frames with different chunk distributions ",
                 "provides different results. upload_file: ", mean(r_u), ", import_frame: ", mean(r_i)))

    
}

h2oTest.doTest("Test runif", test.runif)
