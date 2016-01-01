setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
# Check for 2 parts
test1 <- function() {
  df <- h2o.uploadFile(h2oTest.locate("smalldata/jira/pubdev_2020.csv"))
  splits = h2o.splitFrame(data = df, ratios = 0.75)
  stopifnot(nrow(df) == (nrow(splits[[1]]) + nrow(splits[[2]])))
  
  part1 = splits[[1]]
  i = 1
  split_was_sequential = T
  nrow_part1 = nrow(part1)
  while(i <= nrow_part1) {
    value = part1[i,"C1"]
    print(value)
    if (value != i) {
      split_was_sequential = F
    }
    
    i = i + 1
  }
  
  if (split_was_sequential) {
    stop("splitframe was not random")
  }
  
  part2 = splits[[2]]
  
  if (nrow(part1) == 0) stop("Part 1 has no rows")
  if (nrow(part2) == 0) stop("Part 2 has no rows")  
}

# Check for 3 parts
test2 <- function() {
  df <- h2o.uploadFile(h2oTest.locate("smalldata/jira/pubdev_2020.csv"))
  splits = h2o.splitFrame(data = df, ratios = c(0.5, 0.25))
  stopifnot(nrow(df) == (nrow(splits[[1]]) + nrow(splits[[2]]) + nrow(splits[[3]])))
  if (nrow(splits[[1]]) == 0) stop("Part 1 has no rows")
  if (nrow(splits[[2]]) == 0) stop("Part 2 has no rows")
  if (nrow(splits[[3]]) == 0) stop("Part 2 has no rows")
}

# Check that runif can be seeded for reproducibility
test3 <- function() {
  df <- h2o.uploadFile(h2oTest.locate("smalldata/jira/pubdev_2020.csv"))
  splits = h2o.splitFrame(data = df, ratios = c(0.8), seed = 0)
  part2 = splits[[2]]
  value = part2[1,"C1"]
  stopifnot(value == 4)
  value = part2[2,"C2"]
  stopifnot(value == 11)
  value = part2[3,"C3"]
  stopifnot(value == 22)
  
  df <- h2o.uploadFile(h2oTest.locate("smalldata/jira/pubdev_2020.csv"))
  splits = h2o.splitFrame(data = df, ratios = c(0.8), seed = 0)
  part2 = splits[[2]]
  value = part2[1,"C1"]
  stopifnot(value == 4)
  
  df <- h2o.uploadFile(h2oTest.locate("smalldata/jira/pubdev_2020.csv"))
  splits = h2o.splitFrame(data = df, ratios = c(0.8), seed = 0)
  part2 = splits[[2]]
  value = part2[1,"C1"]
  stopifnot(value == 4)
}

test.pubdev_2020 <- function() {
  test1()
  test2()
  test3()
}

h2oTest.doTest("PUBDEV-2020", test.pubdev_2020)

