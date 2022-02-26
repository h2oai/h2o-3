setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.session.properties <- function() {
  conn <- h2o.getConnection()

  expect_null(.get.session.property(conn@mutable$session_id, "test_key_0"))
  
  .set.session.property(conn@mutable$session_id, "test_key_1", "test_value_1")
  expect_equal(.get.session.property(conn@mutable$session_id, "test_key_1"), "test_value_1")

  .set.session.property(conn@mutable$session_id, "test_key_2", NULL)
  expect_null(.get.session.property(conn@mutable$session_id, "test_key_2"))


  .set.session.property(conn@mutable$session_id, "test_key_3", "test_value_3")
  expect_equal(.get.session.property(conn@mutable$session_id, "test_key_3"), "test_value_3")

  h2o.removeAll()
  expect_null(.get.session.property(conn@mutable$session_id, "test_key_3"))
}

doTest("Test Job polling works better with internal wait on backend", test.session.properties)

