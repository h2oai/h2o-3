
# R Parser for an h2o config file
#
# Input is the path to an .h2oconfig file
# Returns the .h2oconfig file as a data frame with respective key-value pairs as headers
.parse.h2oconfig <- function(h2oconfig_filename){
    cat(paste0("Reading in config file: ",h2oconfig_filename,"\n"))
    #Allowed config keys
    allowed_config_keys = c("init.check_version", "init.proxy","init.cluster_id",
    "init.verify_ssl_certificates","init.cookies")
    connection <- file(h2oconfig_filename)
    Lines  <- readLines(connection)
    close(connection)

    Lines <- chartr("[]", "==", Lines)  # change section headers
    Lines <- subset(Lines,!grepl("^#",Lines)) #Exclude hashtag comments
    Lines <- subset(Lines,!grepl("^py:",ignore.case=TRUE,Lines)) #Exclude any Python specific parameters if present (Not case sensitive)
    Lines <- gsub(".*^r:","",ignore.case=TRUE,Lines) #Get R specific parameters if present (Not case sensitive)
    connection <- textConnection(Lines)
    d <- read.table(connection, as.is = TRUE, sep = "=", fill = TRUE)
    close(connection)

    #If no section headers, then we parse the list itself and no reason to go any further
    if(!all(grepl("\\[|\\]",Lines)) && !grepl("^#",Lines)){
        ini_to_df <- data.frame(t(d$V2))
        colnames(ini_to_df) <- d$V1
        colnames(ini_to_df) <- trimws(colnames(ini_to_df))
        names <- colnames(ini_to_df)[which(colnames(ini_to_df) %in% allowed_config_keys)]
        if(length(names) == 0){
            return()
        }
        ini_to_df = ini_to_df[,names]
        colnames(ini_to_df) <- names
        return(ini_to_df)
    }

    L <- d$V1 == ""                    # location of section breaks
    d <- subset(transform(d, V3 = V2[which(L)[cumsum(L)]])[1:3], V1 != "")

    ToParse  <- paste("ini_list$", d$V3, "$",  d$V1, " <- '",
    d$V2, "'", sep="")

    ini_list <- list()
    eval(parse(text=ToParse))
    col_name_sections <- names(ini_list)

    ini_to_df <- data.frame(sapply(ini_list, `[`))
    colnames(ini_to_df) <- trimws(colnames(ini_to_df))
    names <- colnames(ini_to_df)[which(colnames(ini_to_df) %in% allowed_config_keys)]
    if(length(names) == 0){
        return()
    }
    ini_to_df = ini_to_df[,names]
    colnames(ini_to_df) <- names
    return(ini_to_df)
}


#Helper function responsible for reading h2o config files.

#This function will look for file(s) named ".h2oconfig" in the current folder, in all parent folders, and finally in
#the user's home directory. The first such file found will be used for configuration purposes. The format for such
#file is a simple "key = value" store, with possible section names in square brackets. Single-line comments starting
#with '#' are also allowed.
.h2o.candidate.config.files <- function(){
    path_to_config = getwd()
    current_directory = getwd()
    while(identical(Sys.glob(".h2oconfig"),character(0))){
        if(getwd() == "/"){
            setwd(current_directory)
        return()
        }
        setwd("..")
        path_to_config = getwd()
    }
    setwd(current_directory)
    return(paste0(path_to_config,"/.h2oconfig"))
}
