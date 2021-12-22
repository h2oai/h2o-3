setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.s3.credentials <- function() {
    accessKeyId <- Sys.getenv("AWS_ACCESS_KEY_ID")
    accesSecretKey <- Sys.getenv("AWS_SECRET_ACCESS_KEY")
    
  
    expect_false(nchar(accessKeyId) == 0)
    expect_false(nchar(accessKeyId) == 0)
    
    h2o.set_s3_credentials(accessKeyId, accesSecretKey)
    file <- h2o.importFile(path = "s3://test.0xdata.com/h2o-unit-tests/iris.csv")
    expect_false(is.null(file))
    
    h2o.set_s3_credentials("ab", "cd")
    tryCatch(
      {
        file <- h2o.importFile(path = "s3://test.0xdata.com/h2o-unit-tests/iris.csv")
        expect_true(FALSE)
      }, error = function(e){
        msg <- e$message
        grepl("The AWS Access Key Id you provided does not exist in our records\\. \\(Service: Amazon S3; Status Code: 403; Error Code: InvalidAccessKeyId", msg)
      }
    )
    
  
}

doTest("S3 Credentials", test.s3.credentials)
