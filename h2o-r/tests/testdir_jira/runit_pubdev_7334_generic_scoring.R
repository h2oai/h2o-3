setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pubdev_7334 <- function() {
    cc_h2o <- h2o.importFile(locate('smalldata/jira/pubdev_7334.csv'))
    cc_h2o['DEFAULT_PAYMENT_NEXT_MONTH'] <- h2o.asfactor(cc_h2o['DEFAULT_PAYMENT_NEXT_MONTH'])

    drf <- h2o.randomForest(y = 'DEFAULT_PAYMENT_NEXT_MONTH', training_frame = cc_h2o)

    mojo.dir <- sandbox()
    mojo_file <- h2o.download_mojo(drf, mojo.dir)
    mojo <- h2o.import_mojo(file.path(mojo.dir, mojo_file))

    pred_h2o <- as.data.frame(predict(drf, cc_h2o))
    pred_mojo <- as.data.frame(predict(mojo, cc_h2o))

    expect_equal(pred_h2o, pred_mojo)
}

doTest("PUBDEV-7334: Make sure imported MOJO produces correct predictions on DRF models", test.pubdev_7334)
