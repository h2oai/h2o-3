setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

# Invokes the SQL endpoint with password credentials
# The queries are intentionally invalid - the purpose is to demonstrate we don't log credentials even if the request fails
# Test framework is responsible for detecting possible leaked password (should scan for occurences strings like "password")
# The test itself doesnt't check that

test.import.sql.no.logging <- function() {
    connection_string <- "jdbc:invalid://localhost:3306/my_invalid_db"
    username <- "param_username"
    password <- "param_password"

    expect_error_no_driver <- function(object) {
        expect_error(object, regexp = '.*No suitable driver found.*')
    }

    expect_error_no_driver(
        h2o.import_sql_table(connection_string, table="test_table", username=username, password=password)
    )

    expect_error_no_driver(
        h2o.import_sql_select(connection_string, "SELECT test_column FROM test_table", username=username, password=password)
    )
}

doTest("Run SQL queries with credentials", test.import.sql.no.logging)
