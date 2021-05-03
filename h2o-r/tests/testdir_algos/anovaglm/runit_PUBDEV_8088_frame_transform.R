setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# make sure anovaGlm frame transformation is working
test.model.anovaglm.frame.transformation <- function() {
    train <- h2o.importFile(path = locate("smalldata/anovaGlm/Moore.csv"))
    answer <- h2o.importFile(path = locate("smalldata/anovaGlm/MooreTransformed.csv"))
    model <- h2o.anovaglm(y = "conformity", x = c(1,3),  training_frame = train, family = "gaussian", save_transformed_framekeys = TRUE)
    # get transform frame
    transform_names <-c('fcategory_high', 'fcategory_low', 'partner.status_high', 
                    'fcategory_high:partner.status_high', 
                    'fcategory_low:partner.status_high')
    answer_names <-c('fcategory1', 'fcategory2', 'partner.status1', 
                       'fcategory1:partner.status1', 'fcategory2:partner.status1')
    transformF <- h2o.getFrame(model@model$transformed_columns_key)
    compareFrames(answer[answer_names], transformF[transform_names ], prob=1)
}

doTest("ANOVA GLM frame transformation with Gaussian family", test.model.anovaglm.frame.transformation)
