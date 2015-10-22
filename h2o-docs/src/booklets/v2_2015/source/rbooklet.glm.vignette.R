#---------------------------------------------------------------------
#
# Check the R code snippets from the H2O GLM Vignette.
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

glmBooklet <-
function() {
    story1  <- c(h2o:::.h2o.locate("GLM_Vignette_code_examples/glm_gaussian_example.R"))
    story2  <- c(h2o:::.h2o.locate("GLM_Vignette_code_examples/glm_binomial_example.R"))
    story3  <- c(h2o:::.h2o.locate("GLM_Vignette_code_examples/glm_poisson_example.R"))
    story4  <- c(h2o:::.h2o.locate("GLM_Vignette_code_examples/glm_gamma_example.R"))
    story5  <- c(h2o:::.h2o.locate("GLM_Vignette_code_examples/coerce_column_to_factor.R"))
    story6  <- c(h2o:::.h2o.locate("GLM_Vignette_code_examples/glm_stopping_criteria.R"))
    story7  <- c(h2o:::.h2o.locate("GLM_Vignette_code_examples/glm_cross_validation.R"))
    story8  <- c(h2o:::.h2o.locate("GLM_Vignette_code_examples/glm_grid_search_over_alpha.R"))
    story9  <- c(h2o:::.h2o.locate("GLM_Vignette_code_examples/glm_model_output_10.R"),
                 h2o:::.h2o.locate("GLM_Vignette_code_examples/glm_model_output_20.R"),
                 h2o:::.h2o.locate("GLM_Vignette_code_examples/glm_model_output_30.R"),
                 h2o:::.h2o.locate("GLM_Vignette_code_examples/glm_model_output_40.R"),
                 h2o:::.h2o.locate("GLM_Vignette_code_examples/glm_accessors.R"),
                 h2o:::.h2o.locate("GLM_Vignette_code_examples/glm_confusion_matrix.R"),
                 h2o:::.h2o.locate("GLM_Vignette_code_examples/glm_scoring_history.R"))
    story10 <- c(h2o:::.h2o.locate("GLM_Vignette_code_examples/glm_binomial_predictions_with_response.R"),
                 h2o:::.h2o.locate("GLM_Vignette_code_examples/glm_binomial_predictions_without_response.R"),
                 h2o:::.h2o.locate("GLM_Vignette_code_examples/glm_recalculate_predict.R"))
    story11 <- c(h2o:::.h2o.locate("GLM_Vignette_code_examples/glm_download_pojo.R"))
    story12 <- c(h2o:::.h2o.locate("GLM_Vignette_code_examples/glm_compare_cross_validation_folds.R"))

    approvedRCodeExamples <- c(story1,story2,story3,story4,story5,story6,story7,story8,story9,story10,story11,story12)

    checkCodeExamplesInDir(approvedRCodeExamples, h2o:::.h2o.locate("GLM_Vignette_code_examples"))

    checkStory("story1",story1)
    checkStory("story2",story2)
    checkStory("story3",story3)
    checkStory("story4",story4)
    checkStory("story5",story5)
    checkStory("story6",story6)
    checkStory("story7",story7)
    checkStory("story8",story8)
    checkStory("story9",story9)
    checkStory("story10",story10)
    checkStory("story11",story11)
    checkStory("story12",story12)
}

doBooklet("GLM Vignette Booklet", glmBooklet)
