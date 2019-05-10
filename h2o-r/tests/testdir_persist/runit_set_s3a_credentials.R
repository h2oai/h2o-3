setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.s3.credentials <- function() {
    accessKeyId <- Sys.getenv("AWS_ACCESS_KEY_ID")
    accesSecretKey <- Sys.getenv("AWS_SECRET_ACCESS_KEY")
    
  
    expect_false(nchar(accessKeyId) == 0)
    expect_false(nchar(accessKeyId) == 0)
    
    h2o.set_s3a_credentials(accessKeyId, accesSecretKey)
    file <- h2o.importFile(path = "s3a://test.0xdata.com/h2o-unit-tests/iris.csv")
    expect_false(is.null(file))
    
    h2o.set_s3a_credentials("ab", "cd")
    tryCatch(
      {
        file <- h2o.importFile(path = "s3a://test.0xdata.com/h2o-unit-tests/iris.csv")
        expect_true(FALSE)
      }, error = function(e){
        msg <- e$message
        grepl(msg, "com.amazonaws.services.s3.model.AmazonS3Exception: Forbidden")
      }
    )
    
  
}

doTest("S3 Credentials", test.s3.credentials)
