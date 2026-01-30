setwd(normalizePath(dirname(R.utils::commandArgs(asValues = TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


test.uplift <- function() {
    ntrees <- 3
    mtries <- 6
    seed <- 42
    uplift_metric <- "KL"
    set.seed(seed)

    # Test data preparation for each implementation
    x <- c("feature_1", "feature_2", "feature_3", "feature_4", "feature_5", "feature_6")
    y <- "outcome"
    treatment_col <- "treatment"

    # Test data preparation for each implementation
    train <- h2o.importFile(path=locate("smalldata/uplift/upliftml_train.csv"), 
                            col.types=list(by.col.name=c(treatment_col, y), types=c("factor", "factor")))
    test <- h2o.importFile(path=locate("smalldata/uplift/upliftml_test.csv"), 
                           col.types=list(by.col.name=c(treatment_col, y), types=c("factor", "factor")))

    model <- h2o.upliftRandomForest(
            x = x,
            y = y,
            training_frame = train,
            validation_frame = test,
            treatment_column = treatment_col,
            uplift_metric = uplift_metric,
            auuc_type = "qini",
            distribution = "bernoulli",
            ntrees = ntrees,
            mtries = mtries,
            max_depth = 10,
            min_rows = 10,
            nbins = 100,
            seed = seed)

    print(model)
    pred.uplift <- h2o.predict(model, test)
    pred.uplift.df <- as.data.frame(pred.uplift)

    tmpdir <- tempdir()
    modelfile <- h2o.download_mojo(model, path=tmpdir)
    modelpath <- paste0(tmpdir, "/", modelfile)
    
    model.mojo <- h2o.import_mojo(modelpath)
    print(model.mojo)
    pred.mojo <- h2o.predict(model.mojo, test)
    pred.mojo.df <- as.data.frame(pred.mojo)

    expect_equal(pred.mojo.df[1,1], pred.uplift.df[1,1])
    expect_equal(pred.mojo.df[2,1], pred.uplift.df[2,1])
    expect_equal(pred.mojo.df[10,1], pred.uplift.df[10,1])
    expect_equal(pred.mojo.df[42,1], pred.uplift.df[42,1])
    expect_equal(pred.mojo.df[550,1], pred.uplift.df[550,1])
    expect_equal(pred.mojo.df[666,1], pred.uplift.df[666,1])

    perf.uplift <- h2o.performance(model)
    print(perf.uplift)
    auuc.uplift <- h2o.auuc(perf.uplift)
    print(auuc.uplift)

    perf.mojo <- h2o.performance(model.mojo)
    print(perf.mojo)
    auuc.mojo <- h2o.auuc(perf.mojo)
    print(auuc.mojo)

    expect_equal(auuc.uplift, auuc.mojo)

    on.exit(unlink(modelpath,recursive=TRUE))
    on.exit(unlink(tmpdir,recursive=TRUE))
}

doTest("Uplift Random Forest Test: Test H2O RF uplift", test.uplift)
