setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# make sure anovaGlm frame transformation is working
test.model.anovaglm.frame.transformation <- function() {
    train <- h2o.importFile(path = locate("smalldata/anovaGlm/Moore.csv"))
    answer <- h2o.importFile("smalldata/anovaGlm/MooreTransformed.csv")
    aModel <- h2o.anovaglm(y = "conformity", x = c(1,3),  training_frame = train, family = "gaussian", save_transformed_framekeys = TRUE)
    browser()
    # get transform frame
    transformNames <-c('fcategory_high', 'fcategory_low', 'partner.status_high', 
                    'fcategory_high:partner.status_high', 
                    'fcategory_low:partner.status_high')
    answerNames <-c('fcategory1', 'fcategory2', 'partner.status1', 
                       'fcategory1:partner.status1', 'fcategory2:partner.status1')
    transformF <- h2o.getFrame(aModel@model$transformed_columns_key)
    compareFrames(answer[answerNames], transformF[transformNames ], prob=1)
}

doTest("AnovaGLM frame transformation with Gaussian family", test.model.anovaglm.frame.transformation)
