#----------------------------------------------------------------------
# Purpose:  Create the x-prod interaction terms between two categorical vectors
#----------------------------------------------------------------------

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')
options(echo=TRUE)



# Interaction Method Code
interact.helper <- function(r_level, l_level, l_vec, r_vec) {
   v1 <- l_vec == l_level;
   v2 <- r_vec == r_level;
   vec <- v1 & v2
   #key <- vec@frame_id
   h2o.assign(vec, paste('l', l_level, '_', 'r', r_level, sep = ""))
}

inner <- function(l_level, r_levels, l_vec, r_vec) { lapply(r_levels, interact.helper, l_level, l_vec, r_vec) }

get.name <- function(r_level, l_level) paste('l', l_level, '_', 'r', r_level, sep = "")
inner.names <- function(l_level, r_levels) lapply(r_levels, get.name, l_level)

interact <- function(fr, l_vec, r_vec) {
   print("interact 1")
   first <- h2o.levels(fr, r_vec)
   print("interact 1.1")
   second <- h2o.levels(fr, r_vec)
   print("interact 1.2")
   foo <- fr[l_vec]
   print("interact 1.2.1")
   lapply(first, inner, second, fr[l_vec], fr[r_vec])
   print("interact 1.3")
   unlist(lapply(first, inner, second, fr[l_vec], fr[r_vec]))
   print("interact 1.4")
   terms <- unlist(lapply(h2o.levels(fr, l_vec), inner, h2o.levels(fr, r_vec), fr[l_vec], fr[r_vec]))
   print("interact 2")
   terms <- h2o.cbind(terms)
   print("interact 3")
   print(terms)
   print("interact 4")
   colnames(terms) <- unlist(inner.names(h2o.levels(fr, l_vec), h2o.levels(fr, r_vec)))
   terms
}

# End Interaction Method Code


# Begin Demo #

check.demo_interact <- function() {
  #uploading data file to h2o
  filePath <- locate("smalldata/logreg/prostate.csv")
  hex <- h2o.uploadFile(filePath, "prostate")[1:10,]

  hex$RACE <- as.factor(hex$RACE)
  hex$GLEASON <- as.factor(hex$GLEASON)

  race <- which(colnames(hex) == "RACE")
  glea <- which(colnames(hex) == "GLEASON")

  print(h2o.levels(hex, race))
  print(h2o.levels(hex, glea))
  interaction.matrix <- interact(hex, race, glea)

  print(interaction.matrix)

  augmented_data_set <- h2o.assign(h2o.cbind(hex, interaction.matrix), "augmented")

  h2o.rm(interaction.matrix)

  lapply(as.vector(h2o.ls()[as.h2o(grepl("^l", as.vector(h2o.ls()[,1]))),]), function(x) h2o.rm(x))

  print(augmented_data_set)

  print( h2o.ls() )

  testEnd()
}

doTest("x-prod interaction terms between two categorical vectors", check.demo_interact)
