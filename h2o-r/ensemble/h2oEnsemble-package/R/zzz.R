.onAttach <- function(...) {
  packageStartupMessage('h2oEnsemble R package for H2O-3')
  packageStartupMessage('Version: ', utils::packageDescription('h2oEnsemble')$Version)
  packageStartupMessage('Package created on ', utils::packageDescription('h2oEnsemble')$Date, '\n')
}
