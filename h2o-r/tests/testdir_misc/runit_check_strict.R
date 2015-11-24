check_strict <- function() {

  expect_true(formals(h2o.init)$strict_version_check)
}
doTest("Check that strict version checking is on.", check_strict)
