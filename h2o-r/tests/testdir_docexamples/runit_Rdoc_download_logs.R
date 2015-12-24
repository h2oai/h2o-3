setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.rdoc_download_all_logs.golden <- function() {
	

h2o.downloadAllLogs(dirname = getwd(), filename = "h2o_logs.log")
file.info(paste(getwd(), "h2o_logs.log", sep = .Platform$file.sep))
file.remove(paste(getwd(), "h2o_logs.log", sep = .Platform$file.sep))

}

doTest("R Doc Download Logs", test.rdoc_download_all_logs.golden)

