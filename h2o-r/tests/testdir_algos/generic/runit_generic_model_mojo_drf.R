setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.model.generic.drf <- function() {
    data <- h2o.importFile(path = locate('smalldata/testng/airlines_train.csv'))
    cols <- c("Origin", "Distance")
    model.original <- h2o.randomForest(x=cols, y = "IsDepDelayed", training_frame = data)

    mojo.original.name <- h2o.download_mojo(model = model.original, path = tempdir())
    mojo.original.path <- paste0(tempdir(),"/",mojo.original.name)
    
    model.generic <- h2o.genericModel(mojo.original.path)
    model.generic.preds  <- h2o.predict(model.generic, data)
    expect_equal(length(model.generic.preds), 3)
    expect_equal(length(h2o.nrow(model.generic.preds)), 24421)
    model.generic.path <- h2o.download_mojo(model = model.generic, path = tmpdir())
    expect_equal(file.size(model.generic.path), file.size(model.original.path))
    
}

doTest("Generic model from DRF MOJO", test.model.generic.drf )
