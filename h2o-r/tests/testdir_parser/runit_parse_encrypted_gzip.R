setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

# Tests parsing of encrypted gzip file data 

test.parseEncryptedGzip <- function() {
  iris_path_gz <- locate("smalldata/junit/iris.csv.gz")
  iris_path_gz_aes <- file.path(sandbox(), "iris.csv.gz.aes")

  keystore_path <- locate("smalldata/extdata/keystore.jks")
  keystore <- h2o.importFile(keystore_path, parse = FALSE)

  decrypt_tool <- h2o.decryptionSetup(keystore, key_alias = "secretKeyAlias",
                                      password = "Password123", cipher = "AES/ECB/PKCS5Padding")

  args <- c(keystore_path, "JCEKS", "secretKeyAlias", "Password123", "AES/ECB/PKCS5Padding", 
            iris_path_gz, iris_path_gz_aes)
  tool_result <- h2o.rapids(sprintf('(run_tool "EncryptionTool" ["%s"])', paste(args, collapse = '", "')))
  expect_equal("OK", tool_result$string)

  data <- h2o.importFile(iris_path_gz)
  print(head(data))

  data_aes <- h2o.importFile(iris_path_gz_aes, decrypt_tool = decrypt_tool)
  print(head(data_aes))

  expect_equal(as.data.frame(data), as.data.frame(data_aes))
}

doTest("Test Parse Encrypted Gzip File Data", test.parseEncryptedGzip)
