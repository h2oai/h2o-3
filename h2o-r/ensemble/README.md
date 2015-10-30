# H2O Ensemble (beta)

The `h2oEnsemble` R package provides functionality to create ensembles from the base learning algorithms that are accessible via the `h2o` R package (H2O version 3.0 and above).  This type of ensemble learning is called "super learning", "stacked regression" or "stacking."  The Super Learner algorithm learns the optimal combination of the base learner fits. In a 2007 article titled, "[Super Learner](http://dx.doi.org/10.2202/1544-6115.1309)," it was shown that the super learner ensemble represents an asymptotically optimal system for learning.


## Install

### Prerequisites
The current version of `h2oEnsemble` requires the latest version of the `h2o` R package to run.  If it's your first time installing the `h2o` package, you can download the required R dependencies as follows:
```r
if (! ("methods" %in% rownames(installed.packages()))) { install.packages("methods") }
if (! ("statmod" %in% rownames(installed.packages()))) { install.packages("statmod") }
if (! ("stats" %in% rownames(installed.packages()))) { install.packages("stats") }
if (! ("graphics" %in% rownames(installed.packages()))) { install.packages("graphics") }
if (! ("RCurl" %in% rownames(installed.packages()))) { install.packages("RCurl") }
if (! ("jsonlite" %in% rownames(installed.packages()))) { install.packages("jsonlite") }
if (! ("tools" %in% rownames(installed.packages()))) { install.packages("tools") }
if (! ("utils" %in% rownames(installed.packages()))) { install.packages("utils") }
```

To update or download the latest version of the `h2o` package, type these commands into your R shell:
```r
# The following two commands remove any previously installed H2O packages for R.
if ("package:h2o" %in% search()) { detach("package:h2o", unload=TRUE) }
if ("h2o" %in% rownames(installed.packages())) { remove.packages("h2o") }

# Now we download, install and initialize the latest stable release of the *h2o* package for R.
install.packages("h2o", type="source", repos=(c("http://h2o-release.s3.amazonaws.com/h2o/rel-slater/9/R")))
library(h2o)
```

### Install H2O Ensemble
The `h2oEnsemble` package can be installed using either of the following methods.

Recommended:
- Install in R using `devtools::install_github`:
```r
library(devtools)
install_github("h2oai/h2o-3/h2o-r/ensemble/h2oEnsemble-package")
```
If you cloned the main h2o-3 repo:
- Clone the main h2o repository and install the package:
```bash
git clone https://github.com/h2oai/h2o-3.git
R CMD INSTALL h2o-3/h2o-r/ensemble/h2oEnsemble-package
```


## Create Ensembles
- An example of how to train and test an ensemble is in the `h2o.ensemble` function documentation in the `h2oEnsemble` package.
- The ensemble is defined by its set of base learning algorithms and the metalearning algorithm.  Algorithm wrapper functions are used to specify these algorithms.
- The ensemble fit is an object of class, "h2o.ensemble", however this is just an R list.
- See the [example_twoClass_higgs.R](https://github.com/h2oai/h2o-3/blob/master/h2o-r/ensemble/example_twoClass_higgs.R) script or the `h2o.ensemble` R documentation for an example.


## Wrapper Functions
- The ensemble works by using wrapper functions (located in the `wrappers.R` file in the package).  These wrapper functions are used to specify the base learner and metalearner algorithms for the ensemble.
- This methodology of using wrapper functions is modeled after the [SuperLearner](http://cran.r-project.org/web/packages/SuperLearner/index.html) and [subsemble](http://cran.r-project.org/web/packages/subsemble/index.html) ensemble learning packages.  The use of wrapper functions makes the ensemble code cleaner by providing a unified interface.
- Often it is a good idea to include variants of one algorithm/function by specifying different tuning parameters for different base learners.  There is an examples of how to create new variants of the wrapper functions in the [create_h2o_wrappers.R](https://github.com/h2oai/h2o-3/blob/master/h2o-r/ensemble/create_h2o_wrappers.R) script.
- The wrapper functions must have unique names.


## Metalearning
- Historically, techniques like [non-negative least squares (NNLS)](https://en.wikipedia.org/wiki/Non-negative_least_squares) have been used to find the optimal weighted combination of the base learners, however any supervised learning algorithm can be used as a metalearner.  
- If your base learning library includes several versions of a particular algorithm (with different tuning parameters), then you should consider using a metalearner that can perform well in the presence of correlated predictors (e.g. Ridge Regression).
- We allow the user to specify any learner wrapper to define a metalearner, however, we recommend starting with the default, `h2o.glm.wrapper`, or custom GLM-based wrappers.  Since the metalearning step is relatively quick compared to the base learning tasks, we recommend using the `h2o.metalearn` function to re-train the ensemble fit using different metalearning algorithms.
- At this time, we still support using SuperLearner-baesd functions for metalearners, although, for performance reasons, it is not recommended.  For example, you can use the `SL.nnls` function (in the `SuperLearner_wrappers.R` script) and the `SL.glm` (included in the [SuperLearner](http://cran.r-project.org/web/packages/SuperLearner/index.html) R package).  When using a SuperLearner-based function for a metalearner, an `N x L` matrix will be pulled into R memory from H2O (`n` is number of observations and `L` is the number of base learners).  This may cause the code to fail for training sets of greater than ~8M rows due to a memory allocation issue.  Support for SuperLearner-based metalearners will be deprecated in the future when this package is merged into the H2O core (if this is a problem for anyone let me know).



## Known Issues
- This package is incompatible with R 3.0.0-3.1.0 due to a [parser bug](https://bugs.r-project.org/bugzilla3/show_bug.cgi?id=15753) in R.  Upgrade to R 3.1.1 or greater to resolve the issue.  It may work on earlier versions of R but has not been tested.
- When using a `h2o.deeplearning` model as a base learner, it is not possible to reproduce ensemble model results exactly (even when using the `seed` argument of `h2o.ensemble`) if your H2O cluster uses multiple cores.  This is due to the fact that `h2o.deeplearning` results are only reproducible when trained on a single core.  More info [here](https://0xdata.atlassian.net/projects/TN/issues/TN-3).
- The [SNOW](https://cran.r-project.org/web/packages/snow/) cluster functionality is not active at this time (see the `parallel` option of the `h2o.ensemble` function).  There is a conflict with using the R parallel functionality in conjunction with the H2O parallel functionality.  The `h2o.*` base learning algorithms will use all cores available, so even when the `h2o.ensemble` function is executed with the default `parallel = "seq"` option, the H2O algorithms will be training in parallel.  The `parallel` argument was intended to parallelize the cross-validation and base learning steps, but this functionality either needs to be re-architected to work in concert with H2O parallelism or removed in a future release.
- Currently, the `h2o.ensemble` function outputs a list object which makes up the ensemble "model".  This R object can be serialized to disk using the R base `save` function.  However, if you save the ensemble model to disk, then use it in the future to generate predictions on a test set using a new H2O cluster instance (with a different cluster IP address), this will not work.  This can be fixed by updating the cluster IP address in the saved object with the new one.  The model saving process will probably be modified in the future to serialize each of the individual H2O base models using the `h2o::saveModel` function.  Therefore, the saved H2O base models will be accessible individually.  Currently, the ensemble fit is stored as a single R list object which contains all the base learner fits, the metalearner fit, and a few other pieces of data.
- Passing the `validation_frame` to `h2o.ensemble` does not currently do anything.  This should be updated to produce predicted values.  Right now, you must use the `predict.h2o.ensemble` function to generate predictions on a test set.


## Benchmarks

Benchmarking code for `h2oEnsemble` Classic (compatible with H2O Classic (H2O version < 3.0)) is available here: [https://github.com/ledell/h2oEnsemble-benchmarks](https://github.com/ledell/h2oEnsemble-benchmarks)  These benchmarks are out of date -- a major rewrite of the `h2o.ensemble` backend occured in version 0.0.5, which speeds things up quite a bit.  New benchmarks forthcoming. 

