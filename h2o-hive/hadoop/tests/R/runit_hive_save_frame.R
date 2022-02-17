setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../../h2o-r/scripts/h2o-r-test-setup.R")

test.hive.save.frame <- function() {
    connection_url <- "jdbc:hive2://localhost:10000/default"
    krb_enabled <- Sys.getenv('KRB_ENABLED') == 'true'
    use_token <- Sys.getenv('KRB_USE_TOKEN') == 'true'
    if (krb_enabled) {
      if (use_token) {
        connection_url <- paste0(connection_url, ";auth=delegationToken")
        
      } else {
        connection_url <- paste0(connection_url, ";principal=", Sys.getenv('HIVE_PRINCIPAL'))
      }
    }

    username <- "hive"
    password <- ""

    # read original
    dataset_original <- h2o.importFile(locate("smalldata/prostate/prostate_cat_NA.csv"), "prostate", header=TRUE)

    # save to Hive
    h2o.save_to_hive(
        dataset_original, connection_url, 
        table_name = "prostate_hex_r",
        table_path = "/user/hive/ext/prostate_hex_r",
        format = "csv",
        tmp_path = "/tmp"
    )
    
    # read from Hive
    dataset_hive <- h2o.import_sql_table(connection_url, "prostate_hex_r", username, password, fetch_mode="SINGLE")
    
    # just a smoke test, full comparison done in python test
    expect_equal(dim(dataset_original), dim(dataset_hive))
}

doTest("Save Frame to Hive", test.hive.save.frame)
