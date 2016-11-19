#Allowed config keys
#allowed_config_keys = c("init.check_version", "init.proxy","init.cluster_id",
#"init.verify_ssl_certificates","init.cookies")

# R Parser for an h2o config file
#
# Input is the path to an .h2oconfig file
# Returns the .h2oconfig file as a data frame with respective key-value pairs as headers
.parse.h2oconfig <- function(h2oconfig_filename){
    connection <- file(h2oconfig_filename)
    Lines  <- readLines(connection)
    close(connection)

    Lines <- chartr("[]", "==", Lines)  # change section headers

    connection <- textConnection(Lines)
    d <- read.table(connection, as.is = TRUE, sep = "=", fill = TRUE)
    close(connection)

    L <- d$V1 == ""                    # location of section breaks
    d <- subset(transform(d, V3 = V2[which(L)[cumsum(L)]])[1:3], V1 != "")

    ToParse  <- paste("ini_list$", d$V3, "$",  d$V1, " <- '",
    d$V2, "'", sep="")

    ini_list <- list()
    eval(parse(text=ToParse))
    col_name_sections = names(ini_list)

    ini_to_df = data.frame(t(sapply(ini_list, `[`)))

    return(ini_to_df)
}


#Helper function responsible for reading h2o config files.

#This function will look for file(s) named ".h2oconfig" in the current folder, in all parent folders, and finally in
#the user's home directory. The first such file found will be used for configuration purposes. The format for such
#file is a simple "key = value" store, with possible section names in square brackets. Single-line comments starting
#with '#' are also allowed.
.h2o.candidate.config.files <- function(){
    current_directory = getwd()
    while(identical(Sys.glob(".h2oconfig"),character(0))){
        if(getwd() == "/"){
            return(NULL)
        }
        setwd("..")
        current_directory = getwd()
    }
    return(paste0(current_directory,"/.h2oconfig"))
}
