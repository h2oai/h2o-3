#'
#'
#' ----------------- R Demo utils -----------------
#'
#'
removeH2OInit<-
function(testName) {
    hackedDemo <- paste(results.dir(), paste0(testName,".code"), sep=.Platform$file.sep)
    lines <- readLines(testName, warn=FALSE)
    remove_lines <- which(sapply(lines, function(l) grepl("^h2o.init",l)))
    if (length(remove_lines) > 0) lines <- lines[-remove_lines]
    writeLines(lines, hackedDemo)
    if (!file.exists(hackedDemo)) stop(paste0("Could not create file with h2o.init calls removed. Stopping."))
    return(hackedDemo)
}