setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.s3.credentials <- function() {
    awsCredsPrefix <- Sys.getenv("S3_CREDS_TEST_PREFIX",
                        Sys.getenv("AWS_CREDS_PREFIX_S3_DEV",
                          Sys.getenv("AWS_CREDS_PREFIX", "")))
    accessKeyId <- Sys.getenv(paste0(awsCredsPrefix, "AWS_ACCESS_KEY_ID"))
    accesSecretKey <- Sys.getenv(paste0(awsCredsPrefix, "AWS_SECRET_ACCESS_KEY"))
    sessionToken <- Sys.getenv(paste0(awsCredsPrefix, "AWS_SESSION_TOKEN"))
    if (nchar(sessionToken) == 0) sessionToken <- NULL

    s3Path <- Sys.getenv("S3_CREDS_TEST_PATH", "s3://test.0xdata.com/h2o-unit-tests/iris.csv")

    expect_false(nchar(accessKeyId) == 0)
    expect_false(nchar(accessKeyId) == 0)

    h2o.set_s3_credentials(accessKeyId, accesSecretKey, sessionToken)
    file <- h2o.importFile(path = s3Path)
    expect_false(is.null(file))

    h2o.set_s3_credentials("ab", "cd")
    tryCatch(
      {
        file <- h2o.importFile(path = s3Path)
        expect_true(FALSE)
      }, error = function(e){
        msg <- e$message
        grepl("The AWS Access Key Id you provided does not exist in our records\\. \\(Service: Amazon S3; Status Code: 403; Error Code: InvalidAccessKeyId", msg)
      }
    )


}

doTest("S3 Credentials", test.s3.credentials)
