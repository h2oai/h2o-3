setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


#Export file with h2o.export_file with a custom separator


test.export.file.separator <- function() {
    frame_r <- data.frame(C1 = c(11, 12), C2 = c(21, 22))
    frame_hex <- as.h2o(frame_r)
    
    target_default <- file.path(sandbox(), "separator_export_default.csv")
    target_custom <- file.path(sandbox(), "separator_export_custom.csv")

    Log.info("Exporting File (default separator - ',')...")
    h2o.exportFile(frame_hex, target_default)

    Log.info("Exporting File (custom separator - '|')...")
    h2o.exportFile(frame_hex, target_custom, sep="|")

    parsed_default <- read.csv(target_default, header=TRUE, sep = ",")
    parsed_custom <- read.csv(target_custom, header=TRUE, sep = "|")

    expect_equal(frame_r, parsed_default)
    expect_equal(frame_r, parsed_custom)
}

doTest("Testing Exporting Files (custom separator)", test.export.file.separator)
