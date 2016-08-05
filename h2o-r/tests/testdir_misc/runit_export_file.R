setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


#Export file with h2o.export_file and compare with R counterpart when re importing file to check for parity.


test.export.file <- function(parts) {
  pros.hex <- h2o.uploadFile(locate("smalldata/prostate/prostate.csv"))
  pros.hex[,2] <- as.factor(pros.hex[,2])
  pros.hex[,4] <- as.factor(pros.hex[,4])
  pros.hex[,5] <- as.factor(pros.hex[,5])
  pros.hex[,6] <- as.factor(pros.hex[,6])
  pros.hex[,9] <- as.factor(pros.hex[,9])
  p.sid <- h2o.runif(pros.hex)
  pros.train <- h2o.assign(pros.hex[p.sid > .2, ], "pros.train")
  pros.test <- h2o.assign(pros.hex[p.sid <= .2, ], "pros.test")

  myglm <- h2o.glm(x = 3:9, y = 2, training_frame = pros.train, family = "binomial")
  mypred <- h2o.predict(myglm, pros.test)

  fname <- paste(paste0(sample(letters, 3, replace = TRUE), collapse = ""),
                 paste0(sample(0:9, 3, replace = TRUE), collapse = ""),
                 parts, "predict.csv", sep = "_")
  dname <- file.path(sandbox(), fname)

  Log.info("Exporting File...")
  h2o.exportFile(mypred, dname, parts = parts)

  Log.info("Comparing file with R...")
  rfiles <- ifelse(parts > 1, list.files(dname, full.names = TRUE), dname)
  Log.info(sprintf("Results stored in files: %s", paste(rfiles, collapse = ", ")))
  R.pred <- NULL
  # Note: this test doens't actually check the number of part files
  # (it will likely be just a one part file because the input is tiny)
  for (rfile in rfiles) {
    part <- read.csv(rfile, colClasses=c("factor",NA,NA))
    R.pred <- rbind(R.pred, part)
  }
  print(head(R.pred))
  H.pred <- as.data.frame(mypred)
  print(head(H.pred))

  expect_identical(R.pred, H.pred)
}

test.export.file.single <- function() test.export.file(1)
test.export.file.multipart <- function() test.export.file(2)

doTest("Testing Exporting Files (single file)", test.export.file.single)
doTest("Testing Exporting Files (part files)", test.export.file.multipart)