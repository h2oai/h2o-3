invisible(options(echo=FALSE))

# helpers, possibly removed if current head of this branch merged
# https://svn.r-project.org/R/branches/tools4pkgs/src/library/tools/R/packages.R
packages.dcf <- function(file = "DESCRIPTION", 
                         which = c("Depends","Imports","LinkingTo"), 
                         except.priority = "base") {
  if (!is.character(file) || !length(file) || !all(file.exists(file)))
    stop("file argument must be character of filepath(s) to existing DESCRIPTION file(s)")
  if (!is.character(except.priority) || !length(except.priority) || !all(except.priority %in% c("base","recommended")))
    stop("except.priority accept 'base', 'recommended' or both")
  which_all <- c("Depends", "Imports", "LinkingTo", "Suggests", "Enhances")
  if (identical(which, "all"))
    which <- which_all
  else if (identical(which, "most"))
    which <- c("Depends", "Imports", "LinkingTo", "Suggests")
  if (!is.character(which) || !length(which) || !all(which %in% which_all))
    stop("which argument accept only valid dependency relation: ", paste(which_all, collapse=", "))
  x <- unlist(lapply(file, function(f, which) {
    dcf <- tryCatch(read.dcf(f, fields = which),
                    error = identity)
    if (inherits(dcf, "error") || !length(dcf))
      warning(gettextf("error reading file '%s'", f),
              domain = NA, call. = FALSE)
    else dcf[!is.na(dcf)]
  }, which = which), use.names = FALSE)
  x <- unlist(lapply(x, tools:::.extract_dependency_package_names))
  except <- c("R", unlist(tools:::.get_standard_package_names()[except.priority], use.names = FALSE))
  setdiff(x, except)
}
repos.dcf <- function(file = "DESCRIPTION") {
  if (!is.character(file) || !length(file) || !all(file.exists(file)))
    stop("file argument must be character of filepath(s) to existing DESCRIPTION file(s)")
  x <- unlist(lapply(file, function(f) {
    dcf <- tryCatch(read.dcf(f, fields = "Additional_repositories"),
                    error = identity)
    if (inherits(dcf, "error") || !length(dcf))
      warning(gettextf("error reading file '%s'", f),
              domain = NA, call. = FALSE)
    else dcf[!is.na(dcf)]
  }), use.names = FALSE)
  x <- trimws(unlist(strsplit(trimws(x), ",", fixed = TRUE), use.names = FALSE))
  unique(x)
}

#' @title R version Check
#' @description Check that running R release is at least in a version provided to arguments.
#' @param major character, expected major version R release, default \code{"3"}.
#' @param minor character, expected minor version R release, default \code{"2.1"}.
r_check <- function(major="3", minor="2.1") {
  r_version <- as.package_version(R.version)
  req_version <- as.package_version(paste(major, minor, sep="."))
  if (r_version < req_version)
    stop(sprintf("Jenkins has R version %s but this system has %s", req_version, r_version))
  TRUE
}

#' @title Packages Check and Update
#' @description Confirm that packages are present in library, by default also must match to packages version available in \code{repos}. If needed it will install (upgrade and downgrade) packages which are not matching.
#' @param pkgs character vector of packages to check from \code{lib.loc} library vs. \code{repos}.
#' @param lib.loc library location.
#' @param check_only logical default TRUE.
#' @param strict_version_check default TRUE also check that version of packages match to the one in repository.
#' @param force_install character vector subset of \code{pkgs}, those packages should be installed every time, even if version match. Works only when \code{check_only} is FALSE.
#' @param repos character R repositories where \code{pkgs} can be found, by default \emph{h2o-3 cran-dev} package repository.
#' @param method character default \code{"curl"} passed to \link{install.packages}.
#' @param quiet logical default FALSE passed to \link{install.packages}.
#' @details When package update is to be done on clean R environment/library, or in case when we want to re-install every package, we can simply call \code{install.packages(pkgs, repos="http://s3.amazonaws.com/h2o-r/cran-dev")}.
#' @section Adding dependencies
#' When adding new R dependencies for \emph{h2o-3} project build just edit \code{h2o-3-DESCRIPTION.template} file and put them there, use \code{Additional_repositories} field for packages that are not present in our \code{repos}.
#' @section Force update
#' If some dependencies should be always re-installed, then provide them to \code{force_install} argument.
#' This will ensure that latest version was installed, even if it has version numbers are already installed in \code{lib.loc}.
#' @section Updating packages in \code{repos}
#' When there is a need to update packages in \emph{upstream} \code{repos} it should be done by making new CRAN snapshop of all packages and replacing whole repository content.
#' This is not technically required, but should be practiced to avoid issues between \emph{h2o-3} dependencies.
pkgs_check_update <- function(pkgs, lib.loc=file.path(Sys.getenv("R_LIBS_USER", .libPaths()[1L])),
                              check_only=TRUE, strict_version_check=TRUE, force_install=NULL,
                              repos="http://s3.amazonaws.com/h2o-r/cran-dev",
                              method="curl", quiet=FALSE) {
  if (!length(pkgs) || !is.character(pkgs))
    stop("Argument 'pkgs' must be provided, a character vector of packages to check or update.")
  if (length(force_install) && (!is.character(force_install) || !all(force_install %in% pkgs)))
    stop("Argument 'force_install' must be character vector, subset of 'pkgs' argument.")
  if (!dir.exists(lib.loc) && !dir.create(lib.loc, recursive=TRUE))
    stop(sprintf("Library location 'lib.loc' does not exists '%s' and directory could not be created.", lib.loc))
  
  # exclude base R packages
  pkgs <- setdiff(pkgs, c("R", rownames(installed.packages(priority="base"))))
  
  # proper output redirection so CI catch it
  cat <- function(..., file=stdout()) base::cat(..., file=file)
  
  if (check_only)
    cat(c("", sprintf("INFO: R package%s check only. Please run `./gradlew syncRPackages` if you want to update instead", if (strict_version_check) "/version" else "")), sep="\n")
  else
    cat(c("", sprintf("INFO: R package%s s3 sync procedure", if (strict_version_check) "/version" else "")), sep="\n")
  
  ap <- available.packages(contrib.url(repos))
  # missing available packages
  map <- setdiff(pkgs, ap[,"Package"])
  if (length(map))
    stop(sprintf("Packages requested to check or update are missing in upstream repo(s): %s.", paste(map, collapse=", ")))
  
  cat(c("","INFO: Jenkins (package,version) list:", paste0("(",paste(ap[,"Package"], ap[,"Version"], sep=", "),")")), sep="\n")
  
  # force update packages
  if (!check_only && length(force_install)) {
    cat(c("","INFO: Force installing packages:", paste(force_install, collapse=", "), ""), sep="\n")
    install.packages(force_install, lib=lib.loc, repos=repos, method=method, quiet=quiet)
  }
  
  ip <- installed.packages(lib.loc)
  # missing installed packages
  mip <- setdiff(pkgs, ip[,"Package"])
  if (length(mip))
    cat(c(
      "", "INFO: Missing the following Jenkins-approved R packages:", mip,
      if (check_only) c("", "INFO: Please run `./gradlew syncRPackages` to update")
    ), sep="\n")
  
  mipv <- character()
  if (strict_version_check) {
    missing_installed_packages_version <- function(pkgs, ap, ip) {
      ap <- ap[ap[,"Package"] %in% pkgs,]
      ip <- ip[ip[,"Package"] %in% pkgs,]
      apv <- paste(ap[,"Package"], ap[,"Version"], sep="_")
      ipv <- paste(ip[,"Package"], ip[,"Version"], sep="_")
      setdiff(apv, ipv)
    }
    mipv <- missing_installed_packages_version(setdiff(pkgs, mip), ap, ip)
    
    if (!length(mipv)) {
      cat(c("","INFO: Installed R packages match version to Jenkins-approved"), sep="\n")
    } else {
      cat(c(
        "", "INFO: Some R packages are installed in version which is not Jenkins-approved:",
        mipv, # missing pkgs already reported
        if (check_only) c("", "INFO: Please run `./gradlew syncRPackages` to update")
      ), sep="\n")
    }
  }
  
  if (check_only && length(c(mip, mipv)))
    stop("Check unsuccessful.")
  
  inst_pkgs <- unique(c(unlist(sapply(strsplit(mipv, "_", fixed=TRUE), `[[`, 1L)), mip))
  if (length(inst_pkgs)) {
    if (any(reinst_pkgs<-inst_pkgs %in% force_install))
      stop(sprintf("Attempt to install packages again, those should be already installed by 'force_install' argument: %s.", paste(inst_pkg[reinst_pkgs], collapse=", ")))
    
    cat(c("","INFO: Installing packages:", paste(inst_pkgs, collapse=", "), ""), sep="\n")
    install.packages(inst_pkgs, lib=lib.loc, repos=repos, method=method, quiet=quiet)
    
    cat(c("",sprintf("INFO: R package sync complete. Conducting follow-on R package%s checks...", if (strict_version_check) "/version" else "")), sep="\n")
    
    ip <- installed.packages(lib.loc)
    mip <- setdiff(pkgs, ip[,"Package"])
    if (strict_version_check)
      mipv <- missing_installed_packages_version(setdiff(pkgs, mip), ap, ip)
    if (length(mip))
      stop(sprintf("Missing installed packages were not installed for some reason: %s.",  paste(mip, collapse=", ")))
    if (length(mipv))
      stop(sprintf("Missing installed packages/version were not upgraded/downgraded for some reason: %s.",  paste(mipv, collapse=", ")))
  }
  
  cat(c("","INFO: R package sync successful"), sep="\n")
  
  TRUE
}

# check expected R version
valid_r <- r_check()

# check expected packages (/version)
check_only <- if (length(args <- commandArgs(trailingOnly=TRUE))) !args[[1]]=="update" else TRUE

# finding h2o-3-DESCRIPTION.template
seek.files <- function(files, n = 7L) {
  ans.file <- NULL
  for (i in 0:n) {
    path <- file.path(do.call("file.path", as.list(c(".",rep("..", i)))), files)
    if (any(fe <- file.exists(path))) {
      ans.file <- path[fe][1L] # take first if few present
      break
    }
    if (identical(normalizePath(file.path(getwd(), dirname(path))), "/"))
      break # root dir
  }
  if (is.null(ans.file) || !file.exists(ans.file))
    stop(sprintf("Cannot find any of requested files, tried %s, also in parent directories up to %s levels, current wd '%s'.", paste(paste("'",files,"'",sep=""), sep=", "), i, getwd()))
  ans.file
}
dcf.file <- seek.files(c("h2o-3-DESCRIPTION.template","h2o-3-DESCRIPTION"))

repos <- c(repos.dcf(dcf.file), "http://s3.amazonaws.com/h2o-r/cran-dev")
pkgs <- packages.dcf(dcf.file, which = "all")

# try on windows/macosx
ans <- pkgs_check_update(pkgs, check_only=check_only, repos=repos) #, force_install="data.table") # allows to be fully up to date

if (!interactive()) {
  # expect TRUE
  status <- if (isTRUE(ans)) 0 else 1
  q("no", status=status)
}
