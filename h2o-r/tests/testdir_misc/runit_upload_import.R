setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.upload.import.small <- function() {
    various_datasets = c("smalldata/iris/iris.csv", "smalldata/iris/iris_wheader.csv", "smalldata/prostate/prostate.csv",
                            "smalldata/prostate/prostate_woheader.csv.gz")

    for(dataset in various_datasets){
        uploaded_frame <- h2o.uploadFile(h2oTest.locate(dataset))
        imported_frame <- h2o.importFile(h2oTest.locate(dataset))

        rows_u <- nrow(uploaded_frame)
        rows_i <- nrow(imported_frame)
        cols_u <- ncol(uploaded_frame)
        cols_i <- ncol(imported_frame)

        print(paste0("rows upload: ", rows_u, ", rows import: ", rows_i))
        print(paste0("cols upload: ", cols_u, ", cols import: ", cols_i))
        expect_equal(rows_u, rows_i, info="Expected same number of rows regardless of method.")
        expect_equal(cols_u, cols_i, info="Expected same number of cols regardless of method.")
    }

    
}

h2oTest.doTest("Test upload import", test.upload.import.small)
