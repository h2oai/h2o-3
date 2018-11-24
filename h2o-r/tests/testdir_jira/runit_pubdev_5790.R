setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.csv.longheader <- function() {

  fr <- data.frame(replicate(10000,sample(0:1,10,rep=TRUE)))
  for (i in 1:length(colnames(fr))){
    colnames(fr)[i] <- paste(sample(LETTERS, 30, replace = TRUE), collapse = '')
  }
  
  tmp_file <- tempfile(pattern = "long_header", fileext = "csv")
  tryCatch({
  write.csv(fr, tmp_file)
  h2o.importFile(tmp_file)
  }, finally = {
    unlink(tmp_file)
  })
}

doTest("Long header CSV test", test.csv.longheader)