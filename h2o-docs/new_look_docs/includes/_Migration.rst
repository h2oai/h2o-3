Migrating to H2O 3.0
====================

We're excited about the upcoming release of the latest and greatest
version of H2O, and we hope you are too! H2O 3.0 has lots of
improvements, including:

-  Powerful Python APIs
-  Flow, a brand-new intuitive web UI
-  The ability to share, annotate, and modify workflows
-  Versioned REST APIs with full metadata
-  Spark integration using Sparkling Water
-  Improved algorithm accuracy and speed

and much more! Overall, H2O has been retooled for better accuracy and
performance and to provide additional functionality. If you're a current
user of H2O, we strongly encourage you to upgrade to the latest version
to take advantage of the latest features and capabilities.

Please be aware that H2O 3.0 will supersede all previous versions of H2O
as the primary version as of May 15th, 2015. Support for previous
versions will be offered for a limited time, but there will no longer be
any significant updates to the previous version of H2O.

The following information and links will inform you about what's new and
different and help you prepare to upgrade to H2O 3.0.

Overall, H2O 3.0 is more stable, elegant, and simplified, with
additional capabilities not available in previous versions of H2O.

--------------

Algorithm Changes
-----------------

Most of the algorithms available in previous versions of H2O have been
improved in terms of speed and accuracy. Currently available model types
include:

Supervised
~~~~~~~~~~

-  **Generalized Linear Model (GLM)**: Binomial classification,
   multinomial classification, regression (including logistic
   regression)
-  **Distributed Random Forest (DRF)**: Binomial classification,
   multinomial classification, regression
-  **Gradient Boosting Machine (GBM)**: Binomial classification,
   multinomial classification, regression
-  **Deep Learning (DL)**: Binomial classification, multinomial
   classification, regression

Unsupervised
~~~~~~~~~~~~

-  K-means
-  Principal Component Analysis
-  Autoencoder

There are a few algorithms that are still being refined to provide these
same benefits and will be available in a future version of H2O.

Currently, the following algorithms and associated capabilities are
still in development:

-  Naïve Bayes

Check back for updates, as these algorithms will be re-introduced in an
improved form in a future version of H2O.

**Note**: The SpeeDRF model has been removed, as it was originally
intended as an optimization for small data only. This optimization will
be added to the Distributed Random Forest model automatically for small
data in a future version of H2O.

--------------

Parsing Changes
---------------

In H2O Classic, the parser reads all the data and tries to guess the
column type. In H2O 3.0, the parser reads a subset and makes a type
guess for each column. In Flow, you can view the preliminary parse
results in the **Edit Column Names and Types** area. To change the
column type, select an option from the drop-down menu to the right of
the column. H2O 3.0 can also automatically identify mixed-type columns;
in H2O Classic, if one column is mixed integers or real numbers using a
string, the output is blank.

--------------

Web UI Changes
--------------

Our web UI has been completely overhauled with a much more intuitive
interface that is similar to IPython Notebook. Each point-and-click
action is translated immediately into an individual workflow script that
can be saved for later interactive and offline use. As a result, you can
now revise and rerun your workflows easily, and can even add comments
and rich media.

For more information, refer to our `Getting Started with
Flow <https://github.com/h2oai/h2o-dev/blob/master/h2o-docs/src/product/flow/README.md>`__
guide, which comprehensively documents how to use Flow. You can also
view this brief `video <https://www.youtube.com/watch?v=wzeuFfbW7WE>`__,
which provides an overview of Flow in action.

--------------

API Users
---------

H2O's new Python API allows Pythonistas to use H2O in their favorite
environment. Using the Python command line or an integrated development
environment like IPython Notebook, H2O users can control clusters and
manage massive datasets quickly.

H2O's REST API is the basis for the web UI (Flow), as well as the R and
Python APIs, and is versioned for stability. It is also easier to
understand and use, with full metadata available dynamically from the
server, allowing for easier integration by developers.

--------------

Java Users
----------

Generated Java REST classes ease REST API use by external programs
running in a Java Virtual Machine (JVM).

As in previous versions of H2O, users can export trained models as Java
objects for easy integration into JVM applications. H2O is currently the
only ML tool that provides this capability, making it the data science
tool of choice for enterprise developers.

--------------

R Users
-------

If you use H2O primarily in R, be aware that as a result of the
improvements to the R package for H2O scripts created using previous
versions (Nunes 2.8.6.2 or prior) will require minor revisions to work
with H2O 3.0.

To assist our R users in upgrading to H2O 3.0, a "shim" tool has been
developed. The
`shim <https://github.com/h2oai/h2o-dev/blob/9795c401b7be339be56b1b366ffe816133cccb9d/h2o-r/h2o-package/R/shim.R>`__
reviews your script, identifies deprecated or revised parameters and
arguments, and suggests replacements.

    **Note**: As of Slater v.3.2.0.10, this shim will no longer be
    available.

There is also an `R Porting Guide <#PortingGuide>`__ that provides a
side-by-side comparison of the algorithms in the previous version of H2O
with H2O 3.0. It outlines the new, revised, and deprecated parameters
for each algorithm, as well as the changes to the output.

--------------

Porting R Scripts
=================

This document outlines how to port R scripts written in previous
versions of H2O (Nunes 2.8.6.2 or prior, also known as "H2O Classic")
for compatibility with the new H2O 3.0 API. When upgrading from H2O to
H2O 3.0, most functions are the same. However, there are some
differences that will need to be resolved when porting any scripts that
were originally created using H2O to H2O 3.0.

The original R script for H2O is listed first, followed by the updated
script for H2O 3.0.

Some of the parameters have been renamed for consistency. For each
algorithm, a table that describes the differences is provided.

For additional assistance within R, enter a question mark before the
command (for example, ``?h2o.glm``).

There is also a "shim" available that will review R scripts created with
previous versions of H2O, identify deprecated or renamed parameters, and
suggest replacements. For more information, refer to the repo
`here <https://github.com/h2oai/h2o-dev/blob/d9693a97da939a2b77c24507c8b40a5992192489/h2o-r/h2o-package/R/shim.R>`__.

Changes from H2O 2.8 to H2O 3.0
-------------------------------

``h2o.exec``
~~~~~~~~~~~~

The ``h2o.exec`` command is no longer supported. Any workflows using
``h2o.exec`` must be revised to remove this command. If the H2O 3.0
workflow contains any parameters or commands from H2O Classic, errors
will result and the workflow will fail.

The purpose of ``h2o.exec`` was to wrap expressions so that they could
be evaluated in a single ``\Exec2`` call. For example,
``h2o.exec(fr[,1] + 2/fr[,3])`` and ``fr[,1] + 2/fr[,3]`` produced the
same results in H2O. However, the first example makes a single REST call
and uses a single temp object, while the second makes several REST calls
and uses several temp objects.

Due to the improved architecture in H2O 3.0, the need to use
``h2o.exec`` has been eliminated, as the expression can be processed by
R as an "unwrapped" typical R expression.

Currently, the only known exception is when ``factor`` is used in
conjunction with ``h2o.exec``. For example,
``h2o.exec(fr$myIntCol <- factor(fr$myIntCol))`` would become
``fr$myIntCol <- as.factor(fr$myIntCol)``

Note also that an array is not inside a string:

An int array is [1, 2, 3], *not* "[1, 2, 3]".

A String array is ["f00", "b4r"], *not* "["f00", "b4r"]"

Only string values are enclosed in double quotation marks (``"``).

 ###\ ``h2o.performance``

To access any exclusively binomial output, use ``h2o.performance``,
optionally with the corresponding accessor. The accessor can only use
the model metrics object created by ``h2o.performance``. Each accessor
is named for its corresponding field (for example, ``h2o.AUC``,
``h2o.gini``, ``h2o.F1``). ``h2o.performance`` supports all current
algorithms except for K-Means.

If you specify a data frame as a second parameter, H2O will use the
specified data frame for scoring. If you do not specify a second
parameter, the training metrics for the model metrics object are used.

``xval`` and ``validation`` slots
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The ``xval`` slot has been removed, as ``nfolds`` is not currently
supported.

The ``validation`` slot has been merged with the ``model`` slot.

Principal Components Regression (PCR)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Principal Components Regression (PCR) has also been deprecated. To
obtain PCR values, create a Principal Components Analysis (PCA) model,
then create a GLM model from the scored data from the PCA model.

Saving and Loading Models
~~~~~~~~~~~~~~~~~~~~~~~~~

Saving and loading a model from R is supported in version 3.0.0.18 and
later. H2O 3.0 uses the same binary serialization method as previous
versions of H2O, but saves the model and its dependencies into a
directory, with each object as a separate file. The ``save_CV`` option
for available in previous versions of H2O has been deprecated, as
``h2o.saveAll`` and ``h2o.loadAll`` are not currently supported. The
following commands are now supported:

-  ``h2o.saveModel``
-  ``h2o.loadModel``

**Table of Contents**

-  `GBM <#GBM>`__
-  `GLM <#GLM>`__
-  `K-Means <#Kmeans>`__
-  `Deep Learning <#DL>`__
-  `Distributed Random Forest <#DRF>`__

 ##GBM

N-fold cross-validation and grid search are currently supported in H2O
3.0.

Renamed GBM Parameters
~~~~~~~~~~~~~~~~~~~~~~

The following parameters have been renamed, but retain the same
functions:

+------------------------------+------------------------------+
| H2O Classic Parameter Name   | H2O 3.0 Parameter Name       |
+==============================+==============================+
| ``data``                     | ``training_frame``           |
+------------------------------+------------------------------+
| ``key``                      | ``model_id``                 |
+------------------------------+------------------------------+
| ``n.trees``                  | ``ntrees``                   |
+------------------------------+------------------------------+
| ``interaction.depth``        | ``max_depth``                |
+------------------------------+------------------------------+
| ``n.minobsinnode``           | ``min_rows``                 |
+------------------------------+------------------------------+
| ``shrinkage``                | ``learn_rate``               |
+------------------------------+------------------------------+
| ``n.bins``                   | ``nbins``                    |
+------------------------------+------------------------------+
| ``validation``               | ``validation_frame``         |
+------------------------------+------------------------------+
| ``balance.classes``          | ``balance_classes``          |
+------------------------------+------------------------------+
| ``max.after.balance.size``   | ``max_after_balance_size``   |
+------------------------------+------------------------------+

Deprecated GBM Parameters
~~~~~~~~~~~~~~~~~~~~~~~~~

The following parameters have been removed:

-  ``group_split``: Bit-set group splitting of categorical variables is
   now the default.
-  ``importance``: Variable importances are now computed automatically
   and displayed in the model output.
-  ``holdout.fraction``: The fraction of the training data to hold out
   for validation is no longer supported.
-  ``grid.parallelism``: Specifying the number of parallel threads to
   run during a grid search is no longer supported.

New GBM Parameters
~~~~~~~~~~~~~~~~~~

The following parameters have been added:

-  ``seed``: A random number to control sampling and initialization when
   ``balance_classes`` is enabled.
-  ``score_each_iteration``: Display error rate information after each
   tree in the requested set is built.
-  ``build_tree_one_node``: Run on a single node to use fewer CPUs.

GBM Algorithm Comparison
~~~~~~~~~~~~~~~~~~~~~~~~

+----------------+----------------+
| H2O Classic    | H2O 3.0        |
+================+================+
| ``h2o.gbm <- f | ``h2o.gbm <- f |
| unction(``     | unction(``     |
+----------------+----------------+
| ``x,``         | ``x,``         |
+----------------+----------------+
| ``y,``         | ``y,``         |
+----------------+----------------+
| ``data,``      | ``training_fra |
|                | me,``          |
+----------------+----------------+
| ``key = "",``  | ``model_id,``  |
+----------------+----------------+
|                | ``checkpoint`` |
+----------------+----------------+
| ``distribution | ``distribution |
|  = 'multinomia |  = c("AUTO", " |
| l',``          | gaussian", "be |
|                | rnoulli", "mul |
|                | tinomial", "po |
|                | isson", "gamma |
|                | ", "tweedie"), |
|                | ``             |
+----------------+----------------+
|                | ``tweedie_powe |
|                | r = 1.5,``     |
+----------------+----------------+
| ``n.trees = 10 | ``ntrees = 50` |
| ,``            | `              |
+----------------+----------------+
| ``interaction. | ``max_depth =  |
| depth = 5,``   | 5,``           |
+----------------+----------------+
| ``n.minobsinno | ``min_rows = 1 |
| de = 10,``     | 0,``           |
+----------------+----------------+
| ``shrinkage =  | ``learn_rate = |
| 0.1,``         |  0.1,``        |
+----------------+----------------+
|                | ``sample_rate  |
|                | = 1``          |
+----------------+----------------+
|                | ``col_sample_r |
|                | ate = 1``      |
+----------------+----------------+
| ``n.bins = 20, | ``nbins = 20,` |
| ``             | `              |
+----------------+----------------+
|                | ``nbins_top_le |
|                | vel,``         |
+----------------+----------------+
|                | ``nbins_cats = |
|                |  1024,``       |
+----------------+----------------+
| ``validation,` | ``validation_f |
| `              | rame = NULL,`` |
+----------------+----------------+
| ``balance.clas | ``balance_clas |
| ses = FALSE``  | ses = FALSE,`` |
+----------------+----------------+
| ``max.after.ba | ``max_after_ba |
| lance.size = 5 | lance_size = 1 |
| ,``            | ,``            |
+----------------+----------------+
|                | ``seed,``      |
+----------------+----------------+
|                | ``build_tree_o |
|                | ne_node = FALS |
|                | E,``           |
+----------------+----------------+
|                | ``nfolds = 0,` |
|                | `              |
+----------------+----------------+
|                | ``fold_column  |
|                | = NULL,``      |
+----------------+----------------+
|                | ``fold_assignm |
|                | ent = c("AUTO" |
|                | , "Random", "M |
|                | odulo"),``     |
+----------------+----------------+
|                | ``keep_cross_v |
|                | alidation_pred |
|                | ictions = FALS |
|                | E,``           |
+----------------+----------------+
|                | ``score_each_i |
|                | teration = FAL |
|                | SE,``          |
+----------------+----------------+
|                | ``stopping_rou |
|                | nds = 0,``     |
+----------------+----------------+
|                | ``stopping_met |
|                | ric = c("AUTO" |
|                | , "deviance",  |
|                | "logloss", "MS |
|                | E", "AUC", "r2 |
|                | ", "misclassif |
|                | ication"),``   |
+----------------+----------------+
|                | ``stopping_tol |
|                | erance = 0.001 |
|                | ,``            |
+----------------+----------------+
|                | ``offset_colum |
|                | n = NULL,``    |
+----------------+----------------+
|                | ``weights_colu |
|                | mn = NULL,``   |
+----------------+----------------+
| ``group_split  |                |
| = TRUE,``      |                |
+----------------+----------------+
| ``importance = |                |
|  FALSE,``      |                |
+----------------+----------------+
| ``holdout.frac |                |
| tion = 0,``    |                |
+----------------+----------------+
| ``class.sampli |                |
| ng.factors = N |                |
| ULL,``         |                |
+----------------+----------------+
| ``grid.paralle |                |
| lism = 1)``    |                |
+----------------+----------------+

Output
~~~~~~

The following table provides the component name in H2O, the
corresponding component name in H2O 3.0 (if supported), and the model
type (binomial, multinomial, or all). Many components are now included
in ``h2o.performance``; for more information, refer to
`(``h2o.performance``) <#h2operf>`__.

+----------------+----------------+----------------+
| H2O Classic    | H2O 3.0        | Model Type     |
+================+================+================+
| ``@model$prior |                | ``all``        |
| Distribution`` |                |                |
+----------------+----------------+----------------+
| ``@model$param | ``@allparamete | ``all``        |
| s``            | rs``           |                |
+----------------+----------------+----------------+
| ``@model$err`` | ``@model$scori | ``all``        |
|                | ng_history``   |                |
+----------------+----------------+----------------+
| ``@model$class |                | ``all``        |
| ification``    |                |                |
+----------------+----------------+----------------+
| ``@model$varim | ``@model$varia | ``all``        |
| p``            | ble_importance |                |
|                | s``            |                |
+----------------+----------------+----------------+
| ``@model$confu | ``@model$train | ``binomial``   |
| sion``         | ing_metrics@me | and            |
|                | trics$cm$table | ``multinomial` |
|                | ``             | `              |
+----------------+----------------+----------------+
| ``@model$auc`` | ``@model$train | ``binomial``   |
|                | ing_metrics@me |                |
|                | trics$AUC``    |                |
+----------------+----------------+----------------+
| ``@model$gini` | ``@model$train | ``binomial``   |
| `              | ing_metrics@me |                |
|                | trics$Gini``   |                |
+----------------+----------------+----------------+
| ``@model$best_ |                | ``binomial``   |
| cutoff``       |                |                |
+----------------+----------------+----------------+
| ``@model$F1``  | ``@model$train | ``binomial``   |
|                | ing_metrics@me |                |
|                | trics$threshol |                |
|                | ds_and_metric_ |                |
|                | scores$f1``    |                |
+----------------+----------------+----------------+
| ``@model$F2``  | ``@model$train | ``binomial``   |
|                | ing_metrics@me |                |
|                | trics$threshol |                |
|                | ds_and_metric_ |                |
|                | scores$f2``    |                |
+----------------+----------------+----------------+
| ``@model$accur | ``@model$train | ``binomial``   |
| acy``          | ing_metrics@me |                |
|                | trics$threshol |                |
|                | ds_and_metric_ |                |
|                | scores$accurac |                |
|                | y``            |                |
+----------------+----------------+----------------+
| ``@model$error |                | ``binomial``   |
| ``             |                |                |
+----------------+----------------+----------------+
| ``@model$preci | ``@model$train | ``binomial``   |
| sion``         | ing_metrics@me |                |
|                | trics$threshol |                |
|                | ds_and_metric_ |                |
|                | scores$precisi |                |
|                | on``           |                |
+----------------+----------------+----------------+
| ``@model$recal | ``@model$train | ``binomial``   |
| l``            | ing_metrics@me |                |
|                | trics$threshol |                |
|                | ds_and_metric_ |                |
|                | scores$recall` |                |
|                | `              |                |
+----------------+----------------+----------------+
| ``@model$mcc`` | ``@model$train | ``binomial``   |
|                | ing_metrics@me |                |
|                | trics$threshol |                |
|                | ds_and_metric_ |                |
|                | scores$absolut |                |
|                | e_MCC``        |                |
+----------------+----------------+----------------+
| ``@model$max_p | currently      | ``binomial``   |
| er_class_err`` | replaced by    |                |
|                | ``@model$train |                |
|                | ing_metrics@me |                |
|                | trics$threshol |                |
|                | ds_and_metric_ |                |
|                | scores$min_per |                |
|                | _class_correct |                |
|                | ``             |                |
+----------------+----------------+----------------+

--------------

 ##GLM

Renamed GLM Parameters
~~~~~~~~~~~~~~~~~~~~~~

The following parameters have been renamed, but retain the same
functions:

+------------------------------+--------------------------+
| H2O Classic Parameter Name   | H2O 3.0 Parameter Name   |
+==============================+==========================+
| ``data``                     | ``training_frame``       |
+------------------------------+--------------------------+
| ``key``                      | ``model_id``             |
+------------------------------+--------------------------+
| ``nlambda``                  | ``nlambdas``             |
+------------------------------+--------------------------+
| ``lambda.min.ratio``         | ``lambda_min_ratio``     |
+------------------------------+--------------------------+
| ``iter.max``                 | ``max_iterations``       |
+------------------------------+--------------------------+
| ``epsilon``                  | ``beta_epsilon``         |
+------------------------------+--------------------------+

Deprecated GLM Parameters
~~~~~~~~~~~~~~~~~~~~~~~~~

The following parameters have been removed:

-  ``return_all_lambda``: A logical value indicating whether to return
   every model built during the lambda search. (may be re-added)
-  ``higher_accuracy``: For improved accuracy, adjust the
   ``beta_epsilon`` value.
-  ``strong_rules``: Discards predictors likely to have 0 coefficients
   prior to model building. (may be re-added as enabled by default)
-  ``non_negative``: Specify a non-negative response. (may be re-added)
-  ``variable_importances``: Variable importances are now computed
   automatically and displayed in the model output. They have been
   renamed to *Normalized Coefficient Magnitudes*.
-  ``disable_line_search``: This parameter has been deprecated, as it
   was mainly used for testing purposes.
-  ``max_predictors``: Stops training the algorithm if the number of
   predictors exceeds the specified value. (may be re-added)

New GLM Parameters
~~~~~~~~~~~~~~~~~~

The following parameters have been added:

-  ``validation_frame``: Specify the validation dataset.
-  ``solver``: Select IRLSM or LBFGS.

GLM Algorithm Comparison
~~~~~~~~~~~~~~~~~~~~~~~~

+----------------+----------------+
| H2O Classic    | H2O 3.0        |
+================+================+
| ``h2o.glm <- f | ``h2o.glm(``   |
| unction(``     |                |
+----------------+----------------+
| ``x,``         | ``x,``         |
+----------------+----------------+
| ``y,``         | ``y,``         |
+----------------+----------------+
| ``data,``      | ``training_fra |
|                | me,``          |
+----------------+----------------+
| ``key = "",``  | ``model_id,``  |
+----------------+----------------+
|                | ``validation_f |
|                | rame = NULL``  |
+----------------+----------------+
| ``iter.max = 1 | ``max_iteratio |
| 00,``          | ns = 50,``     |
+----------------+----------------+
| ``epsilon = 1e | ``beta_epsilon |
| -4``           |  = 0``         |
+----------------+----------------+
| ``strong_rules |                |
|  = TRUE,``     |                |
+----------------+----------------+
| ``return_all_l |                |
| ambda = FALSE, |                |
| ``             |                |
+----------------+----------------+
| ``intercept =  | ``intercept =  |
| TRUE,``        | TRUE``         |
+----------------+----------------+
| ``non_negative |                |
|  = FALSE,``    |                |
+----------------+----------------+
|                | ``solver = c(" |
|                | IRLSM", "L_BFG |
|                | S"),``         |
+----------------+----------------+
| ``standardize  | ``standardize  |
| = TRUE,``      | = TRUE,``      |
+----------------+----------------+
| ``family,``    | ``family = c(" |
|                | gaussian", "bi |
|                | nomial", "mult |
|                | inomial", "poi |
|                | sson", "gamma" |
|                | , "tweedie"),` |
|                | `              |
+----------------+----------------+
| ``link,``      | ``link = c("fa |
|                | mily_default", |
|                |  "identity", " |
|                | logit", "log", |
|                |  "inverse", "t |
|                | weedie"),``    |
+----------------+----------------+
| ``tweedie.p =  | ``tweedie_vari |
| ifelse(family  | ance_power = N |
| == "tweedie",1 | aN,``          |
| .5, NA_real_)` |                |
| `              |                |
+----------------+----------------+
|                | ``tweedie_link |
|                | _power = NaN,` |
|                | `              |
+----------------+----------------+
| ``alpha = 0.5, | ``alpha = 0.5, |
| ``             | ``             |
+----------------+----------------+
| ``prior = NULL | ``prior = 0.0, |
| ``             | ``             |
+----------------+----------------+
| ``lambda = 1e- | ``lambda = 1e- |
| 5,``           | 05,``          |
+----------------+----------------+
| ``lambda_searc | ``lambda_searc |
| h = FALSE,``   | h = FALSE,``   |
+----------------+----------------+
| ``nlambda = -1 | ``nlambdas = - |
| ,``            | 1,``           |
+----------------+----------------+
| ``lambda.min.r | ``lambda_min_r |
| atio = -1,``   | atio = 1.0,``  |
+----------------+----------------+
| ``use_all_fact | ``use_all_fact |
| or_levels = FA | or_levels = FA |
| LSE``          | LSE,``         |
+----------------+----------------+
| ``nfolds = 0,` | ``nfolds = 0,` |
| `              | `              |
+----------------+----------------+
|                | ``fold_column  |
|                | = NULL,``      |
+----------------+----------------+
|                | ``fold_assignm |
|                | ent = c("AUTO" |
|                | , "Random", "M |
|                | odulo"),``     |
+----------------+----------------+
|                | ``keep_cross_v |
|                | alidation_pred |
|                | ictions = FALS |
|                | E,``           |
+----------------+----------------+
| ``beta_constra | ``beta_constra |
| ints = NULL,`` | ints = NULL)`` |
+----------------+----------------+
| ``higher_accur |                |
| acy = FALSE,`` |                |
+----------------+----------------+
| ``variable_imp |                |
| ortances = FAL |                |
| SE,``          |                |
+----------------+----------------+
| ``disable_line |                |
| _search = FALS |                |
| E,``           |                |
+----------------+----------------+
| ``offset = NUL | ``offset_colum |
| L,``           | n = NULL,``    |
+----------------+----------------+
|                | ``weights_colu |
|                | mn = NULL,``   |
+----------------+----------------+
|                | ``intercept =  |
|                | TRUE,``        |
+----------------+----------------+
| ``max_predicto | ``max_active_p |
| rs = -1)``     | redictors = -1 |
|                | )``            |
+----------------+----------------+

Output
~~~~~~

The following table provides the component name in H2O, the
corresponding component name in H2O 3.0 (if supported), and the model
type (binomial, multinomial, or all). Many components are now included
in ``h2o.performance``; for more information, refer to
`(``h2o.performance``) <#h2operf>`__.

+----------------+----------------+----------------+
| H2O Classic    | H2O 3.0        | Model Type     |
+================+================+================+
| ``@model$param | ``@allparamete | ``all``        |
| s``            | rs``           |                |
+----------------+----------------+----------------+
| ``@model$coeff | ``@model$coeff | ``all``        |
| icients``      | icients``      |                |
+----------------+----------------+----------------+
| ``@model$nomal | ``@model$coeff | ``all``        |
| ized_coefficie | icients_table$ |                |
| nts``          | norm_coefficie |                |
|                | nts``          |                |
+----------------+----------------+----------------+
| ``@model$rank` | ``@model$rank` | ``all``        |
| `              | `              |                |
+----------------+----------------+----------------+
| ``@model$iter` | ``@model$iter` | ``all``        |
| `              | `              |                |
+----------------+----------------+----------------+
| ``@model$lambd |                | ``all``        |
| a``            |                |                |
+----------------+----------------+----------------+
| ``@model$devia | ``@model$resid | ``all``        |
| nce``          | ual_deviance`` |                |
+----------------+----------------+----------------+
| ``@model$null. | ``@model$null_ | ``all``        |
| deviance``     | deviance``     |                |
+----------------+----------------+----------------+
| ``@model$df.re | ``@model$resid | ``all``        |
| sidual``       | ual_degrees_of |                |
|                | _freedom``     |                |
+----------------+----------------+----------------+
| ``@model$df.nu | ``@model$null_ | ``all``        |
| ll``           | degrees_of_fre |                |
|                | edom``         |                |
+----------------+----------------+----------------+
| ``@model$aic`` | ``@model$AIC`` | ``all``        |
+----------------+----------------+----------------+
| ``@model$train |                | ``binomial``   |
| .err``         |                |                |
+----------------+----------------+----------------+
| ``@model$prior |                | ``binomial``   |
| ``             |                |                |
+----------------+----------------+----------------+
| ``@model$thres | ``@model$thres | ``binomial``   |
| holds``        | hold``         |                |
+----------------+----------------+----------------+
| ``@model$best_ |                | ``binomial``   |
| threshold``    |                |                |
+----------------+----------------+----------------+
| ``@model$auc`` | ``@model$AUC`` | ``binomial``   |
+----------------+----------------+----------------+
| ``@model$confu |                | ``binomial``   |
| sion``         |                |                |
+----------------+----------------+----------------+

 ##K-Means

Renamed K-Means Parameters
~~~~~~~~~~~~~~~~~~~~~~~~~~

The following parameters have been renamed, but retain the same
functions:

+------------------------------+--------------------------+
| H2O Classic Parameter Name   | H2O 3.0 Parameter Name   |
+==============================+==========================+
| ``data``                     | ``training_frame``       |
+------------------------------+--------------------------+
| ``key``                      | ``model_id``             |
+------------------------------+--------------------------+
| ``centers``                  | ``k``                    |
+------------------------------+--------------------------+
| ``cols``                     | ``x``                    |
+------------------------------+--------------------------+
| ``iter.max``                 | ``max_iterations``       |
+------------------------------+--------------------------+
| ``normalize``                | ``standardize``          |
+------------------------------+--------------------------+

**Note** In H2O, the ``normalize`` parameter was disabled by default.
The ``standardize`` parameter is enabled by default in H2O 3.0 to
provide more accurate results for datasets containing columns with large
values.

New K-Means Parameters
~~~~~~~~~~~~~~~~~~~~~~

The following parameters have been added:

-  ``user`` has been added as an additional option for the ``init``
   parameter. Using this parameter forces the K-Means algorithm to start
   at the user-specified points.
-  ``user_points``: Specify starting points for the K-Means algorithm.

K-Means Algorithm Comparison
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

+-------------------------------+--------------------------------------------------------+
| H2O Classic                   | H2O 3.0                                                |
+===============================+========================================================+
| ``h2o.kmeans <- function(``   | ``h2o.kmeans(``                                        |
+-------------------------------+--------------------------------------------------------+
| ``data,``                     | ``training_frame,``                                    |
+-------------------------------+--------------------------------------------------------+
| ``cols = '',``                | ``x,``                                                 |
+-------------------------------+--------------------------------------------------------+
| ``centers,``                  | ``k,``                                                 |
+-------------------------------+--------------------------------------------------------+
| ``key = "",``                 | ``model_id,``                                          |
+-------------------------------+--------------------------------------------------------+
| ``iter.max = 10,``            | ``max_iterations = 1000,``                             |
+-------------------------------+--------------------------------------------------------+
| ``normalize = FALSE,``        | ``standardize = TRUE,``                                |
+-------------------------------+--------------------------------------------------------+
| ``init = "none",``            | ``init = c("Furthest","Random", "PlusPlus"),``         |
+-------------------------------+--------------------------------------------------------+
| ``seed = 0,``                 | ``seed,``                                              |
+-------------------------------+--------------------------------------------------------+
|                               | ``nfolds = 0,``                                        |
+-------------------------------+--------------------------------------------------------+
|                               | ``fold_column = NULL,``                                |
+-------------------------------+--------------------------------------------------------+
|                               | ``fold_assignment = c("AUTO", "Random", "Modulo"),``   |
+-------------------------------+--------------------------------------------------------+
|                               | ``keep_cross_validation_predictions = FALSE)``         |
+-------------------------------+--------------------------------------------------------+

Output
~~~~~~

The following table provides the component name in H2O and the
corresponding component name in H2O 3.0 (if supported).

+---------------------------+-------------------------------+
| H2O Classic               | H2O 3.0                       |
+===========================+===============================+
| ``@model$params``         | ``@allparameters``            |
+---------------------------+-------------------------------+
| ``@model$centers``        | ``@model$centers``            |
+---------------------------+-------------------------------+
| ``@model$tot.withinss``   | ``@model$tot_withinss``       |
+---------------------------+-------------------------------+
| ``@model$size``           | ``@model$size``               |
+---------------------------+-------------------------------+
| ``@model$iter``           | ``@model$iterations``         |
+---------------------------+-------------------------------+
|                           | ``@model$_scoring_history``   |
+---------------------------+-------------------------------+
|                           | ``@model$_model_summary``     |
+---------------------------+-------------------------------+

--------------

 ##Deep Learning

**Note**: If the results in the confusion matrix are incorrect, verify
that ``score_training_samples`` is equal to 0. By default, only the
first 10,000 rows are included.

Renamed Deep Learning Parameters
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The following parameters have been renamed, but retain the same
functions:

+---------------------------------------+--------------------------------------+
| H2O Classic Parameter Name            | H2O 3.0 Parameter Name               |
+=======================================+======================================+
| ``data``                              | ``training_frame``                   |
+---------------------------------------+--------------------------------------+
| ``key``                               | ``model_id``                         |
+---------------------------------------+--------------------------------------+
| ``validation``                        | ``validation_frame``                 |
+---------------------------------------+--------------------------------------+
| ``class.sampling.factors``            | ``class_sampling_factors``           |
+---------------------------------------+--------------------------------------+
| ``override_with_best_model``          | ``overwrite_with_best_model``        |
+---------------------------------------+--------------------------------------+
| ``dlmodel@model$valid_class_error``   | ``@model$validation_metrics@$MSE``   |
+---------------------------------------+--------------------------------------+

Deprecated DL Parameters
~~~~~~~~~~~~~~~~~~~~~~~~

The following parameters have been removed:

-  ``classification``: Classification is now inferred from the data
   type.
-  ``holdout_fraction``: Fraction of the training data to hold out for
   validation.
-  ``dlmodel@model$best_cutoff``: This output parameter has been
   removed.

New DL Parameters
~~~~~~~~~~~~~~~~~

The following parameters have been added:

-  ``export_weights_and_biases``: An additional option allowing users to
   export the raw weights and biases as H2O frames.

The following options for the ``loss`` parameter have been added:

-  ``absolute``: Provides strong penalties for mispredictions
-  ``huber``: Can improve results for regression

DL Algorithm Comparison
~~~~~~~~~~~~~~~~~~~~~~~

+----------------+----------------+
| H2O Classic    | H2O 3.0        |
+================+================+
| ``h2o.deeplear | ``h2o.deeplear |
| ning <- functi | ning (x,``     |
| on(x,``        |                |
+----------------+----------------+
| ``y,``         | ``y,``         |
+----------------+----------------+
| ``data,``      | ``training_fra |
|                | me,``          |
+----------------+----------------+
| ``key = "",``  | ``model_id = " |
|                | ",``           |
+----------------+----------------+
| ``override_wit | ``overwrite_wi |
| h_best_model,` | th_best_model  |
| `              | = true,``      |
+----------------+----------------+
| ``classificati |                |
| on = TRUE,``   |                |
+----------------+----------------+
| ``nfolds = 0,` | ``nfolds = 0`` |
| `              |                |
+----------------+----------------+
| ``validation,` | ``validation_f |
| `              | rame,``        |
+----------------+----------------+
| ``holdout_frac |                |
| tion = 0,``    |                |
+----------------+----------------+
| ``checkpoint = | ``checkpoint,` |
|  " "``         | `              |
+----------------+----------------+
| ``autoencoder, | ``autoencoder  |
| ``             | = false,``     |
+----------------+----------------+
| ``use_all_fact | ``use_all_fact |
| or_levels,``   | or_levels = tr |
|                | ue``           |
+----------------+----------------+
| ``activation,` | ``_activation  |
| `              | = c("Rectifier |
|                | ", "Tanh", "Ta |
|                | nhWithDropout" |
|                | , "RectifierWi |
|                | thDropout", "M |
|                | axout", "Maxou |
|                | tWithDropout") |
|                | ,``            |
+----------------+----------------+
| ``hidden,``    | ``hidden= c(20 |
|                | 0, 200),``     |
+----------------+----------------+
| ``epochs,``    | ``epochs = 10. |
|                | 0,``           |
+----------------+----------------+
| ``train_sample | ``train_sample |
| s_per_iteratio | s_per_iteratio |
| n,``           | n = -2,``      |
+----------------+----------------+
|                | ``target_ratio |
|                | _comm_to_comp  |
|                | = 0.05``       |
+----------------+----------------+
| ``seed,``      | ``_seed,``     |
+----------------+----------------+
| ``adaptive_rat | ``adaptive_rat |
| e,``           | e = true,``    |
+----------------+----------------+
| ``rho,``       | ``rho = 0.99,` |
|                | `              |
+----------------+----------------+
| ``epsilon,``   | ``epsilon = 1e |
|                | -08,``         |
+----------------+----------------+
| ``rate,``      | ``rate = .005, |
|                | ``             |
+----------------+----------------+
| ``rate_anneali | ``rate_anneali |
| ng,``          | ng = 1e-06,``  |
+----------------+----------------+
| ``rate_decay,` | ``rate_decay = |
| `              |  1.0,``        |
+----------------+----------------+
| ``momentum_sta | ``momentum_sta |
| rt,``          | rt = 0,``      |
+----------------+----------------+
| ``momentum_ram | ``momentum_ram |
| p,``           | p = 1e+06,``   |
+----------------+----------------+
| ``momentum_sta | ``momentum_sta |
| ble,``         | ble = 0,``     |
+----------------+----------------+
| ``nesterov_acc | ``nesterov_acc |
| elerated_gradi | elerated_gradi |
| ent,``         | ent = true,``  |
+----------------+----------------+
| ``input_dropou | ``input_dropou |
| t_ratio,``     | t_ratio = 0.0, |
|                | ``             |
+----------------+----------------+
| ``hidden_dropo | ``hidden_dropo |
| ut_ratios,``   | ut_ratios,``   |
+----------------+----------------+
| ``l1,``        | ``l1 = 0.0,``  |
+----------------+----------------+
| ``l2,``        | ``l2 = 0.0,``  |
+----------------+----------------+
| ``max_w2,``    | ``max_w2 = Inf |
|                | ,``            |
+----------------+----------------+
| ``initial_weig | ``initial_weig |
| ht_distributio | ht_distributio |
| n,``           | n = c("Uniform |
|                | Adaptive","Uni |
|                | form", "Normal |
|                | "),``          |
+----------------+----------------+
| ``initial_weig | ``initial_weig |
| ht_scale,``    | ht_scale = 1.0 |
|                | ,``            |
+----------------+----------------+
| ``loss,``      | ``loss = "Auto |
|                | matic", "Cross |
|                | Entropy", "Qua |
|                | dratic", "Abso |
|                | lute", "Huber" |
|                | ),``           |
+----------------+----------------+
|                | ``distribution |
|                |  = c("AUTO", " |
|                | gaussian", "be |
|                | rnoulli", "mul |
|                | tinomial", "po |
|                | isson", "gamma |
|                | ", "tweedie",  |
|                | "laplace", "hu |
|                | ber"),``       |
+----------------+----------------+
|                | ``tweedie_powe |
|                | r = 1.5,``     |
+----------------+----------------+
| ``score_interv | ``score_interv |
| al,``          | al = 5,``      |
+----------------+----------------+
| ``score_traini | ``score_traini |
| ng_samples,``  | ng_samples = 1 |
|                | 0000l,``       |
+----------------+----------------+
| ``score_valida | ``score_valida |
| tion_samples,` | tion_samples = |
| `              |  0l,``         |
+----------------+----------------+
| ``score_duty_c | ``score_duty_c |
| ycle,``        | ycle = 0.1,``  |
+----------------+----------------+
| ``classificati | ``classificati |
| on_stop,``     | on_stop = 0``  |
+----------------+----------------+
| ``regression_s | ``regression_s |
| top,``         | top = 1e-6,``  |
+----------------+----------------+
|                | ``stopping_rou |
|                | nds = 5,``     |
+----------------+----------------+
|                | ``stopping_met |
|                | ric = c("AUTO" |
|                | , "deviance",  |
|                | "logloss", "MS |
|                | E", "AUC", "r2 |
|                | ", "misclassif |
|                | ication"),``   |
+----------------+----------------+
|                | ``stopping_tol |
|                | erance = 0,``  |
+----------------+----------------+
| ``quiet_mode,` | ``quiet_mode = |
| `              |  false,``      |
+----------------+----------------+
| ``max_confusio | ``max_confusio |
| n_matrix_size, | n_matrix_size, |
| ``             | ``             |
+----------------+----------------+
| ``max_hit_rati | ``max_hit_rati |
| o_k,``         | o_k,``         |
+----------------+----------------+
| ``balance_clas | ``balance_clas |
| ses,``         | ses = false,`` |
+----------------+----------------+
| ``class_sampli | ``class_sampli |
| ng_factors,``  | ng_factors,``  |
+----------------+----------------+
| ``max_after_ba | ``max_after_ba |
| lance_size,``  | lance_size,``  |
+----------------+----------------+
| ``score_valida | ``score_valida |
| tion_sampling, | tion_sampling, |
| ``             | ``             |
+----------------+----------------+
| ``diagnostics, | ``diagnostics  |
| ``             | = true,``      |
+----------------+----------------+
| ``variable_imp | ``variable_imp |
| ortances,``    | ortances = fal |
|                | se,``          |
+----------------+----------------+
| ``fast_mode,`` | ``fast_mode =  |
|                | true,``        |
+----------------+----------------+
| ``ignore_const | ``ignore_const |
| _cols,``       | _cols = true,` |
|                | `              |
+----------------+----------------+
| ``force_load_b | ``force_load_b |
| alance,``      | alance = true, |
|                | ``             |
+----------------+----------------+
| ``replicate_tr | ``replicate_tr |
| aining_data,`` | aining_data =  |
|                | true,``        |
+----------------+----------------+
| ``single_node_ | ``single_node_ |
| mode,``        | mode = false,` |
|                | `              |
+----------------+----------------+
| ``shuffle_trai | ``shuffle_trai |
| ning_data,``   | ning_data = fa |
|                | lse,``         |
+----------------+----------------+
| ``sparse,``    | ``sparse = fal |
|                | se,``          |
+----------------+----------------+
| ``col_major,`` | ``col_major =  |
|                | false,``       |
+----------------+----------------+
| ``max_categori | ``max_categori |
| cal_features,` | cal_features,` |
| `              | `              |
+----------------+----------------+
| ``reproducible | ``reproducible |
| )``            | =FALSE,``      |
+----------------+----------------+
| ``average_acti | ``average_acti |
| vation``       | vation = 0,``  |
+----------------+----------------+
|                | ``sparsity_bet |
|                | a = 0``        |
+----------------+----------------+
|                | ``export_weigh |
|                | ts_and_biases= |
|                | FALSE,``       |
+----------------+----------------+
|                | ``offset_colum |
|                | n = NULL,``    |
+----------------+----------------+
|                | ``weights_colu |
|                | mn = NULL,``   |
+----------------+----------------+
|                | ``nfolds = 0,` |
|                | `              |
+----------------+----------------+
|                | ``fold_column  |
|                | = NULL,``      |
+----------------+----------------+
|                | ``fold_assignm |
|                | ent = c("AUTO" |
|                | , "Random", "M |
|                | odulo"),``     |
+----------------+----------------+
|                | ``keep_cross_v |
|                | alidation_pred |
|                | ictions = FALS |
|                | E)``           |
+----------------+----------------+

Output
~~~~~~

The following table provides the component name in H2O, the
corresponding component name in H2O 3.0 (if supported), and the model
type (binomial, multinomial, or all). Many components are now included
in ``h2o.performance``; for more information, refer to
`(``h2o.performance``) <#h2operf>`__.

+----------------+----------------+----------------+
| H2O Classic    | H2O 3.0        | Model Type     |
+================+================+================+
| ``@model$prior |                | ``all``        |
| Distribution`` |                |                |
+----------------+----------------+----------------+
| ``@model$param | ``@allparamete | ``all``        |
| s``            | rs``           |                |
+----------------+----------------+----------------+
| ``@model$train | ``@model$train | ``all``        |
| _class_error`` | ing_metrics@me |                |
|                | trics@$MSE``   |                |
+----------------+----------------+----------------+
| ``@model$valid | ``@model$valid | ``all``        |
| _class_error`` | ation_metrics@ |                |
|                | $MSE``         |                |
+----------------+----------------+----------------+
| ``@model$varim | ``@model$_vari | ``all``        |
| p``            | able_importanc |                |
|                | es``           |                |
+----------------+----------------+----------------+
| ``@model$confu | ``@model$train | ``binomial``   |
| sion``         | ing_metrics@me | and            |
|                | trics$cm$table | ``multinomial` |
|                | ``             | `              |
+----------------+----------------+----------------+
| ``@model$train | ``@model$train | ``binomial``   |
| _auc``         | _AUC``         |                |
+----------------+----------------+----------------+
|                | ``@model$_vali | ``all``        |
|                | dation_metrics |                |
|                | ``             |                |
+----------------+----------------+----------------+
|                | ``@model$_mode | ``all``        |
|                | l_summary``    |                |
+----------------+----------------+----------------+
|                | ``@model$_scor | ``all``        |
|                | ing_history``  |                |
+----------------+----------------+----------------+

--------------

 ##Distributed Random Forest

Changes to DRF in H2O 3.0
~~~~~~~~~~~~~~~~~~~~~~~~~

Distributed Random Forest (DRF) was represented as
``h2o.randomForest(type="BigData", ...)`` in H2O Classic. In H2O
Classic, SpeeDRF (``type="fast"``) was not as accurate, especially for
complex data with categoricals, and did not address regression problems.
DRF (``type="BigData"``) was at least as accurate as SpeeDRF
(``type="fast"``) and was the only algorithm that scaled to big data
(data too large to fit on a single node). In H2O 3.0, our plan is to
improve the performance of DRF so that the data fits on a single node
(optimally, for all cases), which will make SpeeDRF obsolete.
Ultimately, the goal is provide a single algorithm that provides the
"best of both worlds" for all datasets and use cases. Please note that
H2O does not currently support the ability to specify the number of
trees when using ``h2o.predict`` for a DRF model.

**Note**: H2O 3.0 only supports DRF. SpeeDRF is no longer supported. The
functionality of DRF in H2O 3.0 is similar to DRF functionality in H2O.

Renamed DRF Parameters
~~~~~~~~~~~~~~~~~~~~~~

The following parameters have been renamed, but retain the same
functions:

+------------------------------+------------------------------+
| H2O Classic Parameter Name   | H2O 3.0 Parameter Name       |
+==============================+==============================+
| ``data``                     | ``training_frame``           |
+------------------------------+------------------------------+
| ``key``                      | ``model_id``                 |
+------------------------------+------------------------------+
| ``validation``               | ``validation_frame``         |
+------------------------------+------------------------------+
| ``sample.rate``              | ``sample_rate``              |
+------------------------------+------------------------------+
| ``ntree``                    | ``ntrees``                   |
+------------------------------+------------------------------+
| ``depth``                    | ``max_depth``                |
+------------------------------+------------------------------+
| ``balance.classes``          | ``balance_classes``          |
+------------------------------+------------------------------+
| ``score.each.iteration``     | ``score_each_iteration``     |
+------------------------------+------------------------------+
| ``class.sampling.factors``   | ``class_sampling_factors``   |
+------------------------------+------------------------------+
| ``nodesize``                 | ``min_rows``                 |
+------------------------------+------------------------------+

Deprecated DRF Parameters
~~~~~~~~~~~~~~~~~~~~~~~~~

The following parameters have been removed:

-  ``classification``: This is now automatically inferred from the
   response type. To achieve classification with a 0/1 response column,
   explicitly convert the response to a factor (``as.factor()``).
-  ``importance``: Variable importances are now computed automatically
   and displayed in the model output.
-  ``holdout.fraction``: Specifying the fraction of the training data to
   hold out for validation is no longer supported.
-  ``doGrpSplit``: The bit-set group splitting of categorical variables
   is now the default.
-  ``verbose``: Infonrmation about tree splits and extra statistics is
   now included automatically in the stdout.
-  ``oobee``: The out-of-bag error estimate is now computed
   automatically (if no validation set is specified).
-  ``stat.type``: This parameter was used for SpeeDRF, which is no
   longer supported.
-  ``type``: This parameter was used for SpeeDRF, which is no longer
   supported.

New DRF Parameters
~~~~~~~~~~~~~~~~~~

The following parameter has been added:

-  ``build_tree_one_node``: Run on a single node to use fewer CPUs.

DRF Algorithm Comparison
~~~~~~~~~~~~~~~~~~~~~~~~

+----------------+----------------+
| H2O Classic    | H2O 3.0        |
+================+================+
| ``h2o.randomFo | ``h2o.randomFo |
| rest <- functi | rest <- functi |
| on(x,``        | on(``          |
+----------------+----------------+
| ``x,``         | ``x,``         |
+----------------+----------------+
| ``y,``         | ``y,``         |
+----------------+----------------+
| ``data,``      | ``training_fra |
|                | me,``          |
+----------------+----------------+
| ``key="",``    | ``model_id,``  |
+----------------+----------------+
| ``validation,` | ``validation_f |
| `              | rame,``        |
+----------------+----------------+
| ``mtries = -1, | ``mtries = -1, |
| ``             | ``             |
+----------------+----------------+
| ``sample.rate= | ``sample_rate  |
| 2/3,``         | = 0.632,``     |
+----------------+----------------+
|                | ``build_tree_o |
|                | ne_node = FALS |
|                | E,``           |
+----------------+----------------+
| ``ntree=50``   | ``ntrees=50,`` |
+----------------+----------------+
| ``depth=20,``  | ``max_depth =  |
|                | 20,``          |
+----------------+----------------+
|                | ``min_rows = 1 |
|                | ,``            |
+----------------+----------------+
| ``nbins=20,``  | ``nbins = 20,` |
|                | `              |
+----------------+----------------+
|                | ``nbins_top_le |
|                | vel,``         |
+----------------+----------------+
|                | ``nbins_cats = |
|                | 1024,``        |
+----------------+----------------+
|                | ``binomial_dou |
|                | ble_trees = FA |
|                | LSE,``         |
+----------------+----------------+
| ``balance.clas | ``balance_clas |
| ses = FALSE,`` | ses = FALSE,`` |
+----------------+----------------+
| ``seed = -1,`` | ``seed``       |
+----------------+----------------+
| ``nodesize = 1 |                |
| ,``            |                |
+----------------+----------------+
| ``classificati |                |
| on=TRUE,``     |                |
+----------------+----------------+
| ``importance=F |                |
| ALSE,``        |                |
+----------------+----------------+
|                | ``weights_colu |
|                | mn = NULL,``   |
+----------------+----------------+
| ``nfolds=0,``  | ``nfolds = 0,` |
|                | `              |
+----------------+----------------+
|                | ``fold_column  |
|                | = NULL,``      |
+----------------+----------------+
|                | ``fold_assignm |
|                | ent = c("AUTO" |
|                | , "Random", "M |
|                | odulo"),``     |
+----------------+----------------+
|                | ``keep_cross_v |
|                | alidation_pred |
|                | ictions = FALS |
|                | E,``           |
+----------------+----------------+
|                | ``score_each_i |
|                | teration = FAL |
|                | SE,``          |
+----------------+----------------+
|                | ``stopping_rou |
|                | nds = 0,``     |
+----------------+----------------+
|                | ``stopping_met |
|                | ric = c("AUTO" |
|                | , "deviance",  |
|                | "logloss", "MS |
|                | E", "AUC", "r2 |
|                | ", "misclassif |
|                | ication"),``   |
+----------------+----------------+
|                | ``stopping_tol |
|                | erance = 0.001 |
|                | )``            |
+----------------+----------------+
| ``holdout.frac |                |
| tion = 0,``    |                |
+----------------+----------------+
| ``max.after.ba | ``max_after_ba |
| lance.size = 5 | lance_size,``  |
| ,``            |                |
+----------------+----------------+
| ``class.sampli |                |
| ng.factors = N |                |
| ULL,``         |                |
+----------------+----------------+
| ``doGrpSplit = |                |
|  TRUE,``       |                |
+----------------+----------------+
| ``verbose = FA |                |
| LSE,``         |                |
+----------------+----------------+
| ``oobee = TRUE |                |
| ,``            |                |
+----------------+----------------+
| ``stat.type =  |                |
| "ENTROPY",``   |                |
+----------------+----------------+
| ``type = "fast |                |
| ")``           |                |
+----------------+----------------+

Output
~~~~~~

The following table provides the component name in H2O, the
corresponding component name in H2O 3.0 (if supported), and the model
type (binomial, multinomial, or all). Many components are now included
in ``h2o.performance``; for more information, refer to
`(``h2o.performance``) <#h2operf>`__.

+----------------+----------------+----------------+
| H2O Classic    | H2O 3.0        | Model Type     |
+================+================+================+
| ``@model$prior |                | ``all``        |
| Distribution`` |                |                |
+----------------+----------------+----------------+
| ``@model$param | ``@allparamete | ``all``        |
| s``            | rs``           |                |
+----------------+----------------+----------------+
| ``@model$mse`` | ``@model$scori | ``all``        |
|                | ng_history``   |                |
+----------------+----------------+----------------+
| ``@model$fores | ``@model$model | ``all``        |
| t``            | _summary``     |                |
+----------------+----------------+----------------+
| ``@model$class |                | ``all``        |
| ification``    |                |                |
+----------------+----------------+----------------+
| ``@model$varim | ``@model$varia | ``all``        |
| p``            | ble_importance |                |
|                | s``            |                |
+----------------+----------------+----------------+
| ``@model$confu | ``@model$train | ``binomial``   |
| sion``         | ing_metrics@me | and            |
|                | trics$cm$table | ``multinomial` |
|                | ``             | `              |
+----------------+----------------+----------------+
| ``@model$auc`` | ``@model$train | ``binomial``   |
|                | ing_metrics@me |                |
|                | trics$AUC``    |                |
+----------------+----------------+----------------+
| ``@model$gini` | ``@model$train | ``binomial``   |
| `              | ing_metrics@me |                |
|                | trics$Gini``   |                |
+----------------+----------------+----------------+
| ``@model$best_ |                | ``binomial``   |
| cutoff``       |                |                |
+----------------+----------------+----------------+
| ``@model$F1``  | ``@model$train | ``binomial``   |
|                | ing_metrics@me |                |
|                | trics$threshol |                |
|                | ds_and_metric_ |                |
|                | scores$f1``    |                |
+----------------+----------------+----------------+
| ``@model$F2``  | ``@model$train | ``binomial``   |
|                | ing_metrics@me |                |
|                | trics$threshol |                |
|                | ds_and_metric_ |                |
|                | scores$f2``    |                |
+----------------+----------------+----------------+
| ``@model$accur | ``@model$train | ``binomial``   |
| acy``          | ing_metrics@me |                |
|                | trics$threshol |                |
|                | ds_and_metric_ |                |
|                | scores$accurac |                |
|                | y``            |                |
+----------------+----------------+----------------+
| ``@model$Error | ``@model$Error | ``binomial``   |
| ``             | ``             |                |
+----------------+----------------+----------------+
| ``@model$preci | ``@model$train | ``binomial``   |
| sion``         | ing_metrics@me |                |
|                | trics$threshol |                |
|                | ds_and_metric_ |                |
|                | scores$precisi |                |
|                | on``           |                |
+----------------+----------------+----------------+
| ``@model$recal | ``@model$train | ``binomial``   |
| l``            | ing_metrics@me |                |
|                | trics$threshol |                |
|                | ds_and_metric_ |                |
|                | scores$recall` |                |
|                | `              |                |
+----------------+----------------+----------------+
| ``@model$mcc`` | ``@model$train | ``binomial``   |
|                | ing_metrics@me |                |
|                | trics$threshol |                |
|                | ds_and_metric_ |                |
|                | scores$absolut |                |
|                | e_MCC``        |                |
+----------------+----------------+----------------+
| ``@model$max_p | currently      | ``binomial``   |
| er_class_err`` | replaced by    |                |
|                | ``@model$train |                |
|                | ing_metrics@me |                |
|                | trics$threshol |                |
|                | ds_and_metric_ |                |
|                | scores$min_per |                |
|                | _class_correct |                |
|                | ``             |                |
+----------------+----------------+----------------+

Github Users
------------

All users who pull directly from the H2O classic repo on Github should
be aware that this repo will be renamed. To retain access to the
original H2O (2.8.6.2 and prior) repository:

**The simple way**

This is the easiest way to change your local repo and is recommended for
most users.

0. Enter ``git remote -v`` to view a list of your repositories.
1. Copy the address your H2O classic repo (refer to the text in brackets
   below - your address will vary depending on your connection method):

``H2O_User-MBP:h2o H2O_User$ git remote -v   origin    https://{H2O_User@github.com}/h2oai/h2o.git (fetch)   origin    https://{H2O_User@github.com}/h2oai/h2o.git (push)``
0. Enter
``git remote set-url origin {H2O_User@github.com}:h2oai/h2o-2.git``,
where ``{H2O_User@github.com}`` represents the address copied in the
previous step.

**The more complicated way**

This method involves editing the Github config file and should only be
attempted by users who are confident enough with their knowledge of
Github to do so.

0. Enter ``vim .git/config``.
1. Look for the ``[remote "origin"]`` section:

``[remote "origin"]         url = https://H2O_User@github.com/h2oai/h2o.git         fetch = +refs/heads/*:refs/remotes/origin/*``
0. In the ``url =`` line, change ``h2o.git`` to ``h2o-2.git``. 0. Save
the changes.

The latest version of H2O is stored in the ``h2o-3`` repository. All
previous links to this repo will still work, but if you would like to
manually update your Github configuration, follow the instructions
above, replacing ``h2o-2`` with ``h2o-3``.
