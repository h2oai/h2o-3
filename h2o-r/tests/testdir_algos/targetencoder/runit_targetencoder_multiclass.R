setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.targetencoder.multiclass <- function() {
    data <- h2o.importFile(path=locate('smalldata/gbm_test/titanic.csv'),
                           col.types=list(by.col.name=c("survived", "pclass"), types=c("factor", "factor")))
    
    ncols <- h2o.ncol(data)
    nrows <- h2o.nrow(data)
    to_encode <- c('home.dest', 'cabin', 'embarked')
    target_cardinality <- 3
    target_values_for_te <- c("2", "3") # for some reason, with R client, the "Class " prefix in the value is lost

    target_encoder <- h2o.targetencoder(training_frame=data,
                                        x=to_encode,
                                        y="pclass")
    encoded_data <- h2o.transform(target_encoder, data)
    expect_false(is.null(encoded_data))
    expect_equal(ncols + length(to_encode)*(target_cardinality-1), h2o.ncol(encoded_data))
    expect_equal(nrows, h2o.nrow(encoded_data))

    all_res_columns <- names(encoded_data)
    encoded_columns <- c(
        unlist(lapply(to_encode, function(col) paste0(col, "_", target_values_for_te[1], "_te"))),
        unlist(lapply(to_encode, function(col) paste0(col, "_", target_values_for_te[2], "_te")))
    )
    expect_true(all(to_encode %in% all_res_columns))
    print(encoded_columns)
    print(all_res_columns)
    expect_true(all(encoded_columns %in% all_res_columns))
}


doTest("Target Encoder Multiclass test", test.targetencoder.multiclass)
