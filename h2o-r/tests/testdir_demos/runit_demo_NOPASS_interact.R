#----------------------------------------------------------------------
# Purpose:  Create the x-prod interaction terms between two categorical vectors
#----------------------------------------------------------------------

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')
options(echo=TRUE)


remove_tmps <- function(h) h2o.rm(h, grep(pattern = "Last.value", x = h2o.ls(h)$Key, value = TRUE))

# Interaction Method Code
interact.helper <- function(r_level, l_level, l_vec, r_vec) {
   v1 <- l_vec == l_level;
   v2 <- r_vec == r_level;
   vec <- v1 & v2
   key <- vec@key
   ret <- h2o.assign(vec, paste('l', l_level, '_', 'r', r_level, sep = ""))
   h2o.rm(h, v1@key); h2o.rm(h, v2@key); h2o.rm(h, key)
   ret
}

inner <- function(l_level, r_levels, l_vec, r_vec) { lapply(r_levels, interact.helper, l_level, l_vec, r_vec) }

remove_tmp <- function(tmp, h2o) {
  if( any(grepl(tmp@key, as.data.frame(h2o.ls(h2o))))) h2o.rm(h2o, tmp@key)
}
remove_tmp2 <- function(tmp, h2o)  if( any(grepl(tmp, as.data.frame(h2o.ls(h2o))))) h2o.rm(h2o, tmp)
get.name <- function(r_level, l_level) paste('l', l_level, '_', 'r', r_level, sep = "")
inner.names <- function(l_level, r_levels) lapply(r_levels, get.name, l_level)

interact <- function(fr, l_vec, r_vec) {
   terms <- unlist(lapply(h2o.levels(fr, l_vec), inner, h2o.levels(fr, r_vec), fr[l_vec], fr[r_vec]))
   terms <- Reduce(cbind, terms, accumulate=T)
   print(terms)
   res <- as.h2o(terms[[length(terms)]], key ="interactions")
   colnames(res) <- unlist(inner.names(h2o.levels(fr, l_vec), h2o.levels(fr, r_vec)))
   lapply(colnames(res), remove_tmp2, h)
   remove_tmps(h)
   res
}

# End Interaction Method Code


# Begin Demo #

h <- h2o.init(ip=myIP, port=myPort)
#uploading data file to h2o
filePath <- locate("smalldata/logreg/prostate.csv")
hex <- h2o.uploadFile(h, filePath, "prostate")

hex$RACE <- as.factor(hex$RACE)
hex$GLEASON <- as.factor(hex$GLEASON)

race <- which(colnames(hex) == "RACE")
glea <- which(colnames(hex) == "GLEASON")

print(h2o.levels(hex, race))
print(h2o.levels(hex, glea))
interaction.matrix <- interact(hex, race, glea)

print(interaction.matrix)

augmented_data_set <- h2o.assign(h2o.cbind(hex, interaction.matrix), "augmented")
remove_tmps(h)

print(augmented_data_set)
PASS_BANNER()
