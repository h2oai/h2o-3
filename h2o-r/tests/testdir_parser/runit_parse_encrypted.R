setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

# Tests parsing of encrypted data

test.parseEncrypted <- function() {
  keystore_path <- locate("smalldata/extdata/keystore.jks")
  keystore <- h2o.importFile(keystore_path, parse = F)
  decrypt_tool <- h2o.decryptionSetup(keystore, key_alias = "secretKeyAlias",
                                      password = "Password123", cipher = "AES/ECB/PKCS5Padding")
  print(decrypt_tool)
  data_path <- locate("smalldata/extdata/prostate.csv.aes")
  data <- h2o.importFile(data_path, decrypt_tool = decrypt_tool)

  expected <- read.csv(locate("smalldata/extdata/prostate.csv"))
  expect_equal(as.data.frame(data), expected)
}

doTest("Test Parse Encrypted data", test.parseEncrypted)