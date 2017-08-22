#!/usr/bin/env Rscript

# be aware that CRAN rules and messages can change with R upgrade

# NOTE whitelist to be used by grepl, for each check step
whitelist = list(
  "CRAN incoming feasibility" = c("Maintainer:",
                                  "Checking URLs requires 'libcurl' support in the R build",
                                  "Package has FOSS license, installs .class/.jar but has no 'java' directory",
                                  "Insufficient package version", # when we check older version of package than current available on CRAN
                                  "Days since last update", # when we submit to CRAN recently
                                  "Number of updates in past 6 months"), # when we submit to CRAN too often
  "package dependencies" = c("No repository set, so cyclic dependency check skipped",
                             "Package suggested but not available for checking"),
  "installed package size" = c("installed size is .*Mb", # h2o.jar is installed
                               "sub-directories of 1Mb or more",
                               "java .*Mb"),
  "Rd cross-references" = "Package unavailable to check Rd xrefs" # when linking documentation to optional deps not present at check time
)
check_note <- function(details, whitelist, verbose=TRUE) {
  stopifnot(is.data.frame(details), is.list(whitelist))
  status_details <- details[details$Status=="NOTE", , drop=FALSE]
  if (!nrow(status_details))
    stop("No NOTEs were found in provided details, be sure to escape this call when no NOTEs present")
  lines <- strsplit(status_details$Output, "\n", fixed=TRUE)
  names(lines) <- status_details$Check
  filter_check <- function(check, lines, whitelist) {
    check_lines <- lines[[check]]
    check_lines <- check_lines[nzchar(check_lines)]
    check_whitelist <- whitelist[[check]]
    # no whitelist for this check step, all notes are valid
    if (!length(check_whitelist))
      return(check_lines)
    # check if whitelisted
    wl <- sapply(
      check_lines,
      function(line) any(sapply(
        check_whitelist,
        function (pattern) grepl(pattern, line)
        ))
    )
    check_lines[!wl]
  }
  new_note <- sapply(names(lines), filter_check, lines, whitelist, simplify=FALSE)
  new_note_row <- which(sapply(new_note, length) > 0L)
  new_note <- new_note[new_note_row]
  new_note_flags <- unique(status_details[new_note_row, "Flags"])
  
  ans <- !length(new_note)
  if (verbose && !ans) {
    new_note_body <- unlist(lapply(names(new_note), function(check) {
      c(paste("* checking", check), new_note[[check]])
    }))
    message(sprintf("There %s %s unexpected NOTE%s in R CMD check%s\n\n%s",
                    if(length(new_note)>1L) "are" else "is",
                    length(new_note),
                    if(length(new_note)>1L) "s" else "",
                    paste(c("",new_note_flags), collapse=" "),
                    paste(new_note_body, collapse="\n")))
  }
  ans
}

pkg <- if (length(args <- commandArgs(trailingOnly=TRUE))) args[[1]]
rcheck_dir <- Sys.glob(if (is.null(pkg)) "*.Rcheck" else paste(pkg, ".Rcheck", sep=""))
if (length(rcheck_dir)!=1L) stop("This script should be started from directory in which *.Rcheck directory is located, and there must be single Rcheck directory.")

log_file <- file.path(rcheck_dir, "00check.log")
res <- tools:::check_packages_in_dir_results(logs = log_file)[[1L]]
if (length(res)) {
  if (res$status %in% c("FAIL","ERROR","WARN")) {
    status <- 1
    meta <- tools:::analyze_check_log(log_file)
    # print whole file
    message(sprintf("R CMD check%s completed with %s\n\n%s",
                    paste(c("",meta[["Flags"]]), collapse=" "),
                    res$status,
                    paste(readLines(log_file, warn=FALSE), collapse="\n")))
  } else if (res$status=="NOTE") {
    details <- tools:::check_packages_in_dir_details(logs = log_file)
    # print notes which are not whitelisted
    note_ok <- check_note(details, whitelist)
    status <- if (note_ok) 0 else 1
  } else if (res$status=="OK") {
    status <- 0
  } else stop("Unknown status from R CMD check: ", toString(res$status))
} else stop("Unknown results from R CMD check")

if (status==0) cat(sprintf("INFO: %s package R CMD check output successfully validated.\n", sub("\\.Rcheck$", "", rcheck_dir)))

if (!interactive()) q("no", status=status)
