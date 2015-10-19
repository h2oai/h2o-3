#'
#'
#' ----------------- R Booklet utils -----------------
#'
#'
checkCodeExamplesInDir <-
function(codeExamples, directory) {
    actual <- c()
    for (f in dir(directory)) {
        if (grepl("*.R$",f)) actual <- c(actual, f)
    }
    if (length(codeExamples) > length(actual)) FALSE
    for(e in codeExamples) {
        if (!tail(strsplit(e,"/"))[[1]][2] %in% actual) FALSE
    }
    TRUE
}

checkStory <-
function(storyName, paragraphs) {
    h2o.removeAll()

    h2o.logAndEcho("------------------------------------------------------------")
    h2o.logAndEcho("")
    h2o.logAndEcho(paste0("CHECKING: ",storyName))
    h2o.logAndEcho("")
    h2o.logAndEcho("------------------------------------------------------------")

    # 1. Combine the related, individual code paragraphs into a single, coherent R story
    story <- c()
    for (p in paragraphs) story <- c(story, readLines(p, warn=FALSE))

    # 2. Execute the story

    # first, remove any h2o.init calls
    remove_lines <- which(sapply(story, function(l) grepl("^h2o.init",l)))
    if (length(remove_lines) > 0) story <- story[-remove_lines]

    # write the story that will executed to the results directory for future reference
    story_file <- paste(results.dir(), paste0(test.name(),".",storyName,".code"), sep=.Platform$file.sep)
    writeLines(story, story_file)

    source(story_file)
}

doBooklet <-
function(bookletDesc, booklet) {
    h2o.removeAll()
    conn <- h2o.getConnection()
    conn@mutable$session_id <- h2o:::.init.session_id()
    booklet()
}