
#Read in small datasets with uncommon delimiters to see how it is parsed:

test.separator <- function(){
  #Define file paths to test files of particular delimiters
  path.tab = "smalldata/parser/tabs.tsv"
  path.pipe = "smalldata/parser/pipes.psv"
  path.hive = "smalldata/parser/test.hive"
  path.semi = "smalldata/parser/semi.scsv"
  path.caret = "smalldata/parser/caret.csv"
  path.asterisk = "smalldata/parser/asterisk.asv"
  
  #Tab delimited with argument
  tab.test = h2o.importFile(path = normalizePath(locate(path.tab)), destination_frame = "tab.hex",sep = "\t")
  expect_that(dim(tab.test), equals(c(3,3)))
  #Tab delimited with no argument
  tab.test.noarg = h2o.importFile(path = normalizePath(locate(path.tab)), destination_frame = "tab.hex",sep = "")
  expect_that(dim(tab.test.noarg), equals(c(3,3)))
  #--------------------------------------------------------------------------------------------------------------------
  #Pipe delimited with argument
  pipe.test = h2o.importFile(path = normalizePath(locate(path.pipe)), destination_frame = "pipe.hex",sep = "|")
  expect_that(dim(pipe.test), equals(c(3,3)))
  #Pipe delimited with no argument
  pipe.test.noarg = h2o.importFile(path = normalizePath(locate(path.pipe)), destination_frame = "pipe.hex",sep = "")
  expect_that(dim(pipe.test.noarg), equals(c(3,3)))
  #--------------------------------------------------------------------------------------------------------------------
  #Hive delimited with argument
  hive.test = h2o.importFile(path = normalizePath(locate(path.hive)), destination_frame = "hive.hex",sep = "\001")
  expect_that(dim(hive.test), equals(c(3,3)))
  #Hive delimited wih no argument
  hive.test.noarg = h2o.importFile(path = normalizePath(locate(path.hive)), destination_frame = "hive.hex",sep = "")
  expect_that(dim(hive.test.noarg), equals(c(3,3)))
  #--------------------------------------------------------------------------------------------------------------------
  #Semi colon delimited with argument
  semi.test = h2o.importFile(path = normalizePath(locate(path.semi)), destination_frame = "semi.hex",sep = ";")
  expect_that(dim(semi.test), equals(c(3,3)))
  #Semi colon delimited with no argument
  semi.test.noarg = h2o.importFile(path = normalizePath(locate(path.semi)), destination_frame = "semi.hex",sep = "")
  expect_that(dim(semi.test.noarg), equals(c(3,3)))
  #--------------------------------------------------------------------------------------------------------------------
  #Caret delimited with argument
  caret.test = h2o.importFile(path = normalizePath(locate(path.caret)), destination_frame = "caret.hex",sep = "^")
  expect_that(dim(caret.test), equals(c(3,3)))
  
  #Below gives error and does not parse correctly:
  #This is because the separator is not given, which sends the input to a set of auto detectors, which do not contain the caret symbol as a possible parser.
  #Caret delimited with no argument
  #caret.test.noarg = h2o.importFile(path = normalizePath(locate(path.caret)), destination_frame = "caret.hex",sep = "")
  #expect_that(dim(caret.test.noarg), equals(c(3,3)))
  #print(caret.test.noarg)
  #--------------------------------------------------------------------------------------------------------------------
  #Asterisk delimited with argument
  asterisk.test = h2o.importFile(path = normalizePath(locate(path.asterisk)), destination_frame = "asterisk.hex",sep = "*")
  expect_that(dim(asterisk.test), equals(c(3,3)))
  
  #Below gives error and does not parse correctly:
  #This is because the separator is not given, which sends the input to a set of auto detectors, which do not contain the asterisk symbol as a possible parser.
  #Asterisk delimited with no argument
  #asterisk.test.noarg = h2o.importFile(path = normalizePath(locate(path.asterisk)), destination_frame = "asterisk.hex",sep = "")
  #expect_that(dim(asterisk.test.noarg), equals(c(3,3)))
  #print(asterisk.test.noarg)
}

doTest("Separator Test", test.separator)
