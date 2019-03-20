setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.model.generic.glm <- function() {
    data <- h2o.importFile(path = locate('smalldata/testng/airlines_train.csv'))
    cols <- c("Origin", "IsDepDelayed")
    model.original <- h2o.glm(x=cols, y = "Distance", training_frame = data)

    mojo.original.name <- h2o.download_mojo(model = model.original, path = tempdir())
    mojo.original.path <- paste0(tempdir(),"/",mojo.original.name)
    
    model.generic <- h2o.genericModel(mojo.original.path)
    model.generic.preds  <- h2o.predict(model.generic, data)
    expect_equal(length(model.generic.preds), 1)
    expect_equal(h2o.nrow(model.generic.preds), 24421)
    model.generic.path <- h2o.download_mojo(model = model.generic, path = tempdir())
    expect_equal(file.size(paste0(tempdir(),"/",model.generic.path)), file.size(mojo.original.path))
    
}

doTest("Generic model from GLM MOJO", test.model.generic.glm )
