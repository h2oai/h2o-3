



test <- function() {
  json_file <- locate("smalldata/jira/hex-1833.json")

  print(jsonlite::fromJSON(json_file))
 
  
}

doTest("testing JSON parse", test)
