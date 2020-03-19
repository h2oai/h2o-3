setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


#Export file with h2o.export_file compressed with 'gzip'


test.export.file.gzip <- function() {
    prostate_hex <- h2o.importFile(locate("smalldata/prostate/prostate.csv"))

    target <- file.path(sandbox(), "prostate_export.csv.gzip")

    Log.info("Exporting File (gzip)...")
    h2o.exportFile(prostate_hex, target, compression = "gzip")

    prostate_gzip <- read.csv(gzfile(target), header=TRUE)
    prostate_r <- as.data.frame(prostate_hex)
    
    expect_equal(prostate_r, prostate_gzip)
}

doTest("Testing Exporting Files (gzip compressed)", test.export.file.gzip)
