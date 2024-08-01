setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

# Test to make sure that when we pass an illegal command to
# AstRunTool will not crash H2O.

test.astRunTimeBadCommands <- function() {
  iris_path_gz <- locate("smalldata/junit/iris.csv.gz")
  iris_path_gz_aes <- file.path(sandbox(), "iris.csv.gz.aes")

  keystore_path <- locate("smalldata/extdata/keystore.jks")
  keystore <- h2o.importFile(keystore_path, parse = FALSE)
  decrypt_tool <- h2o.decryptionSetup(keystore, key_alias = "secretKeyAlias",
                                      password = "Password123", cipher = "AES/ECB/PKCS5Padding")

  args <- c(keystore_path, "JCEKS", "secretKeyAlias", "Password123", "AES/ECB/PKCS5Padding", 
            iris_path_gz, iris_path_gz_aes)
  # command is wrong
  tryCatch({  
    tool_result <- h2o.rapids(sprintf('(run_tool "EncryptionTools" ["%s"])', paste(args, collapse = '", "')))},
    error = function(e) {print(e)})
  iris_file <- h2o.importFile(locate("smalldata/junit/iris.csv.gz"))
  expect_true(h2o.clusterIsUp())
  # passcode is wrong
  args[4] <- "password"
  tryCatch({  
    tool_result <- h2o.rapids(sprintf('(run_tool "EncryptionTool" ["%s"])', paste(args, collapse = '", "')))},
    error = function(e) {print(e)})
  iris_file <- h2o.importFile(locate("smalldata/junit/iris.csv.gz"))
  expect_true(h2o.clusterIsUp())
  # keystore_path is bad
  args[4] = "Password123"
  args[1] = "/bad/Directory"
  tryCatch({  
    tool_result <- h2o.rapids(sprintf('(run_tool "EncryptionTool" ["%s"])', paste(args, collapse = '", "')))},
    error = function(e) {print(e)})
  expect_true(h2o.clusterIsUp())
}

doTest("Test AstRunTool.java when passed with illegal commands will not crash.", test.astRunTimeBadCommands)
