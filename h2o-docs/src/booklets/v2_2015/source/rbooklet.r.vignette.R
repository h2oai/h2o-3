#---------------------------------------------------------------------
#
# Check the R code snippets from the H2O R Vignette.
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

rBooklet <-
function() {
    story1  <- c(h2o:::.h2o.locate("R_Vignette_code_examples/r_start_help.R"),
                 h2o:::.h2o.locate("R_Vignette_code_examples/r_import_file.R"),
                 h2o:::.h2o.locate("R_Vignette_code_examples/r_upload_file.R"),
                 h2o:::.h2o.locate("R_Vignette_code_examples/r_factors.R"),
                 h2o:::.h2o.locate("R_Vignette_code_examples/r_to_factors.R"),
                 h2o:::.h2o.locate("R_Vignette_code_examples/r_as_data_frame.R"),
                 h2o:::.h2o.locate("R_Vignette_code_examples/r_as_h2o.R"),
                 h2o:::.h2o.locate("R_Vignette_code_examples/r_assign.R"),
                 h2o:::.h2o.locate("R_Vignette_code_examples/r_colnames.R"),
                 h2o:::.h2o.locate("R_Vignette_code_examples/r_min_max.R"),
                 h2o:::.h2o.locate("R_Vignette_code_examples/r_quantiles.R"),
                 h2o:::.h2o.locate("R_Vignette_code_examples/r_summary.R"),
                 h2o:::.h2o.locate("R_Vignette_code_examples/r_table.R"),
                 h2o:::.h2o.locate("R_Vignette_code_examples/r_runif.R"),
                 h2o:::.h2o.locate("R_Vignette_code_examples/r_splitframe.R"),
                 h2o:::.h2o.locate("R_Vignette_code_examples/r_getframe.R"),
                 h2o:::.h2o.locate("R_Vignette_code_examples/r_ls.R"),
                 h2o:::.h2o.locate("R_Vignette_code_examples/r_rm.R"),
                 h2o:::.h2o.locate("R_Vignette_code_examples/r_addfunc.R"))
    story2  <- c(h2o:::.h2o.locate("R_Vignette_code_examples/r_cluster_info.R"))
    story3  <- c(h2o:::.h2o.locate("R_Vignette_code_examples/r_ddply.R"))
    story4  <- c(h2o:::.h2o.locate("R_Vignette_code_examples/r_glm_demo.R"))
    story5  <- c(h2o:::.h2o.locate("R_Vignette_code_examples/r_gbm.R"),
                 h2o:::.h2o.locate("R_Vignette_code_examples/r_gbm_multinomial.R"),
                 h2o:::.h2o.locate("R_Vignette_code_examples/r_glm2.R"),
                 h2o:::.h2o.locate("R_Vignette_code_examples/r_kmeans.R"),
                 h2o:::.h2o.locate("R_Vignette_code_examples/r_pca.R"),
                 h2o:::.h2o.locate("R_Vignette_code_examples/r_predict.R"))

    approvedRCodeExamples <- c(story1,story2,story3,story4,story5)

    checkCodeExamplesInDir(approvedRCodeExamples, h2o:::.h2o.locate("R_Vignette_code_examples"))

    checkStory("story1",story1)
    checkStory("story2",story2)
    checkStory("story3",story3)
    checkStory("story4",story4)
    checkStory("story5",story5)
}

doBooklet("R Vignette Booklet", rBooklet)
