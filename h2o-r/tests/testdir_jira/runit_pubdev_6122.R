setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pdp.save <- function() {
    data <- h2o.importFile(path = locate('smalldata/prostate/prostate_cat_NA.csv'))
    cols <- c("AGE","RACE","DCAPS")
    model <- h2o.gbm(x=cols, y = "CAPSULE", training_frame = data)

    temp_filename_no_extension <- tempfile(pattern = "pdp", tmpdir = tempdir(), fileext = "")
    plot <- h2o.partialPlot(object = model, newdata = data, save_to = temp_filename_no_extension)
    expect_false(is.null(plot))

    check_file <- function(feature){
      filepath <- paste0(temp_filename_no_extension,'_',feature,'.png')
      expect_true(file.size(filepath) > 0)
      unlink(filepath)
    }

    lapply(cols, check_file)
}

doTest("Saving partial plot", test.pdp.save)
