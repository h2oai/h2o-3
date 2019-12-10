setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../h2o-r/scripts/h2o-r-test-setup.R")

adapt_frame <- function(dataset, original, table_name="table_for_h2o_import") {
    dataset[paste0(table_name, ".community_area_name")] <- as.factor(dataset[paste0(table_name, ".community_area_name")])
    names(dataset) <- names(original)
    dataset
}

test.hive.jdbc.import <- function() {
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

    select_query <- "select * from chicago"
    username <- "hive"
    password <- ""

    # read original
    dataset_original <- h2o.importFile(locate("smalldata/chicago/chicagoCensus.csv"), "chicago", header=TRUE)
    names(dataset_original) <- c("community_area_num", "community_area_name", "pct_owned", "pct_below", "pct_16plus", "pct_25plus",
                                                   "pct_yng_old", "per_cpt_income", "hardship_index")
    dataset_original["community_area_name"] <- as.factor(dataset_original["community_area_name"])

    # read from Hive Streaming
    dataset_streaming <- h2o.import_sql_select(connection_url, select_query, username, password, fetch_mode="SINGLE")
    dataset_streaming <- adapt_frame(dataset_streaming, dataset_original)
    expect_equal(dim(dataset_original), dim(dataset_streaming))
    expect_equal(summary(dataset_original, exact_quantiles=TRUE), summary(dataset_streaming, exact_quantiles=TRUE))

    # read from Hive without temp table
    dataset_no_temp_table <- h2o.import_sql_select(
      connection_url, select_query, username, password, use_temp_table = FALSE, fetch_mode="SINGLE"
    )
    dataset_no_temp_table <- adapt_frame(dataset_no_temp_table, dataset_original, "sub_h2o_import")
    expect_equal(dim(dataset_original), dim(dataset_no_temp_table))
    expect_equal(summary(dataset_original, exact_quantiles=TRUE), summary(dataset_no_temp_table, exact_quantiles=TRUE))
    
    # read from Hive with custom temp table
    dataset_custom_temp_table <- h2o.import_sql_select(
      connection_url, select_query, username, password, 
      use_temp_table = TRUE, temp_table_name = "user_database.test_import_table", fetch_mode="SINGLE"
    )
    dataset_custom_temp_table <- adapt_frame(dataset_custom_temp_table, dataset_original, "test_import_table")
    expect_equal(dim(dataset_original), dim(dataset_custom_temp_table))
    expect_equal(summary(dataset_original, exact_quantiles=TRUE), summary(dataset_custom_temp_table, exact_quantiles=TRUE))
}

doTest("Import Hive JDBC Import", test.hive.jdbc.import)
