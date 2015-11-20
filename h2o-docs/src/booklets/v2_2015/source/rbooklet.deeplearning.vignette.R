#---------------------------------------------------------------------
#
# Check the R code snippets from the H2O DeepLearning Vignette.
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

deeplearningBooklet <-
function() {
    approvedRCodeExamples <- c(
    h2o:::.h2o.locate("DeepLearning_Vignette_code_examples/deeplearning_importfile_example.R"),
    h2o:::.h2o.locate("DeepLearning_Vignette_code_examples/deeplearning_examplerun.R"),
    h2o:::.h2o.locate("DeepLearning_Vignette_code_examples/deeplearning_crossval.R"),
    h2o:::.h2o.locate("DeepLearning_Vignette_code_examples/deeplearning_inspect_model.R"),
    h2o:::.h2o.locate("DeepLearning_Vignette_code_examples/deeplearning_predict.R"),
    h2o:::.h2o.locate("DeepLearning_Vignette_code_examples/deeplearning_varimp.R"),
    h2o:::.h2o.locate("DeepLearning_Vignette_code_examples/deeplearning_gridsearch.R"),
    h2o:::.h2o.locate("DeepLearning_Vignette_code_examples/deeplearning_gridsearch_result.R"),
    h2o:::.h2o.locate("DeepLearning_Vignette_code_examples/deeplearning_checkpoint.R"),
    h2o:::.h2o.locate("DeepLearning_Vignette_code_examples/deeplearning_savemodel.R"),
    h2o:::.h2o.locate("DeepLearning_Vignette_code_examples/deeplearning_loadmodel_checkpoint.R"),
    h2o:::.h2o.locate("DeepLearning_Vignette_code_examples/deeplearning_getmodel.R"),
    h2o:::.h2o.locate("DeepLearning_Vignette_code_examples/deeplearning_anomaly.R"))

    checkCodeExamplesInDir(approvedRCodeExamples, h2o:::.h2o.locate("DeepLearning_Vignette_code_examples"))

    story1 <- approvedRCodeExamples
    checkStory("story1",story1)
}

doBooklet("Deeplearning Vignette Booklet", deeplearningBooklet)
