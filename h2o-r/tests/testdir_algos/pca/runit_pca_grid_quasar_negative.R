setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



check.pca.grid.quasar.negative <- function() {
  quasar <- h2o.importFile(h2oTest.locate("smalldata/pca_test/SDSS_quasar.txt.zip"), header = TRUE)
  quasar <- quasar[,-1]

  ## Invalid pca parameters
  grid_space <- list()
  grid_space$max_iterations <- c(1,2,-50)
  grid_space$transform <- c("NONE","STANDARDIZE","BAR")
  h2oTest.logInfo(lapply(names(grid_space), function(n) paste0("The provided ",n," search space: ", grid_space[n])))

  expected_grid_space <- list()
  expected_grid_space$max_iterations <- c(1,2)
  expected_grid_space$transform = c("NONE","STANDARDIZE")
  h2oTest.logInfo(lapply(names(grid_space), function(n) paste0("The expected ",n," search space: ", expected_grid_space[n])))

  h2oTest.logInfo("Constructing the grid of pca models with some invalid pca parameters...")
  quasar_pca_grid <- h2o.grid("pca", grid_id="pca_grid_quasar_test", x=1:22, k=3, training_frame=quasar, hyper_params=grid_space, do_hyper_params_check=FALSE)
  expect_error(h2o.grid("pca", grid_id="pca_grid_quasar_test", x=1:22, k=3, training_frame=quasar, hyper_params=grid_space, do_hyper_params_check=TRUE))
  print(quasar_pca_grid)

  h2oTest.logInfo("Performing various checks of the constructed grid...")
  h2oTest.logInfo("Check cardinality of grid, that is, the correct number of models have been created...")
  expect_equal(length(quasar_pca_grid@model_ids), 4)

  h2oTest.logInfo("Check that the hyper_params that were passed to grid, were used to construct the models...")
  # Get models
  grid_models <- lapply(quasar_pca_grid@model_ids, function(mid) { model = h2o.getModel(mid) })
  # Check expected number of models
  expect_equal(length(grid_models), 4)
  # Check parameters coverage
  for ( name in names(grid_space) ) { h2oTest.expectModelParam(grid_models, name, expected_grid_space[[name]]) }

  # TODO: Check error messages for cases with invalid pca parameters

  ## Non-gridable parameter passed as grid parameter
  non_gridable_parameter <- sample(1:3, 1)
  if ( non_gridable_parameter == 1 ) { grid_space$pca_method <-c("GramSVD", "Power", "GLRM") }
  if ( non_gridable_parameter == 2 ) { grid_space$seed <- c(1234, 5678) }
  if ( non_gridable_parameter == 3 ) { grid_space$use_all_factor_levels <- c(TRUE, FALSE) }

  h2oTest.logInfo(paste0("Constructing the grid of pca models with non-gridable parameter: ", non_gridable_parameter ,
                  " (1:pca_method, 2:seed, 3:use_all_factor_levels). Expecting failure..."))
  expect_error(quasar_pca_grid <- h2o.grid("pca", grid_id="pca_grid_quasar_test", x=1:22, k=3, training_frame=quasar, hyper_params=grid_space, do_hyper_params_check=TRUE))

  
}

h2oTest.doTest("PCA Grid Search using bad parameters", check.pca.grid.quasar.negative)

