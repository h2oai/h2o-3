#'
#'
#' ----------------- R Demo utils -----------------
#'
#'
library(jsonlite)

removeH2OInit<-
function(testName) {
    hackedDemo <- paste(RESULTS.DIR, paste0(testName,".code"), sep=.Platform$file.sep)
    lines <- readLines(testName, warn=FALSE)
    remove_lines <- which(sapply(lines, function(l) grepl("^h2o.init",l)))
    if (length(remove_lines) > 0) lines <- lines[-remove_lines]
    writeLines(lines, hackedDemo)
    if (!file.exists(hackedDemo)) stop(paste0("Could not create file with h2o.init calls removed. Stopping."))
    return(hackedDemo)
}

ipyNotebookExec <-
function(path) {
    nb <- fromJSON(path)
    p <- ''
    for(cell in nb$cells$source[nb$cells$cell_type == "code"]) {
        for (line in cell) {
            if (!grepl("h2o.init", line)) {
                p <- paste0(p,line)
            }
        }
        p <- paste(p,'',sep='\n')
    }
    eval(parse(text=p))
}