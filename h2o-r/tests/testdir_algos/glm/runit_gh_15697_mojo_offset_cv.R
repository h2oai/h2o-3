setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
library(data.table)

test_glm_mojo_offset_fold_columns <- function() {
    mt <- as.h2o(mtcars)
    
    find_offset <- h2o.glm(
        x = c("cyl", "disp"),
        y = "mpg",
        training_frame = mt,
        lambda = 0
    )
    
    h2o.residual_deviance(find_offset)
    # [1] 270.7403
    
    # create offset to look exactly like it's in the model (easy comparison)
    mt$offset <- mt$cyl * find_offset@model$coefficients[["cyl"]]
    mt$fold <- h2o.kfold_column(data = mt, nfolds = 3, seed = 123)
    
    # move "cyl" from being modeled to be an offset
    # build with a fold column
    mod_w_offset <- h2o.glm(
        x = c("disp"),
        y = "mpg",
        training_frame = mt,
        offset = "offset",
        lambda = 0,
        fold_column = "fold"
    )
    
    h2o.residual_deviance(mod_w_offset)
    # [1] 270.7403 (match as expected)
    
    # save out models then immediately reimport
    mojo_path <- h2o.save_mojo(object = mod_w_offset, path = ".")
    biny_path <- h2o.saveModel(object = mod_w_offset, path = ".")
    
    mojo <- h2o.import_mojo(mojo_file_path = mojo_path)
    genericGLM <- h2o.genericModel(mojo_path)
    biny <- h2o.loadModel(path = biny_path)
    

    predict <- h2o.predict(object = mod_w_offset, newdata = mt)
    predict0 <- h2o.predict(object = mojo, newdata = mt)
    predict1 <- h2o.predict(object = biny, newdata = mt)
    predict2 <- h2o.predict(object = genericGLM, newdata = mt)
    
    # check all predicts match
    compareFrames(predict, predict0)
    compareFrames(predict, predict1)
    compareFrames(predict, predict2)
    
    # remove the fold column in the dataset for prediction
    predict3 <- h2o.predict(object = mojo, newdata = mt[-c(13)])
    predict4 <- h2o.predict(object = genericGLM, newdata = mt[-c(13)])
    # make sure prediction still matches after removing fold column when calling predict
    compareFrames(predict, predict3)
    compareFrames(predict, predict4)
}

doTest("Fix GLM mojo with offset and fold column", test_glm_mojo_offset_fold_columns)
