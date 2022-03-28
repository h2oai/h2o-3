setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.ExtendedIsolationForest.downloadAndImportMojoGiveTheSameResult <- function() {
    set.seed(1234)
    N = 1e4
    random_data <- data.frame(
        x = c(rnorm(N, 0, 0.5), rnorm(N*0.05, -1.5, 1)),
        y = c(rnorm(N, 0, 0.5), rnorm(N*0.05,  1.5, 1))
    )
    random_data.hex <- as.h2o(random_data)

    exisofor.model <- h2o.extendedIsolationForest(training_frame = random_data.hex)
    print(exisofor.model)
    predict <- h2o.predict(exisofor.model, random_data.hex)

    eif_mojo_path <- h2o.download_mojo(exisofor.model, path = tempdir())

    generic_eif <- h2o.import_mojo(file.path(tempdir(), eif_mojo_path))
    print(generic_eif)
    generic_predict <- h2o.predict(generic_eif, random_data.hex)

    print(predict)
    print(generic_predict)

    expect_false(is.na(exisofor.model@model$model_summary$seed))

    expect_equal(predict, generic_predict, tolerance = 0.1, scale = 1)
    expect_equal(as.character(class(generic_eif)), "H2OAnomalyDetectionModel")
    expect_equal(generic_eif@algorithm, "generic")
}

doTest("ExtendedIsolationForest: Smoke Test", test.ExtendedIsolationForest.downloadAndImportMojoGiveTheSameResult)
