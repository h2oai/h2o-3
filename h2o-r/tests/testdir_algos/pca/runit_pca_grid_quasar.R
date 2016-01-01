setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



check.pca.grid.quasar <- function() {
  quasar <- h2o.importFile(h2oTest.locate("smalldata/pca_test/SDSS_quasar.txt.zip"), header = TRUE)
  quasar <- quasar[,-1]

  grid_space = h2oTest.makeRandomGridSpace(algo="pca",ncols=ncol(quasar),nrows=nrow(quasar))
  h2oTest.logInfo(lapply(names(grid_space), function(n) paste0("The ",n," search space: ", grid_space[n])))

  h2oTest.logInfo("Constructing the grid of pca models...")
  quasar_pca_grid = h2o.grid("pca", grid_id="pca_grid_quasar_test", x=1:22, training_frame=quasar, hyper_params=grid_space)

  h2oTest.logInfo("Performing various checks of the constructed grid...")
  h2oTest.logInfo("Check cardinality of grid, that is, the correct number of models have been created...")
  size_of_grid_space <- 1
  for ( name in names(grid_space) ) { size_of_grid_space <- size_of_grid_space * length(grid_space[[name]]) }
  expect_equal(length(quasar_pca_grid@model_ids), size_of_grid_space)

  h2oTest.logInfo("Duplicate-entries-in-grid-space check")
  new_grid_space <- grid_space
  for ( name in names(grid_space) ) { new_grid_space[[name]] <- c(grid_space[[name]],grid_space[[name]]) }
  h2oTest.logInfo(lapply(names(new_grid_space), function(n) paste0("The new ",n," search space: ", new_grid_space[n])))
  h2oTest.logInfo("Constructing the new grid of pca models...")
  quasar_pca_grid2 = h2o.grid("pca", grid_id="pca_grid_quasar_test2", x=1:22, training_frame=quasar, hyper_params=grid_space)
  expect_equal(length(quasar_pca_grid@model_ids), length(quasar_pca_grid2@model_ids))

  h2oTest.logInfo("Check that the hyper_params that were passed to grid, were used to construct the models...")
  # Get models
  grid_models <- lapply(quasar_pca_grid2@model_ids, function(mid) { model = h2o.getModel(mid) })
  # Check expected number of models
  expect_equal(length(grid_models), size_of_grid_space)
  # Check parameters coverage
  for ( name in names(grid_space) ) { h2oTest.expectModelParam(grid_models, name, grid_space[[name]]) }

  # TODO
  # h2oTest.logInfo("Check best grid model against a randomly selected grid model...")

  
}

h2oTest.doTest("PCA Grid Search using quasar dataset", check.pca.grid.quasar)

