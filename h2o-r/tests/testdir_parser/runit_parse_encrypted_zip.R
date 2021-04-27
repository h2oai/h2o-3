setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

# Tests parsing of encrypted zip file data 

test.parseEncryptedZip <- function() {
  crimes_path_zip <- locate("smalldata/chicago/chicagoCrimes10k.csv.zip")
  crimes_path_zip_aes <- file.path(sandbox(), "crimes.csv.zip.aes")

  keystore_path <- locate("smalldata/extdata/keystore.jks")
  keystore <- h2o.importFile(keystore_path, parse = FALSE)

  decrypt_tool <- h2o.decryptionSetup(keystore, key_alias = "secretKeyAlias",
                                      password = "Password123", cipher = "AES/ECB/PKCS5Padding")

  args <- c(keystore_path, "JCEKS", "secretKeyAlias", "Password123", "AES/ECB/PKCS5Padding", 
            crimes_path_zip, crimes_path_zip_aes)
  tool_result <- h2o.rapids(sprintf('(run_tool "EncryptionTool" ["%s"])', paste(args, collapse = '", "')))
  expect_equal("OK", tool_result$string)

  data <- h2o.importFile(crimes_path_zip)
  print(head(data))

  data_aes <- h2o.importFile(crimes_path_zip_aes, decrypt_tool = decrypt_tool)
  print(head(data_aes))

  expect_equal(as.data.frame(data), as.data.frame(data_aes))
}

doTest("Test Parse Encrypted Zip File Data", test.parseEncryptedZip)
