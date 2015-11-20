#---------------------------------------------------------------------
#
# Check the R code snippets from the H2O GBM Vignette.
#
# The snippets are broken out into separate files so the exact same
# piece of code both shows up in the document and is checked by this
# script.
#
# Check consists of:
# 1. Check that all of the approved code examples are present in the
#    Deeplearning_Vignette_code_examples directory
# 2. Combine the related, individual code examples (paragraphs) into coherent stories
# 3. Execute each story
#
#---------------------------------------------------------------------

gbmBooklet <-
function() {
    approvedRCodeExamples <- c(
    h2o:::.h2o.locate("GBM_Vignette_code_examples/gbm_uploadfile_example.R"),
    h2o:::.h2o.locate("GBM_Vignette_code_examples/gbm_examplerun.R"),
    h2o:::.h2o.locate("GBM_Vignette_code_examples/gbm_examplerun_stochastic.R"),
    h2o:::.h2o.locate("GBM_Vignette_code_examples/gbm_extractmodelparams.R"),
    h2o:::.h2o.locate("GBM_Vignette_code_examples/gbm_predict.R"),
    h2o:::.h2o.locate("GBM_Vignette_code_examples/gbm_gridsearch.R"),
    h2o:::.h2o.locate("GBM_Vignette_code_examples/gbm_gridsearch_result.R"))

    checkCodeExamplesInDir(approvedRCodeExamples, h2o:::.h2o.locate("GBM_Vignette_code_examples"))

    story1 <- approvedRCodeExamples
    checkStory("story1",story1)
}

doBooklet("GBM Vignette Booklet", gbmBooklet)
