options(echo=FALSE)
#'
#' Check that the required packages and R are installed and it's the correct version
#'

r_check <- function(major="3", minor="2.1") {
  r_version <- as.package_version(R.version)
  req_version <- as.package_version(paste(major, minor, sep="."))
  if (r_version < req_version)
    stop(sprintf("Jenkins has R version %s but this system has %s", req_version, r_version))
  TRUE
}

#' @param pkgs character vector of packages to check from \code{lib.loc} library vs. \code{repos}.
#' @param lib.loc library location.
#' @param check_only logical default TRUE.
#' @param strict_version_check default TRUE also check that version of packages match to the one in repository.
#' @param force_install character vector subset of \code{pkgs}, those packages should be installed every time, even if version match.
#' @param repos character R repositories where \code{pkgs} can be found, by default \emph{h2o-3 cran-dev} package repository.
#' @param method character default \code{"curl"} passed to \link{install.packages}.
#' @param quiet logical default FALSE passed to \link{install.packages}.
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
  
  if (check_only)
    cat("\nINFO: R package/version check only. Please run `./gradlew syncRPackages` if you want to update instead\n")
  else
    cat("\nINFO: R package/version s3 sync procedure\n")
  
  ap <- available.packages(contrib.url(repos))
  # missing available packages
  map <- setdiff(pkgs, ap[,"Package"])
  if (length(map))
    stop(sprintf("Packages requested to check or update are missing in upstream repo(s): %s.", paste(map, collapse=", ")))
  cat("\nINFO: Jenkins' (package,version) list:", paste0("(",paste(ap[,"Package"], ap[,"Version"], sep=", "),")"), "", sep="\n")
  
  ip <- installed.packages(lib.loc)
  # missing installed packages
  mip <- setdiff(pkgs, ip[,"Package"])
  if (length(mip))
    cat(c(
      "", "INFO: Missing the following Jenkins-approved R packages:", mip,
      if (check_only) c("", "INFO: Please run `./gradlew syncRPackages` to update")
    ), sep="\n")
  
  # force update packages
  if (length(force_install)) {
    cat(c("INFO: Force installing packages:", paste(force_install, collapse=", "), ""), sep="\n")
    install.packages(force_install, lib=lib.loc, repos=repos, method=method, quiet=quiet)
  }
  
  # for strict_version_check=TRUE it will skip Version
  missing_installed_packages_version <- function(pkgs, ap, ip, strict_version_check) {
    ap <- ap[ap[,"Package"] %in% pkgs,]
    ip <- ip[ip[,"Package"] %in% pkgs,]
    apv <- if (strict_version_check) paste(ap[,"Package"], ap[,"Version"], sep="_") else ap[,"Package"]
    ipv <- if (strict_version_check) paste(ip[,"Package"], ip[,"Version"], sep="_") else ip[,"Package"]
    setdiff(apv, ipv)
  }
  mipv <- missing_installed_packages_version(pkgs, ap, ip, strict_version_check)
  if (!length(mipv)) {
    cat(sprintf("INFO: Check successful. All system R packages%s are Jenkins-approved\n", if (strict_version_check) "/versions" else ""))
  } else {
    cat(c(
      "", sprintf("INFO: This system has R packages that are not Jenkins-approved packages%s:", if (strict_version_check) "/versions" else ""),
      mipv,
      if (check_only) c("", "INFO: Please run `./gradlew syncRPackages` to update")
    ), sep="\n")
    
    if (check_only)
      stop("Check unsuccessful.")
    
    inst_pkgs <- if (strict_version_check) sapply(strsplit(mipv, "_", fixed=TRUE), `[[`, 1L) else mipv
    if (any(reinst_pkgs<-inst_pkgs %in% force_install))
      stop(sprintf("Attempt to install packages again, those should be already installed with 'force_install' argument: %s.", paste(inst_pkg[reinst_pkgs], collapse=", ")))
    cat(c("INFO: Installing packages:", paste(inst_pkgs, collapse=", "), ""), sep="\n")
    
    install.packages(inst_pkgs, lib=lib.loc, repos=repos, method=method, quiet=quiet)
    
    cat("INFO: R package sync complete. Conducting follow-on R package/version checks...\n")
    
    ip <- installed.packages(lib.loc)
    mipv <- missing_installed_packages_version(pkgs, ap, ip, strict_version_check)
    if (length(mipv))
      stop(sprintf("Missing installed packages%s were not installed for some reason: %s.",  if (strict_version_check) "/versions" else "", paste(mipv, collapse=", ")))
    
    cat("INFO: R package sync successful\n")
  }
  TRUE
}

# extract dependencies info from standard R DESCRIPTION file
# should be simpler in future, for documentation see: https://stat.ethz.ch/pipermail/r-devel/2016-June/072826.html
dcf.packages <- function(file="DESCRIPTION", fields=c("Depends","Imports","LinkingTo","Suggests"), except.priority="base") { dcf<-read.dcf(file, fields); setdiff(trimws(sapply(strsplit(trimws(unlist(strsplit(dcf[!is.na(dcf)], ",", fixed=TRUE))), "(", fixed=TRUE), `[[`, 1L)), c("R", rownames(installed.packages(priority=except.priority)))) }
# same for "Additional_repositories"
dcf.repos <- function(file="DESCRIPTION") c(na.omit(trimws(strsplit(trimws(read.dcf(file, "Additional_repositories")), ",")[[1L]])))

# check expected R version
r_check()

# check expected packages (/versions)
check_only <- if (length(args <- commandArgs(trailingOnly=TRUE))) !args[[1]]=="update" else TRUE
dcf.file <- "h2o-3-DESCRIPTION.template"
repos <- c(dcf.repos(dcf.file), "http://s3.amazonaws.com/h2o-r/cran-dev")
pkgs <- dcf.packages(dcf.file)
## try on windows/macosx
# options(install.packages.check.source="no")
pkgs_check_update(pkgs, check_only=check_only, repos=repos) # force_install="data.table" # allows to be fully up to date

q("no", status=0)
