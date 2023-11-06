

# Helper functions for h2o.explain commands in .onLoad
# Inspired by vctrs' s3_register function for registering s3 methods for generics from suggested packages
.s3_register <- function(package, generic, class) {
  method_env <- if (isNamespace(topenv())) asNamespace(environmentName(topenv())) else parent.frame()
  method <- get(paste(generic, class, sep = "."), envir = method_env)
  
  # Register hook in case package is unloaded & reloaded
  setHook(packageEvent(package, "onLoad"),
          function(...) {
            registerS3method(generic, class, method, envir = asNamespace(package))
          }
  )
  
  # Don't register if the package is not present
  if (!isNamespaceLoaded(package)) {
    return(invisible())
  }
  
  # Register iff generic exists in the package environment
  if (exists(generic, asNamespace(package))) {
    registerS3method(generic, class, method, envir = asNamespace(package))
  }
  
  invisible()
}


# This was moved from connection.R; .onAttach, .onDetach, .Last are still in connection.R
.onLoad <- function(lib, pkg) {
  .h2o.pkg.path <<- file.path(lib, pkg)
  
  # installing RCurl requires curl and curl-config, which is typically separately installed
  rcurl_package_is_installed = length(find.package("RCurl", quiet = TRUE)) > 0L
  if (!rcurl_package_is_installed) {
    if (.Platform$OS.type == "unix") {
      # packageStartupMessage("Checking libcurl version...")
      if (!nzchar(libcurlVersion()))
        stop("libcurl not found. Please install libcurl\n",
             "(version 7.14.0 or higher) from http://curl.haxx.se.\n",
             "On Linux systems you will often have to explicitly install\n",
             "libcurl-devel to have the header files and the libcurl library.")
    }
  }
  
  # for h2o.explain
  registerS3method("print", "H2OExplanation", "print.H2OExplanation")
  .s3_register("repr", "repr_text", "H2OExplanation")
  .s3_register("repr", "repr_html", "H2OExplanation")
  
  # CoxPH
  .s3_register("survival", "survfit", "H2OCoxPHModel")
  
  # H2OFrame
  .s3_register("stats", "median", "H2OFrame")
  
  invisible()
}



.h2o.downloadJar()
