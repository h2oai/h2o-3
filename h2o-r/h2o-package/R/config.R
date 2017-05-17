
#-------------------------------------------------------------------------------------------------------------------
# Helper Functions responsible for reading h2o config files.
#-------------------------------------------------------------------------------------------------------------------

# R Parser for an h2o config file
#
# Input is the path to an .h2oconfig file along with a `print_path` flag, which signifies if a message regarding the path of
# the read config file should be printed to the console.
# Returns the .h2oconfig file as a data frame with a respective key-value store where the `key` is a column name.
#' @importFrom utils read.table
.parse.h2oconfig <- function(h2oconfig_filename, print_path = FALSE){

    #Only show this output when connecting to H2O
    if(print_path == TRUE){
      cat(paste0("Reading in config file: ",h2oconfig_filename,"\n"))
    }

    #Need to allocate some value for V1 & V2, which are used down the function for parsing.
    #This is to help with the CRAN NOTE check.
    #These variables are first used in subset() and transform().
    V1 <- V2 <- NULL

    #Allowed config keys
    allowed_config_keys = c("init.check_version", "init.proxy","init.cluster_id",
    "init.verify_ssl_certificates","init.cookies","general.allow_breaking_changes",
    "init.username","init.password")

    #Read in config line by line
    connection <- file(h2oconfig_filename)
    Lines  <- readLines(connection)
    Lines <- trimws(Lines)

    #Make all section headers lowercase to avoid case sensitive exceptions
    for(i in 1:length(Lines)){
      if(grepl("\\[|\\]",Lines[i])){
        Lines[i] = tolower(Lines[i])
      }
    }

    #Check for correct section headers. In this case it is [init] & [general] (case insensitive).
    #Can update vector of acceptable headers in time.
    if(grepl("\\[|\\]",Lines) && all(!(c("[init]","[general]") %in% Lines))){
      return()
    }
    close(connection)

    #Some sanity checks
    Lines <- chartr("[]", "==", Lines)  # change section headers
    Lines <- subset(Lines,!grepl("^#",Lines)) #Exclude hashtag comments
    Lines <- subset(Lines,!grepl("^py:",ignore.case=TRUE,Lines)) #Exclude any Python specific parameters if present (Not case sensitive)
    Lines <- gsub(".*^r:","",ignore.case=TRUE,Lines) #Get R specific parameters if present (Not case sensitive)
    connection <- textConnection(Lines)

    #If all empty sections after initial parse return NULL
    if(length(Lines) == 0 || all(Lines == "")){
      return()
    }

    #Get connection to previous parse and make initial data frame
    d <- read.table(connection, as.is = TRUE, sep = "=", fill = TRUE)

    #Trim whitespace from potential columns
    d$V1 = trimws(d$V1)

    #Only get last occurence of duplicates
    if(any(duplicated(d$V1[d$V1 != ""]))){
      d = d[!rev(duplicated(rev(d$V1))),]
    }
    close(connection)

    #If no headers are present & no leading #(indicate comments) & no empty strings return NULL
    if(grepl("^=",Lines) && !grepl("^#",Lines) && any(Lines=="")){
      return()
    }

    #If no section headers, then we parse the list itself and return the final parsed data frame
    if(!(grepl("^=",Lines)) && !grepl("^#",Lines)){
      ini_to_df <- data.frame(t(d$V2))
      colnames(ini_to_df) <- d$V1
      colnames(ini_to_df) <- trimws(colnames(ini_to_df))

      #Check if allowed keys are present. If none are present, return NULL
      names <- colnames(ini_to_df)[which(colnames(ini_to_df) %in% allowed_config_keys)]
      if(length(names) == 0){
       return()
      }
      ini_to_df = ini_to_df[,names]
      ini_to_df = data.frame(ini_to_df)
      colnames(ini_to_df) <- names
      ini_to_df <- data.frame(lapply(ini_to_df, trimws))
      return(ini_to_df)
    }

    L <- d$V1 == ""                    # location of section breaks
    d <- subset(transform(d, V3 = V2[which(L)[cumsum(L)]])[1:3], V1 != "")

    ToParse  <- paste("ini_list$",  d$V1, " <- '",
    d$V2, "'", sep="")

    ini_list <- list()
    eval(parse(text=ToParse))

    ini_to_df <- as.data.frame.list(ini_list)
    colnames(ini_to_df) <- trimws(colnames(ini_to_df))
    colnames(ini_to_df) <- paste0(d$V3,".",colnames(ini_to_df))
    names <- colnames(ini_to_df)[which(colnames(ini_to_df) %in% allowed_config_keys)]
    if(length(names) == 0){
        return()
    }
    ini_to_df = subset(ini_to_df,select = names)
    colnames(ini_to_df) <- names
    ini_to_df <- data.frame(lapply(ini_to_df, trimws))
    return(ini_to_df)
}


# Read h2o config files.

# This function will look for file(s) named ".h2oconfig" in the current folder, in all parent folders (up to root), and finally in
# the user's home directory. The first such file found will be used for configuration purposes. The format for such
# file is a simple "key = value" store, with possible section names in square brackets. Single-line comments starting
# with '#' are also allowed.
# Input is a list of file names or a single file name.
# Returns path to first file found in files. Otherwise it returns NULL
.find.config <- function(files = ".h2oconfig") {
  windows <- .Platform$OS.type == "windows" #Are we dealing with a Windows OS?

  #Function to check if in root directory
  is_root <- function() {
    if (windows)
     identical(normalizePath(file.path(getwd(), dirname(path))), paste(win.drive,":\\",sep = ""))
    else
     identical(normalizePath(file.path(getwd(), dirname(path))), "/")
  }

  if(windows){
    win.drive <- strsplit(normalizePath(getwd()), ":", fixed=TRUE)[[1]][1] #Get drive if Windows OS
  }
  ans.file <- NULL
  i <- 0

  while(is.null(ans.file)) {
    path <- file.path(do.call("file.path", as.list(c(".",rep("..", i)))), files)
    i <- i + 1
    if (any(fe <- file.exists(path))) {
     ans.file <- path[fe][1L] # take first if few present
     break
    }
   if (is_root()){
     #Final check in home diretory
     if (any(fe <- file.exists(fp <- path.expand(file.path("~",files)))))  {
      ans.file <- fp[fe][1L] #`fp` and `fe` are vectors. If multiple files are the input, then we first subset from all files
                             # that exist in `fp`, which are `[fe]` and then only use first, which is shown by `[1L]`
     }
     break # root directory and files are not found
   }
 }

 return(ans.file)
}

# Return config value corresponding to the provided `key`
.get.config.value <- function(key,default = NULL){
    config_path <- .find.config()
    if(is.null(config_path)){
      return(default)
    }else{
      h2oconfig = .parse.h2oconfig(config_path)
      if(key %in% colnames(h2oconfig)){
        return(h2oconfig[,key])
      }else{
        return(default)
      }
    }
}
