# Here is an example of how to create variants of the h2o algorithm wrappers.
# This will create h2o.glm wrappers with different alphas.
# The same technique can be applied to the other h2o wrapper functions.

library(h2o)

create_h2o_glm_wrappers <- function(alphas = c(0.0, 0.5, 1.0)) {
  for (i in seq(length(alphas))) {
    alpha <- alphas[i]
    body <- sprintf(' <- function(..., alpha = %s) h2o.glm.wrapper(..., alpha = alpha)', alpha)
    eval(parse(text = paste('h2o.glm.', i, body, sep = '')), envir = .GlobalEnv)
  }
}
create_h2o_glm_wrappers()