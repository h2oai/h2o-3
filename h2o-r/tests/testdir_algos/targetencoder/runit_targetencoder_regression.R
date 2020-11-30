setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.targetencoder.regression <- function() {
    data <- h2o.importFile(path=locate('smalldata/gbm_test/titanic.csv'),
                           col.types=list(by.col.name=c("survived"), types=c("factor")))
    ncols <- h2o.ncol(data)
    nrows <- h2o.nrow(data)
    to_encode <- c('home.dest', 'cabin', 'embarked')

    target_encoder <- h2o.targetencoder(training_frame=data,
                                        x=to_encode,
                                        y="fare")
    encoded_data <- h2o.transform(target_encoder, data)
    expect_false(is.null(encoded_data))
    expect_equal(ncols + length(to_encode), h2o.ncol(encoded_data))
    expect_equal(nrows, h2o.nrow(encoded_data))

    all_res_columns <- names(encoded_data)
    encoded_columns <- unlist(lapply(to_encode, function(col) paste0(col, "_te")))
    expect_true(all(to_encode %in% all_res_columns))
    expect_true(all(encoded_columns %in% all_res_columns))
}


doTest("Target Encoder Regression test", test.targetencoder.regression)
