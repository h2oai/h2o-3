# H2O Ensemble (beta)

The `h2oEnsemble` R package provides functionality to create ensembles from the base learning algorithms that are accessible via the `h2o` R package (H2O version 3.0 and above).  This type of ensemble learning is called "super learning", "stacked regression" or "stacking."  The Super Learner algorithm learns the optimal combination of the base learner fits. In a 2007 article titled, "[Super Learner](http://dx.doi.org/10.2202/1544-6115.1309)," it was shown that the super learner ensemble represents an asymptotically optimal system for learning.


## Install
The `h2oEnsemble` package can be installed using either of the following methods.
- Clone the main h2o repository and install the package:
```
git clone https://github.com/h2oai/h2o-3.git
R CMD INSTALL h2o-3/h2o-r/ensemble/h2oEnsemble-package
```
- Install in R using `devtools::install_github`:
```
library(devtools)
install_github("h2oai/h2o-3/h2o-r/ensemble/h2oEnsemble-package")
```

## Create Ensembles
- An example of how to train and test an ensemble is in the `h2o.ensemble` function documentation in the `h2oEnsemble` package.
- The ensemble is defined by its set of base learning algorithms and the metalearning algorithm.  Algorithm wrapper functions are used to specify these algorithms.
- The ensemble fit is an object of class, "h2o.ensemble", however this is just an R list.
- See the `example_twoClass_higgs.R` script or the `h2o.ensemble` R documentation for an example.


## Wrapper Functions
- The ensemble works by using wrapper functions (located in the `wrappers.R` file in the package).  These wrapper functions are used to specify the base learner and metalearner algorithms for the ensemble.
- This methodology of using wrapper functions is modeled after the [SuperLearner](http://cran.r-project.org/web/packages/SuperLearner/index.html) and [subsemble](http://cran.r-project.org/web/packages/subsemble/index.html) ensemble learning packages.  The use of wrapper functions makes the ensemble code cleaner by providing a unified interface.
- Often it is a good idea to include variants of one algorithm/function by specifying different tuning parameters for different base learners.  There is an examples of how to create new variants of the wrapper functions in the `create_h2o_wrappers.R` script.
- The wrapper functions must have unique names.


## Metalearning
- Historically, techniques like [non-negative least squares (NNLS)](https://en.wikipedia.org/wiki/Non-negative_least_squares) have been used to find the optimal weighted combination of the base learners, however any supervised learning algorithm can be used as a metalearner.  
- If your base learning library includes several versions of a particular algorithm (with different tuning parameters), then you should consider using a metalearner that can perform well in the presence of correlated predictors (e.g. Ridge Regression).
- We allow the user to specify any learner wrapper to define a metalearner, and we can use the `SL.nnls` function (in the `SuperLearner_wrappers.R` script) and the `SL.glm` (included in the [SuperLearner](http://cran.r-project.org/web/packages/SuperLearner/index.html) R package).
- The ensembles that use an h2o-based metalearner have suboptimal performance, which will be addressed in a future release.  


## Known Issues
- This package is incompatible with R 3.0.0-3.1.0 due to a [parser bug](https://bugs.r-project.org/bugzilla3/show_bug.cgi?id=15753) in R.  Upgrade to R 3.1.1 or greater to resolve the issue.  It may work on earlier versions of R but has not been tested.
- Sometimes while executing `h2o.ensemble`, the code hangs due to a communication issue with H2O.  This seems to happen mostly when using the R interpreter, so if you run into this issue, try to run your script using `R CMD BATCH` or `Rscript`, or simply kill R and restart the interpreter.  The output looks something like this:
```
GET /Cloud.json HTTP/1.1
Host: localhost:54321
Accept: */*
```
- The `h2o.*` algorithms are currently performing sub-optimally as metalearners.  Work is being done to address this issue.  As a result, we are currently supporting the `SL.*` based metalearners from the [SuperLearner](http://cran.r-project.org/web/packages/SuperLearner/index.html) R package.  This means that a matrix of size `n x L`, where `L` is the number of base learners and `n` is the number of training observations, must be pulled into R memory for the metalearning step.  This is a memory bottleneck that will be addressed in a future release.  The plan is to use H2O/Java-based metalearners to avoid having to pull data back into R.
- When using a `h2o.deeplearning` model as a base learner, it is not possible to reproduce ensemble model results exactly even when using the `seed` argument of `h2o.ensemble` is set if your H2O cluster uses multiple cores.  This is due to the fact that `h2o.deeplearning` results are only reproducible when trained on a single core.
- The multicore and snow cluster functionality is not working (see the `parallel` option of the `h2o.ensemble` function).  There is a conflict with using the R parallel functionality in conjunction with the H2O parallel functionality.  The `h2o.*` base learning algorithms will use all cores available, so even when the `h2o.ensemble` function is executed with the default `parallel = "seq"` option, you will be training in parallel.  The `parallel` argument was intended to parallelize the cross-validation and base learning steps, but this functionality either needs to be re-architected to work in concert with H2O parallelism or removed in a future release.
- Currently, the `h2o.ensemble` function outputs a list object which makes up the ensemble "model".  This R object can be serialized to disk using the R base `save` function.  However, if you save the ensemble model to disk, then use it in the future to generate predictions on a test set using a new H2O cluster instance (with a different cluster IP address), this will not work.  This can be fixed by updating the cluster IP address in the saved object with the new one.  The model saving process will probably be modified in the future to serialize each of the individual H2O base models using the `h2o::saveModel` function.  Therefore, the saved H2O base models will be accessible individually.  Currently, the ensemble fit is stored as a single R list object which contains all the base learner fits, the metalearner fit, and a few other pieces of data.


## Benchmarks

Benchmarking code for `h2oEnsemble` Classic (compatible with H2O Classic (H2O version < 3.0)) is available here: [https://github.com/ledell/h2oEnsemble-benchmarks](https://github.com/ledell/h2oEnsemble-benchmarks)

