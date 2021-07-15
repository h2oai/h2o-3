setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.string.manipulation <- function() {
  s1 <- as.character(as.h2o(" this is a string "))
  s2 <- as.character(as.h2o("this is another string"))
  s3 <- as.character(as.h2o("this is a longer string"))
  s4 <- as.character(as.h2o("this is tall, this is taller"))

  Log.info("Single and all substitutions...")
  s4 <- h2o.sub("this", "that", s4)
  print(s4)
  expect_equal(s4[1,1], "that is tall, this is taller", check.attributes = FALSE)
  s4 <- h2o.gsub("tall", "fast", s4)
  print(s4)
  expect_equal(s4[1,1], "that is fast, this is faster", check.attributes = FALSE)

  Log.info("Trimming...")
  print(s1[1,1])
  expect_equal(s1[1,1], " this is a string ", check.attributes = FALSE)
  s1 <- h2o.trim(s1)
  expect_equal(as.character(s1[1,1]), "this is a string", check.attributes = FALSE)

  Log.info("String splitting")
  ds <- h2o.rbind(s1, s2, s3)
  print(ds)
  splits <- h2o.strsplit(ds, " ")
  print(splits)
  splits.expected <- data.frame(
    C1 = "this",
    C2 = "is",
    C3 = c("a", "another", "a"),
    C4 = c("string", "string", "longer"),
    C5 = c(NA, NA, "string"), stringsAsFactors = FALSE
  )
  expect_equal(as.data.frame(splits), splits.expected, check.attributes = FALSE)

  tokenized <- h2o.tokenize(ds, " ")
  tokenized.expected <- data.frame(C1 = c("this", "is", "a", "string", NA,
                                          "this", "is", "another", "string", NA,
                                          "this", "is", "a", "longer", "string", NA), stringsAsFactors = FALSE)
  expect_equal(as.data.frame(tokenized), tokenized.expected, check.attributes = FALSE)
  expect_equal(as.data.frame(tokenized), tokenized.expected, check.attributes = FALSE)
}

doTest("Testing Various String Manipulations", test.string.manipulation)
