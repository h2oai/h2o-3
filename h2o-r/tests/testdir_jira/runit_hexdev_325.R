


test.parse.mismatching.col.length <- function(){

  df <- h2o.importFile(locate("smalldata/jira/hexdev_325.csv"), header = TRUE)
  expected <- c("C3", "Cats", "C3C3", "C4", "Mouse", "C6")
  actual <- colnames(df)

  expect_equal(expected, actual)

  
}

doTest("Testing Parsing of Mismatching Header and Data length", test.parse.mismatching.col.length)
