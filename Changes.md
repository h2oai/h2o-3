# Recent Changes

## H2O

### 3.46.0.3 - 6/11/2024

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-3.46.0/3/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-3.46.0/3/index.html</a>

#### Bug Fix
- [[#16274]](https://github.com/h2oai/h2o-3/issues/16274) - Fixed plotting for H2O Explainabilty by resolving issue in the matplotlib wrapper.
- [[#16192]](https://github.com/h2oai/h2o-3/issues/16192) - Fixed `h2o.findSynonyms` failing if the `word` parameter is unknown to the Word2Vec model.
- [[#15947]](https://github.com/h2oai/h2o-3/issues/15947) - Fixed `skipped_columns` error caused by mismatch during the call to `parse_setup` when constructing an `H2OFrame`.

#### Improvement
- [[#16278]](https://github.com/h2oai/h2o-3/issues/16278) - Added flag to enable `use_multi_thread` automatically when using `as_data_frame`.

#### New Feature
- [[#16284]](https://github.com/h2oai/h2o-3/issues/16284) - Added support for Websockets to steam.jar.

#### Docs
- [[#16288]](https://github.com/h2oai/h2o-3/issues/16288) - Fixed GBM Python example in user guide.
- [[#16188]](https://github.com/h2oai/h2o-3/issues/16188) - Updated API-related changes page to adhere to style guide requirements.
- [[#16016]](https://github.com/h2oai/h2o-3/issues/16016) - Added examples to Python documentation for Uplift DRF.
- [[#15988]](https://github.com/h2oai/h2o-3/issues/15988) - Added examples to Python documentation for Isotonic Regression.

### 3.46.0.2 - 5/13/2024

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-3.46.0/2/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-3.46.0/2/index.html</a>

#### Bug Fix
- [[#16161]](https://github.com/h2oai/h2o-3/issues/16161) - Fixed parquet export throwing NPEs when column types are strings. 
- [[#16149]](https://github.com/h2oai/h2o-3/issues/16149) - Fixed GAM models failing with datasets of certain size by rebalancing the dataset to avoid collision.
- [[#16130]](https://github.com/h2oai/h2o-3/issues/16130) - Removed `distutils` version check to stop deprecation warnings with Python 3.12.
- [[#16026]](https://github.com/h2oai/h2o-3/issues/16026) - Removed `custom_metric_func` from ModelSelection.
- [[#15697]](https://github.com/h2oai/h2o-3/issues/15697) - Fixed MOJO failing to recognize `fold_column` and therefore  using wrong index calculated for the `offset_column`.

#### Improvement
- [[#16116]](https://github.com/h2oai/h2o-3/issues/16116) - Implemented a warning if you want to use monotone splines for GAM but don’t set `non_negative=True` that you will not get a monotone output.
- [[#16056]](https://github.com/h2oai/h2o-3/issues/16066) - Added support to XGBoost for all `gblinear` parameters.
- [[#6722]](https://github.com/h2oai/h2o-3/issues/6722) - Implemented linear constraint support to GLM toolbox. 

#### New Feature
- [[#16146]](https://github.com/h2oai/h2o-3/issues/16146) - Added ZSTD compression format support. 

#### Docs
- [[#16193]](https://github.com/h2oai/h2o-3/issues/16193) - Added mapr7.0 to the download page for the Install on Hadoop tab.
- [[#16180]](https://github.com/h2oai/h2o-3/issues/16180) - Updated Index page to adhere to style guide requirements.
- [[#16131]](https://github.com/h2oai/h2o-3/issues/16131) - Added 3.46 release blog to the user guide.

#### Security
- [[#16170]](https://github.com/h2oai/h2o-3/issues/16170) - Addressed CVE-2024-21634 by upgrading aws-java-sdk-*.
- [[#16135]](https://github.com/h2oai/h2o-3/issues/16135) - Addressed CVE-2024-29131 by upgrading commons-configuration2.

### 3.46.0.1 - 3/13/2024

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-3.46.0/1/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-3.46.0/1/index.html</a>

#### Bug Fix
- [[#16079]](https://github.com/h2oai/h2o-3/issues/16079) - Updated warning for multithreading in `H2OFrame.as_data_frame`.
- [[#16063]](https://github.com/h2oai/h2o-3/issues/16063) - Added error to explain method explaining incompatibility with UpliftDRF models.
- [[#16052]](https://github.com/h2oai/h2o-3/issues/16052) - Fixed finding best split point for UpliftDRF.
- [[#16043]](https://github.com/h2oai/h2o-3/issues/16043) - Fixed `isin()`.
- [[#16036]](https://github.com/h2oai/h2o-3/issues/16036) - Fixed `AstMatch` failing with multinode.
- [[#15978]](https://github.com/h2oai/h2o-3/issues/15978) - Fixed Deep Learning Autoencoder MOJO PredictCSV failure. 
- [[#15682]](https://github.com/h2oai/h2o-3/issues/15682) - Fixed log when `web_ip` is used.
- [[#15677]](https://github.com/h2oai/h2o-3/issues/15677) - Fixed match function only returning 1 and `no match`. 

#### Improvement
- [[#16074]](https://github.com/h2oai/h2o-3/issues/16074) - Improved `perRow` metric calculation by implementing `isGeneric()` method.
- [[#16060]](https://github.com/h2oai/h2o-3/issues/16060) - Improved log message to show that Apple silicon is not supported. 
- [[#16033]](https://github.com/h2oai/h2o-3/issues/16033) - Added optional GBLinear grid step to AutoML.
- [[#16015]](https://github.com/h2oai/h2o-3/issues/16015) - Suppressed the genmodel warnings when `verbose=False`.
- [[#15809]](https://github.com/h2oai/h2o-3/issues/15809) - Implemented ability to calculate full loglikelihood and AIC for an already-built GLM model. 
- [[#15791]](https://github.com/h2oai/h2o-3/issues/15791) - Implemented early stopping for UpliftDRF and implemented gridable parameters for UpliftDRF.
- [[#15684]](https://github.com/h2oai/h2o-3/issues/15684) - Reconfigured all logs to standard error for level `ERROR` and `FATAL`.
- [[#7325]](https://github.com/h2oai/h2o-3/issues/7325) - Implemented prediction consistency check for constrained models. 

#### New Feature
- [[#15993]](https://github.com/h2oai/h2o-3/issues/15993) - Added `custom_metric` as a hyperparameter for grid search.
- [[#15967]](https://github.com/h2oai/h2o-3/issues/15967) - Added custom metrics for XGBoost.
- [[#15858]](https://github.com/h2oai/h2o-3/issues/15858) - Implemented consistent mechanism that protects frames and their vecs from autodeletion.
- [[#15683]](https://github.com/h2oai/h2o-3/issues/15683) - Introduced a warning if `web_ip` is not specified that H2O Rest API is listening on all interfaces.
- [[#15654]](https://github.com/h2oai/h2o-3/issues/15654) - Introduced MLFlow flavors for working with H2O-3 MOJOs and POJOs instead of binary models.
- [[#6573]](https://github.com/h2oai/h2o-3/issues/6573) - Implemented machine learning interpretability support for UpliftDRF by allowing Uplift models to access partial dependences plots and variable importance.

#### Docs
- [[#16004]](https://github.com/h2oai/h2o-3/issues/16004) - Updated copyright year in user guide and Python guide.
- [[#16000]](https://github.com/h2oai/h2o-3/issues/16000) - Fixed Decision Tree Python example.
- [[#15930]](https://github.com/h2oai/h2o-3/issues/15930) - Fixed GLM Python example.
- [[#15915]](https://github.com/h2oai/h2o-3/issues/15915) - Added examples to Python documentation for Model Selection algorithm.
- [[#15798]](https://github.com/h2oai/h2o-3/issues/15798) - Added examples to Python documentation for GAM algorithm.
- [[#15709]](https://github.com/h2oai/h2o-3/issues/15709) - Added examples to Python documentation for ANOVA GLM algorithm.

#### Security
- [[#16102]](https://github.com/h2oai/h2o-3/issues/16102) - Addressed SNYK-JAVA-COMNIMBUSDS-6247633 by upgrading nimbus-jose-jwt to 9.37.2.
- [[#16093]](https://github.com/h2oai/h2o-3/issues/16093) - Addressed CVE-2024-26308 by upgrading org.apache.commons:commons-compress.
- [[#16067]](https://github.com/h2oai/h2o-3/issues/16067) - Addressed CVE-2023-35116 in the h2o-steam.jar. 
- [[#15972]](https://github.com/h2oai/h2o-3/issues/15972) - Addressed CVE-2023-6038 by adding option to filter file system for reading and writing.
- [[#15971]](https://github.com/h2oai/h2o-3/issues/15971) - Addressed CVE-2023-6016 by introducing Java property that disables automatic import of POJOs during `import_mojo` or `upload_mojo`.

### 3.44.0.3 - 12/20/2023

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-3.44.0/3/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-3.44.0/3/index.html</a>

#### Bug Fix
- [[#15958]](https://github.com/h2oai/h2o-3/issues/15958) - Fixed maximum likelihood dispersion estimation for GLM tweedie family producing the wrong result for a specific dataset.
- [[#15936]](https://github.com/h2oai/h2o-3/issues/15936) - Added data frame transformations using polars since datatable cannot be installed on Python 3.10+. 
- [[#15894]](https://github.com/h2oai/h2o-3/issues/15894) - Ensured that the functions that are supposed to be exported in the R package are exported.
- [[#15891]](https://github.com/h2oai/h2o-3/issues/15891) - Corrected sign in AIC calculation to fix problem with tweedie dispersion parameter estimation, AIC, and loglikelihood.
- [[#15887]](https://github.com/h2oai/h2o-3/issues/15887) - Allowed Python H2OFrame constructor to accept an existing H2OFrame.
- [[#6725]](https://github.com/h2oai/h2o-3/issues/6725) - Fixed LoggerFactory slf4j related regression. 

#### Improvement
- [[#15937]](https://github.com/h2oai/h2o-3/issues/15937) - Exposed `gainslift_bins` parameter for Deep Learning, GAM, GLM, and Stacked Ensemble algorithms.
- [[#15916]](https://github.com/h2oai/h2o-3/issues/15916) - Sped up computation of Friedman-Popescu’s H statistic.

#### New Feature
- [[#15927]](https://github.com/h2oai/h2o-3/issues/15927) - Added anomaly score metric to be used as a `sort_by` metric when sorting grid model performances for Isolation Forest with grid search.
- [[#15780]](https://github.com/h2oai/h2o-3/issues/15780) - Added `weak_learner_params` parameter for AdaBoost.
- [[#15779]](https://github.com/h2oai/h2o-3/issues/15779) - Added `weak_learner="deep_learning"` option for AdaBoost.
- [[#7118]](https://github.com/h2oai/h2o-3/issues/7118) - Implemented scoring and scoring history for Extended Isolation Forest by adding `score_each_iteration` and `score_tree_interval`. 

#### Docs
- [[#15817]](https://github.com/h2oai/h2o-3/issues/15817) - Improved default threshold API and documentation for binomial classification.

#### Security
- [[#15754]](https://github.com/h2oai/h2o-3/issues/15754) - Addressed CVE-2022-21230 by replacing nanohttpd.

### 3.44.0.2 - 11/8/2023

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-3.44.0/2/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-3.44.0/2/index.html</a>

#### Bug Fix
- [[#15906]](https://github.com/h2oai/h2o-3/issues/15906) - Fixed `learning_curve_plot` for CoxPH with specified metric = 'loglik'.
- [[#15889]](https://github.com/h2oai/h2o-3/issues/15889) - Fixed inability to call `thresholds_and_metric_scores()` with binomial models and metrics.
- [[#15861]](https://github.com/h2oai/h2o-3/issues/15861) - Fixed the warning message that caused `as_data_frame` to fail due to not having datatable installed. 
- [[#15860]](https://github.com/h2oai/h2o-3/issues/15860) - Fixed `force_col_type` not working with `skipped_columns` when parsing parquet files.
- [[#15832]](https://github.com/h2oai/h2o-3/issues/15832) - Fixed UpliftDRF MOJO API and updated the documentation. 
- [[#15761]](https://github.com/h2oai/h2o-3/issues/15761) - Fixed `relevel_by_frequency` resetting the values of the column.

#### Improvement
- [[#15893]](https://github.com/h2oai/h2o-3/issues/15893) - Renamed the `data` parameter of the `partial_plot` function to `frame`.

#### Docs
- [[#15881]](https://github.com/h2oai/h2o-3/issues/15881) - Added security note that Kubernetes images don’t apply security settings by default.
- [[#15851]](https://github.com/h2oai/h2o-3/issues/15851) - Added the 3.44 major release blog to the user guide.
- [[#15842]](https://github.com/h2oai/h2o-3/issues/15842) - Introduced *Known Bug* section to the release notes. 
- [[#15840]](https://github.com/h2oai/h2o-3/issues/15840) -  Fixed the release notes UI not loading by making them smaller by putting all release notes prior to 3.28.0.1 into a separate file.
- [[#6570]](https://github.com/h2oai/h2o-3/issues/6570) - Added information on the Friedman and Popescu H Statistic to XGBoost and GBM.

#### Security
- [[#15865]](https://github.com/h2oai/h2o-3/issues/15865) - Upgraded org.python.jython to CWE-416 of com.github.jnr:jnr-posix.

### 3.44.0.1 - 10/16/2023

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-3.44.0/1/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-3.44.0/1/index.html</a>


#### Bug Fix
- [[#15743]](https://github.com/h2oai/h2o-3/issues/15743) - Fixed`shap_summary_plot` for H2O Explainability Interface failing when one column was full of zeroes or NaN values.
- [[#15669]](https://github.com/h2oai/h2o-3/issues/15669) - Fixed R package to ensure it downloads the fixed version of H2O.
- [[#15651]](https://github.com/h2oai/h2o-3/issues/15651) - Upgraded the minimal supported version of `ggplot2` to 3.3.0 to remove the deprecated dot-dot notation.

#### Improvement
- [[#15801]](https://github.com/h2oai/h2o-3/issues/15801) - Updated Friedman and Popescu’s H statistic calculation to include missing values support.
- [[#15741]](https://github.com/h2oai/h2o-3/issues/15741) - Implemented ability for force column types during parsing.
- [[#15713]](https://github.com/h2oai/h2o-3/issues/15713) - Improved the default threshold API for binomial classification.
- [[#15582]](https://github.com/h2oai/h2o-3/issues/15582) - Renamed prediction table header for UpliftDRF to be more user-friendly. 
- [[#12678]](https://github.com/h2oai/h2o-3/issues/12678) - Added check to `mojo_predict_df` to look for a valid R dataframe.
- [[#7079]](https://github.com/h2oai/h2o-3/issues/7079) - Added verbosity to H2O initialization. `h2oconn.clust.show_status()` is now guarded and will only be shown when `verbose=True` during initialization.
- [[#6768]](https://github.com/h2oai/h2o-3/issues/6768) - Enabled categorical features for single decision tree. 

#### New Feature
- [[#15773]](https://github.com/h2oai/h2o-3/issues/15773) - Implemented `make_metrics` with custom AUUC thresholds for UpliftDRF.
- [[#15565]](https://github.com/h2oai/h2o-3/issues/15565) - Implemented custom metric for AutoML.
- [[#15559]](https://github.com/h2oai/h2o-3/issues/15559) - Implemented custom metric for Stacked Ensemble.
- [[#15556]](https://github.com/h2oai/h2o-3/issues/15556) - Implemented MOJO support for UpliftDRF.
- [[#15535]](https://github.com/h2oai/h2o-3/issues/15535) - Implemented Python 3.10 and 3.11 support.
- [[#6784]](https://github.com/h2oai/h2o-3/issues/6784) - Implemented custom metric for Deep Learning.
- [[#6783]](https://github.com/h2oai/h2o-3/issues/6783) - Implemented custom metric functionalities and the ATE, ATT, and ATC metrics for UpliftDRF.
- [[#6779]](https://github.com/h2oai/h2o-3/issues/6779) - Implemented custom metric for leaderboard.
- [[#6723]](https://github.com/h2oai/h2o-3/issues/6723) - Implemented new AdaBoost algorithm for binary classification.
- [[#6698]](https://github.com/h2oai/h2o-3/issues/6698) - Implemented Shapley values support for ensemble models.

#### Security
- [[#15815]](https://github.com/h2oai/h2o-3/issues/15815) - Addressed CVE-2023-36478 by upgrading Jetty server.
- [[#15805]](https://github.com/h2oai/h2o-3/issues/15805) - Addressed CVE-2023-42503 by upgrading commons-compress to 1.24.0 in Standalone Jars.
- [[#15802]](https://github.com/h2oai/h2o-3/issues/15802) - Addressed CVE-2023-39410 by upgrading org.apache.avro:avro to 1.11.3.
- [[#15799]](https://github.com/h2oai/h2o-3/issues/15799) - Addressed CVE-2023-43642 by upgrading snappy-java in Standalone Jars to 1.1.10.5.
- [[#15759]](https://github.com/h2oai/h2o-3/issues/15759) - Addressed CVE-202-13949, CVE-2019-0205, CVE-2018-1320, and CVE-2018-11798 by excluding org.apache.thrift:libthrift from dependencies of Main Standalone Jar.
- [[#15757]](https://github.com/h2oai/h2o-3/issues/15757) - Addressed CVE-2020-29582 and CVE-2022-24329 by upgrading org.jetbrains.kotlin:kotlin-stdlib to 1.6.21 in Main and Steam Standalone Jars.
- [[#15755]](https://github.com/h2oai/h2o-3/issues/15755) - Addressed CVE-2023-3635 by upgrading com.squareup.okio:okio to 3.5.0 in Main and Steam Standalone Jars.
- [[#15752]](https://github.com/h2oai/h2o-3/issues/15752) - Addressed CVE-2023-34455, CVE-2023-34454, and CVE-2023-34453 by upgrading snappy-java to 1.1.10.3 in Main and Steam Standalone Jars.
- [[#15750]](https://github.com/h2oai/h2o-3/issues/15750) - Addressed CVE-2023-1370 by upgrading json-smart to 2.4.10 in Main standalone Jar.
- [[#15746]](https://github.com/h2oai/h2o-3/issues/15746) - Addressed CVE-2023-1436, CVE-2022-40149, CVE-2022-40150, CVE-2022-45685, and CVE-2022-45693 by upgrading org.codehaus.jettison:jettison to 1.5.4 in Main Standalone Jar.
- [[#15744]](https://github.com/h2oai/h2o-3/issues/15744) - Addressed CVE-2017-12197 by upgrading libpam4j to 1.11.
- [[#15706]](https://github.com/h2oai/h2o-3/issues/15706) - Addressed CVE-2023-40167 and CVE-2023-36479 by upgrading the Jetty server.
- [[#15470]](https://github.com/h2oai/h2o-3/issues/15470) - Upgraded Hadoop Libraries in Main Standalone Jar to address high and critical vulnerabilities.

#### *Known Bug*
*(The list of bugs introduced by the changes in this release)*

- [[#15832]](https://github.com/h2oai/h2o-3/issues/15832) - Broken Python and R API for UpliftDRF MOJO models. *Resolved in 3.44.0.2.*

### 3.42.0.4 - 10/3/2023

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-3.42.0/4/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-3.42.0/4/index.html</a>

#### Bug Fix
- [[#15729]](https://github.com/h2oai/h2o-3/issues/15729) - Implemented multi-thread `as_data_frame` by using Datatable to speedup the conversion process.
- [[#15643]](https://github.com/h2oai/h2o-3/issues/15643) - Fixed validation of `include_explanation` and `exclude_explanation` parameters

#### Improvement
- [[#15719]](https://github.com/h2oai/h2o-3/issues/15719) - Implemented warnings in python and R for accessing `model.negative_log_likelihood()`
- [[#13859]](https://github.com/h2oai/h2o-3/issues/13859) - Improved K-Means testing.

#### New Feature
- [[#15727]](https://github.com/h2oai/h2o-3/issues/15727) - Implemented new `write_checksum` parameter that allows you to disable default Hadoop Parquet writer systematically writing a `.crc` checksum file for each written data file.

#### Security
- [[#15766]](https://github.com/h2oai/h2o-3/issues/15766) - Addressed CVE-2023-40167 and CVE-2023-36479 in Steam Jar

### 3.42.0.3 - 8/22/2023

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-3.42.0/3/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-3.42.0/3/index.html</a>

#### Bug Fix
- [[#15679]](https://github.com/h2oai/h2o-3/issues/15679) - Fixed GBM invalid tree index feature interaction.
- [[#15666]](https://github.com/h2oai/h2o-3/issues/15666) - Updated test to showcase GBM checkpointing.
- [[#6605]](https://github.com/h2oai/h2o-3/issues/6605) - Fixed `h2o.feature_interaction` failing on cross-validation models with early stopping. 

#### Improvement
- [[#6707]](https://github.com/h2oai/h2o-3/issues/6707) - Added extended message to `h2o.init()` to help users get around version mismatch error.

#### Docs
- [[#15694]](https://github.com/h2oai/h2o-3/issues/15694) - Added `custom_metric_func` and `upload_custom_metric` to GLM.
- [[#15680]](https://github.com/h2oai/h2o-3/issues/15680) - Added security installation disclaimer in documentation and on the download page.
- [[#15598]](https://github.com/h2oai/h2o-3/issues/15598) - Updated `import_file` description and added Google Storage support note.

#### Security
- [[#15687]](https://github.com/h2oai/h2o-3/issues/15687) - Replaced dependencies on no.priv.garshol.duke:duke:1.2 by extracting string comparators from Duke library.

### 3.42.0.2 - 7/25/2023

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-3.42.0/2/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-3.42.0/2/index.html</a>

#### Bug Fix
- [[#15637]](https://github.com/h2oai/h2o-3/issues/15637) - Fixed AUCPR plot assigning incorrect values to the variable recalls and precisions. 
- [[#6545]](https://github.com/h2oai/h2o-3/issues/6545) - Fixed out of memory error on multi-node sorting stage or sorted frame generation process.

#### New Feature
- [[#15614]](https://github.com/h2oai/h2o-3/issues/15614) - Enabled H2OFrame to pandas DataFrame using multi-thread from datatable to speed-up the conversion process.
- [[#15597]](https://github.com/h2oai/h2o-3/issues/15597) - Added support for EMR 6.10.

#### Engineering Task
- [[#15626]](https://github.com/h2oai/h2o-3/issues/15626) - Updated Jira links in H2O Flow UI with GH issue links.

#### Docs
- [[#15629]](https://github.com/h2oai/h2o-3/issues/15629) - Fixed typo on Hadoop introduction page.
- [[#15606]](https://github.com/h2oai/h2o-3/issues/15606) - Updated major release blog for user guide.
- [[#15580]](https://github.com/h2oai/h2o-3/issues/15580) - Added information on UniformRobust method for `histogram_type` and created an accompanying blog post. 
- [[#15563]](https://github.com/h2oai/h2o-3/issues/15563) - Updated out of date copyright year in user guide and python guide.
- [[#6574]](https://github.com/h2oai/h2o-3/issues/6574) - Added a warning to Infogram user guide that it should not be used to remove correlated columns.
- [[#6554]](https://github.com/h2oai/h2o-3/issues/6554) - Updated `nfolds` parameter description for AutoML in Python guide.

#### Security
- [[#15634]](https://github.com/h2oai/h2o-3/issues/15634) - Addressed CVE-2019-10086 by upgrading MOJO2 lib.

### 3.42.0.1 - 6/21/2023

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-3.42.0/1/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-3.42.0/1/index.html</a>

#### Bug Fix
- [[#15423]](https://github.com/h2oai/h2o-3/issues/15423) - Fixed Infogram cross-validation with weights.
- [[#15482]](https://github.com/h2oai/h2o-3/issues/15482) - Updated R package maintainer.
- [[#15461]](https://github.com/h2oai/h2o-3/issues/15461) - Fixed leaks in GLM’s Negative Binomial estimation.

#### Improvement
- [[#6843]](https://github.com/h2oai/h2o-3/issues/6843) - Changed `warning` tag to `info` tag when weights are not provided during validation/test dataset scoring when weights are present in training. 
- [[#6828]](https://github.com/h2oai/h2o-3/issues/6828) - Removed support for Python 2.7 and 3.5.
- [[#6813]](https://github.com/h2oai/h2o-3/issues/6813) - Upgraded the default parquet library to 1.12.3 for standalone jar.
- [[#7630]](https://github.com/h2oai/h2o-3/issues/7630) - Upgraded XGBoost to version 1.6.1.

#### New Feature
- [[#6548]](https://github.com/h2oai/h2o-3/issues/6548) - Implemented AIC metric for all GLM model families.
- [[#6880]](https://github.com/h2oai/h2o-3/issues/6880) - Implemented Tweedie variance power maximum likelihood estimation for GLM.
- [[#6943]](https://github.com/h2oai/h2o-3/issues/6943) - Added ability to convert H2OAssembly to a MOJO2 artifact.
- [[#7008]](https://github.com/h2oai/h2o-3/issues/7008) - Implemented new Decision Tree algorithm.

#### Docs
- [[#15474]](https://github.com/h2oai/h2o-3/issues/15474) - Added link to AutoML Wave app from AutoML user guide.
- [[#15550]](https://github.com/h2oai/h2o-3/issues/15550) - Added documentation on H2OAssembly to MOJO 2 export functionality.
- [[#15602]](https://github.com/h2oai/h2o-3/issues/15602) - Added algorithm page in user guide for new Decision Tree algorithm.
- [[#15529]](https://github.com/h2oai/h2o-3/issues/15529) - Added AIC metric support for all GLM families to GLM user guide page and GLM booklet.
- [[#15466]](https://github.com/h2oai/h2o-3/issues/15466) - Updated authors and editors for GLM booklet.
- [[#6884]](https://github.com/h2oai/h2o-3/issues/6884) - Added documentation on Tweedie variance power maximum likelihood estimation to GLM booklet and user guide.
- [[#7200]](https://github.com/h2oai/h2o-3/issues/7200) - Improved user guide documentation for Generalized Additive Models algorithm.

#### Security
- [[#15594]](https://github.com/h2oai/h2o-3/issues/15594) - Addressed CVE-2023-2976 in h2o-steam.jar.
- [[#15548]](https://github.com/h2oai/h2o-3/issues/15548) - Addressed CVE-2020-29582 in h2o-steam.jar.
- [[#15546]](https://github.com/h2oai/h2o-3/issues/15546) - Addressed CVE-2023-26048 and CVE-2023-26049 by upgrading Jetty for minimal and steam jar.
- [[#15540]](https://github.com/h2oai/h2o-3/issues/15540) - Addressed PRISMA-2023-0067 in h2o-steam.jar.
- [[#6827]](https://github.com/h2oai/h2o-3/issues/6827) - Addressed CVE-2023-1436, CVE-2022-45693, CVE-2022-45685, and CVE-2022-40150 by upgrading org.codehaus.jettison:jettison in h2o-steam.jar.

### Kurka (3.40.0.4) - 4/28/2023

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zz_kurka/4/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zz_kurka/4/index.html</a>

#### Bug Fix
- [[#6758]](https://github.com/h2oai/h2o-3/issues/6758) - Fixed the deprecation warning thrown for Python 2.7 and 3.5.

#### Improvement
- [[#6756]](https://github.com/h2oai/h2o-3/issues/6756) - Added official support for Python 3.9.

#### Docs
- [[#6759]](https://github.com/h2oai/h2o-3/issues/6759) - Removed mention of support for Python 2.7 and Python 3.5 from documentation.
- [[#7600]](https://github.com/h2oai/h2o-3/issues/7600) - Reorganized supervised and unsupervised algorithm parameters by algorithm-specific, common, and shared-tree (for tree-based algorithms). Updated parameter descriptions for all supervised and unsupervised algorithms. Shifted all shared GLM family parameters to the GLM algorithm page.

#### Security
- [[#6732]](https://github.com/h2oai/h2o-3/issues/6732) - Addressed CVE-2023-1370 by removing the vulnerability from h2o-steam.jar.

### Kurka (3.40.0.3) - 4/4/2023

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zz_kurka/3/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zz_kurka/3/index.html</a>

#### Improvement
- [[#6763]](https://github.com/h2oai/h2o-3/issues/6763) - Added GAM Knot Locations to Model Output.
- [[#6764]](https://github.com/h2oai/h2o-3/issues/6764) - Addressed CVE-2014-125087 in h2o-steam.jar

#### Engineering Story
- [[#6767]](https://github.com/h2oai/h2o-3/issues/6767) - Disabled execution of tests in client mode.
- [[#6772]](https://github.com/h2oai/h2o-3/issues/6772) - Deprecated support for Python 2.7 and 3.5.

#### Docs
- [[#6773]](https://github.com/h2oai/h2o-3/issues/6773) - Introduced a page describing MOJO capabilities.
- [[#6790]](https://github.com/h2oai/h2o-3/issues/6790) - Updated the DRF documentation page to reflect what dataset is used to calculate the model metric.
- [[#6793]](https://github.com/h2oai/h2o-3/issues/6793) - Updated and rearranged the hyper-parameter list in the Grid Search documentation page.

### Kurka (3.40.0.2) - 3/9/2023

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zz_kurka/2/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zz_kurka/2/index.html</a>

#### Bug Fix
- [[#6818]](https://github.com/h2oai/h2o-3/issues/6818) - Fixed dependency on numpy in Fairness-related code.
- [[#6819]](https://github.com/h2oai/h2o-3/issues/6819) - Added ability to debug GBM reproducibility by looking at tree structure with `equal_gbm_model_tree_structure`.

#### Improvement
- [[#6995]](https://github.com/h2oai/h2o-3/issues/6995) - Fixed the deviance computation for GBM Poisson distribution.

#### New Feature
- [[#6777]](https://github.com/h2oai/h2o-3/issues/6777) - Added `save_plot_path` parameter for Fairness plotting allowing you to save plots.

#### Task
- [[#6538]](https://github.com/h2oai/h2o-3/issues/6538) - Implemented incremental MaxRSweep without using sweep vectors.
- [[#6799]](https://github.com/h2oai/h2o-3/issues/6799) - Removed duplicate predictors for ModelSelection’s MaxRSweep.

#### Engineering Story
- [[#6776]](https://github.com/h2oai/h2o-3/issues/6776) - Pointed MLOps integration to internal.dedicated environment.

#### Docs
- [[#6501]](https://github.com/h2oai/h2o-3/issues/6501) - Added warning that `max_runtime_secs` cannot always produce reproducible models.
- [[#6503]](https://github.com/h2oai/h2o-3/issues/6503) - Added example for how to save a file as a parquet.
- [[#6811]](https://github.com/h2oai/h2o-3/issues/6811) - Added example for how to connect to an H2O cluster by name.
- [[#6887]](https://github.com/h2oai/h2o-3/issues/6887) - Added information on the implementation of the `eval_metric` for XGBoost.

### Kurka (3.40.0.1) - 2/8/2023

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zz_kurka/1/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zz_kurka/1/index.html</a>

#### Bug Fix
- [[#6845]](https://github.com/h2oai/h2o-3/issues/6845) - Improved GLM negative binomial calculation time.
- [[#6882]](https://github.com/h2oai/h2o-3/issues/6882) - Cleaned up COLLATE field in the description of the R package by allowing Roxygen2 to generate the COLLATE field.
- [[#6891]](https://github.com/h2oai/h2o-3/issues/6891) - Changed the exceptions in Stacked Ensembles checks to ModelBuilder warnings.
- [[#7090]](https://github.com/h2oai/h2o-3/issues/7090) - Fixed GLM ignoring time budget when trained using cross-validation in AutoML.
- [[#7132]](https://github.com/h2oai/h2o-3/issues/7132) - Fixed incorrect actual `ntrees` value reported in tree-based models.

#### Improvement
- [[#6805]](https://github.com/h2oai/h2o-3/issues/6805) - Increased speed of XGBoost scoring on wide datasets.
- [[#6864]](https://github.com/h2oai/h2o-3/issues/6864) - Updated error message for when a user specifies the wrong cluster when connecting to a running H2O instance.
- [[#6886]](https://github.com/h2oai/h2o-3/issues/6886) - Improved memory usage in creation of parse-response for wide datasets.
- [[#6893]](https://github.com/h2oai/h2o-3/issues/6893) - Increased testing speed by adding ability to train XGBoost cross-validation models concurrently on the same GPU. 
- [[#6900]](https://github.com/h2oai/h2o-3/issues/6900) - Added ability to score `eval_metric` on validation datasets for XGBoost.
- [[#6901]](https://github.com/h2oai/h2o-3/issues/6901) - Added notebook demonstrating `eval_metric` for XGBoost.
- [[#6902]](https://github.com/h2oai/h2o-3/issues/6902) - Increased XGBoost model training speed by disabling H2O scoring to rely solely on `eval_metric`. 
- [[#6910]](https://github.com/h2oai/h2o-3/issues/6910) - Updated to Java 17 from Java 11/openjdk in H2O docker images.
- [[#7294]](https://github.com/h2oai/h2o-3/issues/7294) - Updated warning message for when H2O version is outdated.
- [[#7598]](https://github.com/h2oai/h2o-3/issues/7598) - Introduced a better format for storing default, input, and actual parameters in H2O model objects for R by using `@params` slots.
- [[#7835]](https://github.com/h2oai/h2o-3/issues/7835) - Added `model_summary` to Stacked Ensembles.
- [[#7980]](https://github.com/h2oai/h2o-3/issues/7980) - Moved StackedEnsembleModel::checkAndInheritModelProperties to StackedEnsemble class.

#### New Feature
- [[#6858]](https://github.com/h2oai/h2o-3/issues/6858) - Added ability to publish models to MLOps via Python API.
- [[#7009]](https://github.com/h2oai/h2o-3/issues/7009) - Added ability to grid over Infogram.
- [[#7044]](https://github.com/h2oai/h2o-3/issues/7044) - Implemented Regression Influence Diagnostics for GLM.
- [[#7045]](https://github.com/h2oai/h2o-3/issues/7045) - Enhanced GBM procedures to output which records are used for each tree.
- [[#7537]](https://github.com/h2oai/h2o-3/issues/7537) - Added learning curve plot to H2O’s Explainability.

#### Task
- [[#6802]](https://github.com/h2oai/h2o-3/issues/6802) - Added `negative_log_likelihood` and `average_objective` accessor functions in R and Python for GLM.
- [[#7088]](https://github.com/h2oai/h2o-3/issues/7088) - Limited the number of iterations when training the final GLM model after cross-validation.

#### Technical Task
- [[#6898]](https://github.com/h2oai/h2o-3/issues/6898) - Added support for scoring `eval_metric` on a validation set for external XGBoost cluster.
- [[#6899]](https://github.com/h2oai/h2o-3/issues/6899) - Added support for scoring `eval_metric` on a validation set for internal XGBoost cluster.
- [[#7012]](https://github.com/h2oai/h2o-3/issues/7012) - Implemented GLM dispersion estimation parameter using maximum likelihood method for the negative binomial family.

#### Docs
- [[#6820]](https://github.com/h2oai/h2o-3/issues/6820) - Highlighted information about how rebalancing makes reproducibility impossible.
- [[#6815]](https://github.com/h2oai/h2o-3/issues/6815) - Added documentation on the `negative_log_likelihood` and `average_objective` accessor functions.
- [[#6816]](https://github.com/h2oai/h2o-3/issues/6816) - Added information on GLM dispersion estimation using maximum likelihood method for the negative binomial family.
- [[#6821]](https://github.com/h2oai/h2o-3/issues/6821) - Added documentation on Regression Influence Diagnostics for GLM.
- [[#6803]](https://github.com/h2oai/h2o-3/issues/6803) - Fixed non-functional data paths in code examples throughout the user guide.
- [[#6804]](https://github.com/h2oai/h2o-3/issues/6804) - Added information on the `row_to_tree_assignment` function.
- [[#6807]](https://github.com/h2oai/h2o-3/issues/6807) - Added documentation on using H2O with Apple M1 chip.
- [[#6808]](https://github.com/h2oai/h2o-3/issues/6808) - Added information on `init` parameter being skipped due to `estimate_k=True` for K-Means.

### Zygmund (3.38.0.4) - 1/5/2023

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zygmund/4/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zygmund/4/index.html</a>

#### Bug Fix
- [[#6851]](https://github.com/h2oai/h2o-3/issues/6851) - Fixed error in SHAP values report for DRF. 
- [[#6865]](https://github.com/h2oai/h2o-3/issues/6865) - Fixed a ModelSelection replacement error stopping too early and implemented incremental forward step and incremental replacement step for numerical predictors.

#### Task
- [[#6852]](https://github.com/h2oai/h2o-3/issues/6852) - Resolved hyperparameters amongst the algorithms.
- [[#6857]](https://github.com/h2oai/h2o-3/issues/6857) - Removed redundant predictors found in `mode=“backward”` for ModelSelection.

#### Engineering Story
- [[#6846]](https://github.com/h2oai/h2o-3/issues/6846) - Renamed the docker image `h2o-steam-k8s` to `h2o-open-source-k8s-minimal`.

#### Docs
- [[#6800]](https://github.com/h2oai/h2o-3/issues/6800) - Updated download page by adding options for steam jar and python client without h2o backend.
- [[#6849]](https://github.com/h2oai/h2o-3/issues/6849) - Fixed log likelihood of negative binomial for GLM.
- [[#6855]](https://github.com/h2oai/h2o-3/issues/6855) - Added how users can force an unsupported Java version.
- [[#6856]](https://github.com/h2oai/h2o-3/issues/6856) - Fixed broken links on the H2O Release page.
- [[#6860]](https://github.com/h2oai/h2o-3/issues/6860) - Added information on how Isolation Forest and Extended Isolation Forest handle missing values.
- [[#6862]](https://github.com/h2oai/h2o-3/issues/6862) - Fixed typos and made examples work on performance-and-prediction.html.
- [[#6863]](https://github.com/h2oai/h2o-3/issues/6863) - Removed outdated roadmap from Readme file.

#### Security
- [[#6794]](https://github.com/h2oai/h2o-3/issues/6794) - Addressed CVE-2022-3509 by upgrading `google-cloud-storage`.

### Zygmund (3.38.0.3) - 11/23/2022

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zygmund/3/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zygmund/3/index.html</a>

#### Bug Fix
- [[#6877]](https://github.com/h2oai/h2o-3/issues/6877) - Enforced DkvClassLoader while accessing Python resources through JythonCFuncLoader.
- [[#6878]](https://github.com/h2oai/h2o-3/issues/6878) - Closed open file descriptors from H2OConnection.
- [[#6871]](https://github.com/h2oai/h2o-3/issues/6871) - Fixed incorrect value indicator for a partial dependence plot for its current row.
- [[#6873]](https://github.com/h2oai/h2o-3/issues/6873) - Fixed GBM model with `interaction_constraints` only building single-depth trees.
- [[#6897]](https://github.com/h2oai/h2o-3/issues/6897) - Fixed slow estimator validation when training model with wide datasets.
- [[#6907]](https://github.com/h2oai/h2o-3/issues/6907) - Fixed GAM failure when `numknots=2` for I-spline.


#### Task
- [[#6896]](https://github.com/h2oai/h2o-3/issues/6896) - Ensured non-negative will not overwrite `splines_non_negative` for GAM I-spline.
- [[#6921]](https://github.com/h2oai/h2o-3/issues/6921) - Implemented p-value calculation for GLM with regularization.
- [[#6925]](https://github.com/h2oai/h2o-3/issues/6925) - Verified the minimum number of knots each spline type can support for GAM.
- [[#6926]](https://github.com/h2oai/h2o-3/issues/6926) - Implemented normal (non-monotonic) splines that can support any degrees.


#### Docs
- [[#6874]](https://github.com/h2oai/h2o-3/issues/6874) - Updated `compute_p_value` documentation for GLM and GAM to reflect that p-values and z-values can now be computed with regularization.
- [[#6875]](https://github.com/h2oai/h2o-3/issues/6875) - Documented GAM M-splines.
- [[#6876]](https://github.com/h2oai/h2o-3/issues/6876) - Updated site logo, favicon, and color scheme to reflect H2O’s brand kit.
- [[#6870]](https://github.com/h2oai/h2o-3/issues/6870) - Updated booklet links for GBM, GLM, and Deep Learning on their respective algorithm pages.
- [[#6881]](https://github.com/h2oai/h2o-3/issues/6881) - Fixed typo in Model Selection for `build_glm_model` parameter.
- [[#6885]](https://github.com/h2oai/h2o-3/issues/6885) - Updated links in the provided bibliography in the FAQ.
- [[#6894]](https://github.com/h2oai/h2o-3/issues/6894) - Removed Sparkling Water booklet link from the download page.
- [[#6904]](https://github.com/h2oai/h2o-3/issues/6904) - Added optional Python plotting requirement `matplotlib` to the download page.

### Zygmund (3.38.0.2) - 10/27/2022

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zygmund/2/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zygmund/2/index.html</a>

#### Bug Fix

- [[#6895]](https://github.com/h2oai/h2o-3/issues/6895) - Fixed H2ODeepLearningEstimator `autoencoder` not working without `y` value.
- [[#6911]](https://github.com/h2oai/h2o-3/issues/6911) - Added `libgomp` into docker images thus enabling XGBoost multithreading.
- [[#6919]](https://github.com/h2oai/h2o-3/issues/6919) - Stopped throwing warning about jobs not having proper model types when models weren’t even trained.
- [[#6928]](https://github.com/h2oai/h2o-3/issues/6928) - Fixed cross validation failure for concurrent sorting.
- [[#6930]](https://github.com/h2oai/h2o-3/issues/6930) - Enabled parallelism in cross validation for Isotonic Regression.

#### Task

- [[#6917]](https://github.com/h2oai/h2o-3/issues/6917) - Enabled GAM I-spline to support increasing and decreasing functions.
- [[#6925]](https://github.com/h2oai/h2o-3/issues/6925) - Updated the number of knots required for GAM I-splines to be >=2.
- [[#6949]](https://github.com/h2oai/h2o-3/issues/6949) -  Improved ModelSelection’s `mode=“maxrsweep”` runtime.

#### Docs

- [[#6890]](https://github.com/h2oai/h2o-3/issues/6890) - Added information on ModelSelection’s new `build_glm_model` parameter for `mode=“maxrsweep”`.
- [[#6903]](https://github.com/h2oai/h2o-3/issues/6903) - Fixed incorrect header case on ModelSelection and Cox Proportional Hazards algorithm pages in the user guide.
- [[#6913]](https://github.com/h2oai/h2o-3/issues/6913) - Added an example to Variable Inflation Factors in the user guide.
- [[#6917]](https://github.com/h2oai/h2o-3/issues/6917) - Fixed broken links on the “Welcome to H2O-3” page of the user guide.
- [[#6948]](https://github.com/h2oai/h2o-3/issues/6948) - Added model explainability for plotting SHAP to the “Performance and Prediction” page of the user guide.
- [[#7442]](https://github.com/h2oai/h2o-3/issues/7442) - Added examples for `varsplits()` and `feature_frequencies()` to Python documentation.

#### Security

- [[#6889]](https://github.com/h2oai/h2o-3/issues/6889) - Addressed CVE-2022-42003 and CVE-2022-42889 security issues through Library upgrades.


### Zygmund (3.38.0.1) - 9/19/2022

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zygmund/1/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zygmund/1/index.html</a>

#### Bug Fix

- [[#6937]](https://github.com/h2oai/h2o-3/issues/6937) - Fixed the sorting of `h2o.make_leaderboard`.
- [[#6940]](https://github.com/h2oai/h2o-3/issues/6940) - Fixed H2O dependencies overriding Jetty implementation.
- [[#6951]](https://github.com/h2oai/h2o-3/issues/6951) - Fixed Flow’s export Frame throwing an NPE because it doesn’t provide a file type.
- [[#6959]](https://github.com/h2oai/h2o-3/issues/6959) - Fixed GLM ordinal generic metrics to provide missing information in the payload.
- [[#6960]](https://github.com/h2oai/h2o-3/issues/6960) - Fixed “maxrsweep” NPE in ModelSelection thrown when the replacement step stopped too early.
- [[#6961]](https://github.com/h2oai/h2o-3/issues/6961) - Fixed “maxrsweep” replacement bug in ModelSelection by updating the implementation method.
- [[#6973]](https://github.com/h2oai/h2o-3/issues/6973) - Fixed  unnecessary transformations in the scikit-learn wrapper by using model performance API. 
- [[#6979]](https://github.com/h2oai/h2o-3/issues/6979) - Fixed upload of big files in Sparkling Water deployment.
- [[#6983]](https://github.com/h2oai/h2o-3/issues/6983) - Changed the error message that GLM does not support contributions.
- [[#6985]](https://github.com/h2oai/h2o-3/issues/6985) - Fixed QuantilesGlobal histogram type failing in GBM when all columns were categorial.
- [[#7002]](https://github.com/h2oai/h2o-3/issues/7002) - Added support for MapR 6.2 to fix the error caused by updating the cluster.
- [[#7006]](https://github.com/h2oai/h2o-3/issues/7006) - Fixed large file upload in Python.
- [[#7023]](https://github.com/h2oai/h2o-3/issues/7023) - Fixed inability to stop print out of model information in Python.
- [[#7056]](https://github.com/h2oai/h2o-3/issues/7056) - Removed `-seed` variable hiding in GAM.
- [[#7104]](https://github.com/h2oai/h2o-3/issues/7104) - Updated `h2o.upload_mojo` to also work for POJO.
- [[#7432]](https://github.com/h2oai/h2o-3/issues/7432) - Added unsupported operation exception when trying to use SHAP summary plot when building DRF model with `binomial_double_trees`.
- [[#8542]](https://github.com/h2oai/h2o-3/issues/8542) - Refactored the rendering logic in the Python client.
- [[#10436]](https://github.com/h2oai/h2o-3/issues/10436) - Added xval argument to `h2o.confusionMatrix` in R.

#### Improvement

- [[#6933]](https://github.com/h2oai/h2o-3/issues/6933) - Added support for calibrating an already trained model manually.
- [[#6941]](https://github.com/h2oai/h2o-3/issues/6941) - Added support for using Isotonic Regression for model calibration.
- [[#6942]](https://github.com/h2oai/h2o-3/issues/6942) - Added ability to S3A allowing it to share the built-in AWS credential providers.
- [[#6947]](https://github.com/h2oai/h2o-3/issues/6947) - Improved `configure_s3_using_s3a` allowing it to be usable in any deployment.
- [[#6982]](https://github.com/h2oai/h2o-3/issues/6982) - Updated `train_segments` function in R to be independent of camel casing in the algorithm name.
- [[#6986]](https://github.com/h2oai/h2o-3/issues/6986) -  Improved runtime for QuantilesGlobal histogram by using exact split-points for low-cardinality columns.
- [[#6992]](https://github.com/h2oai/h2o-3/issues/6992) - Exposed the Sequential Walker for R/Python and added option to disable early stopping.
- [[#7007]](https://github.com/h2oai/h2o-3/issues/7007) - Cleaned up Key API by removing replicas.
- [[#7340]](https://github.com/h2oai/h2o-3/issues/7340) - Cleaned up the default output after training a model.
- [[#7510]](https://github.com/h2oai/h2o-3/issues/7510) - Exposed calibrated probabilities in `mojo_predict_pandas`.

#### New Feature

- [[#6950]](https://github.com/h2oai/h2o-3/issues/6950) - Simplified the configuration of S3 for Frame exportation. 
- [[#6984]](https://github.com/h2oai/h2o-3/issues/6984) - Added `train_segments` test for Isolation Forest.
- [[#6991]](https://github.com/h2oai/h2o-3/issues/6991) - Added ability to `h2o.no_progress` in R allowing it to accept expressions.
- [[#7011]](https://github.com/h2oai/h2o-3/issues/7011) - Implemented dispersion parameter estimation for GLM.
- [[#7016]](https://github.com/h2oai/h2o-3/issues/7016) - Added ability to export H2O Frame to a Parquet.
- [[#7076]](https://github.com/h2oai/h2o-3/issues/7076) - Added Pareto front plots to AutoML Explain.
- [[#7091]](https://github.com/h2oai/h2o-3/issues/7091) - Added “deviance” method to dispersion for calculating p-values. 
- [[#7093]](https://github.com/h2oai/h2o-3/issues/7093) - Implemented variable inflation factors for GLM.
- [[#7192]](https://github.com/h2oai/h2o-3/issues/7192) - Implemented in-training checkpoints for GBM.
- [[#8005]](https://github.com/h2oai/h2o-3/issues/8005) - Implemented support for interactions to MOJO for CoxPH.
- [[#12152]](https://github.com/h2oai/h2o-3/issues/12152) - Added `h2o.make_leaderboard` function which scores and compares a set of models to AutoML.

#### Task

- [[#6927]](https://github.com/h2oai/h2o-3/issues/6927) - Secured XGBoost connections in multinode environments. 
- [[#6948]](https://github.com/h2oai/h2o-3/issues/6948) - Added missing added predictor and deleted predictor to the result frame and model summary of ModelSelection.
- [[#6952]](https://github.com/h2oai/h2o-3/issues/6952) - Added support allowing you to force GLM to build a null model where the model only returns the coefficients for the intercept.
- [[#6953]](https://github.com/h2oai/h2o-3/issues/6953) - Added support allowing GLM `gamma` to fix the dispersion parameter to calculate p-values.
- [[#6998]](https://github.com/h2oai/h2o-3/issues/6998) - Implemented “maxr” speedup for Modelselection by introducting “maxrsweep”.
- [[#7013]](https://github.com/h2oai/h2o-3/issues/7013) - Implemented dispersion factor estimation using maximum likelihood for GLM gamma family.

#### Docs

- [[#6920]](https://github.com/h2oai/h2o-3/issues/6920) - Added documentation on Isotonic Regression.
- [[#6931]](https://github.com/h2oai/h2o-3/issues/6931) - Added variable inflation factors to GLM section of the user guide.
- [[#6932]](https://github.com/h2oai/h2o-3/issues/6932) - Added Tweedie dispersion parameter estimation to the GLM section of the user guide.
- [[#6934]](https://github.com/h2oai/h2o-3/issues/6934) - Added confusion matrix calculation explanation to performance and prediction.
- [[#6939]](https://github.com/h2oai/h2o-3/issues/6939) - Added `get_predictors_removed_per_step()` and `get_predictors_added_per_step()` examples to ModelSelection.
- [[#6945]](https://github.com/h2oai/h2o-3/issues/6945) - Added use case section to the welcome page of the user guide.
- [[#6987]](https://github.com/h2oai/h2o-3/issues/6987) - Added MOJO import/export information to each algorithm page.
- [[#7048]](https://github.com/h2oai/h2o-3/issues/7048) - Added major release blogs to user guide and moved change log to top of the sidebar.

### Zumbo (3.36.1.5) - 9/15/2022

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zumbo/5/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zumbo/5/index.html</a>

#### Security

- Addressed security vulnerability CVE-2021-22569 in the `h2o.jar`.

### Zumbo (3.36.1.4) - 8/3/2022

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zumbo/4/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zumbo/4/index.html</a>

#### Bug Fix

- [[#6954]](https://github.com/h2oai/h2o-3/issues/6954) - Disabled `partial_plot` for Uplift DRF temporarily.
- [[#6958]](https://github.com/h2oai/h2o-3/issues/6958) - Added support for predicting with Autoencoder when using eigen encoding.
- [[#6962]](https://github.com/h2oai/h2o-3/issues/6962) - Fixed XGBoost failure with enabled cross validation in external cluster mode by explicitly starting external XGBoost before cross validation.

#### Security

- [[#6946]](https://github.com/h2oai/h2o-3/issues/6946) - Addressed security vulnerabilities CVE-2021-22573 and CVE-2019-10172 in Steam assembly.

### Zumbo (3.36.1.3) - 7/8/2022

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zumbo/3/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zumbo/3/index.html</a>

#### Bug Fix

- [[#6975]](https://github.com/h2oai/h2o-3/issues/6975) - Fixed CoxPH MOJO ignoring offset column.
- [[#6976]](https://github.com/h2oai/h2o-3/issues/6976) - Fixed the incorrect predictions from the CoxPH MOJO on categorical columns. 
- [[#6977]](https://github.com/h2oai/h2o-3/issues/6977) - Fixed the **View** button not working after completing an AutoML job.
- [[#6989]](https://github.com/h2oai/h2o-3/issues/6989) - Fixed `num_of_features` not being used in call for `varimp_heatmap()`.
- [[#7015]](https://github.com/h2oai/h2o-3/issues/7015) - Fixed GAM’s `fold_column` being treated as a normal column to score for.
- [[#7053]](https://github.com/h2oai/h2o-3/issues/7053) - Updated GBM cross validation model summary tables to reflect that some trees are removed due to a better score occurring with fewer trees.
- [[#7140]](https://github.com/h2oai/h2o-3/issues/7140) - Fixed `fit_params` passthrough for scikit-learn compatibility.
- [[#7718]](https://github.com/h2oai/h2o-3/issues/7718) - Fixed validateWithCheckpoint to work with default parameter settings.
- [[#7719]](https://github.com/h2oai/h2o-3/issues/7719) - Fixed validateWithCheckpoint to work with parameters that are arrays.

#### Improvement

- [[#6990]](https://github.com/h2oai/h2o-3/issues/6990) - Added expert option to force-enable MOJO for CoxPH even when `interactions` are enabled.
- [[#7060]](https://github.com/h2oai/h2o-3/issues/7060) - Makes language rules generation on demand and introduced “EnumLimited” option for categorical encoding.

#### New Feature

- [[#6968]](https://github.com/h2oai/h2o-3/issues/6968) - Added `transform_frame` for GLRM allowing users to obtain the new X for a new data set.
- [[#6974]](https://github.com/h2oai/h2o-3/issues/6974) - Added support for numerical interactions in CoxPH MOJO.

#### Docs

- [[#6965]](https://github.com/h2oai/h2o-3/issues/6965) - Fixed the `uplift_metric` documentation for Uplift DRF.
- [[#6967]](https://github.com/h2oai/h2o-3/issues/6967) - Added `transform_frame` to GLRM documentation. 
- [[#6970]](https://github.com/h2oai/h2o-3/issues/6970) - Added `mode = “maxrsweep”` to ModelSelection documentation.
- [[#6971]](https://github.com/h2oai/h2o-3/issues/6971) - Corrected the R documentation on R^2.
- [[#6988]](https://github.com/h2oai/h2o-3/issues/6988) - Updated supported MOJO list to include GAM MOJO import.

#### Security

- [[#6978]](https://github.com/h2oai/h2o-3/issues/6978) - Fixed security issue in genmodel (CVE-2022-25647).

### Zumbo (3.36.1.2) - 5/26/2022

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zumbo/2/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zumbo/2/index.html</a>

#### Bug Fix

- [[#6999]](https://github.com/h2oai/h2o-3/issues/6999) - Refactored Uplift DRF methods.
- [[#7001]](https://github.com/h2oai/h2o-3/issues/7001) - Removed duplicate runs in MaxR.
- [[#7014]](https://github.com/h2oai/h2o-3/issues/7014) - Fixed the ambiguity check in Explain’s consolidate varimp.
- [[#7020]](https://github.com/h2oai/h2o-3/issues/7020) - Improved efficiency of ``pd_plot`` and ``ice_plot`` and made ``rug`` optional.
- [[#7021]](https://github.com/h2oai/h2o-3/issues/7021) - Fixed H2O failing with null pointer exception when providing an improper ``-network`` to h2o.jar.
- [[#7022]](https://github.com/h2oai/h2o-3/issues/7022) - Fixed external XGBoost on K8s.
- [[#7042]](https://github.com/h2oai/h2o-3/issues/7042) - Fixed failing concurrent-running GLM training processes.
- [[#7283]](https://github.com/h2oai/h2o-3/issues/7283) - Fixed missing time values SHAP summary plot error.
- [[#7732]](https://github.com/h2oai/h2o-3/issues/7732) - Fixed Partial Dependence Plot’s date/time handling from explainability modules.

#### New Feature

- [[#7004]](https://github.com/h2oai/h2o-3/issues/7004) - Updated Uplift DRF API.

#### Docs

- [[#7000]](https://github.com/h2oai/h2o-3/issues/7000) - Added ``model_summary`` examples for GLM.
- [[#7010]](https://github.com/h2oai/h2o-3/issues/7010) - Updated incorrect formula in GLM booklet.
- [[#7219]](https://github.com/h2oai/h2o-3/issues/7219) - Updated Python Module documentation readability.

### Zumbo (3.36.1.1) - 4/13/2022

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zumbo/1/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zumbo/1/index.html</a>

#### Bug Fix

- [[#7035]](https://github.com/h2oai/h2o-3/issues/7035) - Fixed Residual Analysis plot flipping the residual calculation.
- [[#7040]](https://github.com/h2oai/h2o-3/issues/7040) - Added more detailed exception when disconnected due to error caused by `Rcurl`. 
- [[#7047]](https://github.com/h2oai/h2o-3/issues/7047) - Made R client attempt to connect to `curl` package  instead of `Rcurl` package first.
- [[#7055]](https://github.com/h2oai/h2o-3/issues/7055) - Ensures GLM models fail instead of throwing warnings when `beta_contraints` and `non_negative are used with multinomial or ordinal families.
- [[#7057]](https://github.com/h2oai/h2o-3/issues/7057) - Fixed how `cv_computeAndSetOptimalParameters` deals with multiple `alpha` and `lambda` values across different folds.
- [[#7058]](https://github.com/h2oai/h2o-3/issues/7058) - Increased MaxR running speed.
- [[#7068]](https://github.com/h2oai/h2o-3/issues/7068) - Fixed `getGLMRegularizationPath` erroring out when `standardize = False`.
- [[#7194]](https://github.com/h2oai/h2o-3/issues/7194) - Fixed Keystore not generating on Java 16+.
- [[#7634]](https://github.com/h2oai/h2o-3/issues/7634) - Added a `num_of_features` argument to `h2o.varimp_heatmap` to limit the number of displayed variables. 
- [[#12130]](https://github.com/h2oai/h2o-3/issues/12130) - Fixed `cross_validation_metrics_summary` not being accessible for Stacked Ensemble.

#### Improvement

- [[#7029]](https://github.com/h2oai/h2o-3/issues/7029) - Improved AUUC result information in Uplift DRF by adding information on number of bins.
- [[#7038]](https://github.com/h2oai/h2o-3/issues/7038) - Replaced `class()` with `inherits()` in R package. 
- [[#7039]](https://github.com/h2oai/h2o-3/issues/7039) - Fixed invalid URLs in R Package.
- [[#7051]](https://github.com/h2oai/h2o-3/issues/7051) - Added normalized `AUUC` to Uplift DRF. 
- [[#7059]](https://github.com/h2oai/h2o-3/issues/7059) - Sped-up AutoML by avoiding sleep-waiting.
- [[#7061]](https://github.com/h2oai/h2o-3/issues/7061) - Removed Stacked Ensembles with XGB metalearner to increase speed.
- [[#7062]](https://github.com/h2oai/h2o-3/issues/7062) - Ensures AutoML reproducibility when `max_models` is used.
- [[#7135]](https://github.com/h2oai/h2o-3/issues/7135) - Updated AutoML default leaderboard regression sorting to `RMSE`.

#### New Feature

- [[#7036]](https://github.com/h2oai/h2o-3/issues/7036) - Bundled several basic datasets with H2O for use in examples.
- [[#7050]](https://github.com/h2oai/h2o-3/issues/7050) - Added h2o.jar assembly for secure Steam deployments and excluded PAM authentication from minimal/Steam builds.
- [[#7054]](https://github.com/h2oai/h2o-3/issues/7054) - Added ability to ingest data from secured Hive using h2odriver.jar in standalone.
- [[#7073]](https://github.com/h2oai/h2o-3/issues/7073) - Bundled KrbStandalone extension in h2odriver.jar. 
- [[#7085]](https://github.com/h2oai/h2o-3/issues/7085) - Implemented new method for defining histogram split-points in GBM/DRF designed to address outlier issues with default `UniformAdaptive` method.
- [[#7092]](https://github.com/h2oai/h2o-3/issues/7092) - Added ability to reorder frame levels based on their frequencies and to relieve only topN levels for GLM.
- [[#7094]](https://github.com/h2oai/h2o-3/issues/7094) - Added a function to calculate predicted versus actual response in GLM.
- [[#7258]](https://github.com/h2oai/h2o-3/issues/7258) - Implemented MOJO for Extended Isolation Forest.
- [[#7261]](https://github.com/h2oai/h2o-3/issues/7261) - Added monotone splines to GAM.
- [[#7271]](https://github.com/h2oai/h2o-3/issues/7271) - Added a plot function for gains/lift to R and Python.  
- [[#7285]](https://github.com/h2oai/h2o-3/issues/7285) - Added ability to acquire metric builder updates for Sparkling Water calculation without H2O runtime.
- [[#7664]](https://github.com/h2oai/h2o-3/issues/7664) - Added support for `interaction_constraints` to GBM.
- [[#7785]](https://github.com/h2oai/h2o-3/issues/7785) - Exposed distribution parameter in AutoML

#### Task

- [[#7078]](https://github.com/h2oai/h2o-3/issues/7078) - Decoupled Infogram and XGBoost removing Infograms reliance on XGBoost to work.
- [[#7080]](https://github.com/h2oai/h2o-3/issues/7080) - Verified GLM binomial IRLSM implementation and `p-value` calculation. 
- [[#7089]](https://github.com/h2oai/h2o-3/issues/7089) - Added private ModelBuilder parameter to AutoML to enforce the time budget on the final model after cross-validation.

#### Sub-Task

- [[#7069]](https://github.com/h2oai/h2o-3/issues/7069) - Made Ice plot functionalities also available on `pd_plot`.
- [[#7160]](https://github.com/h2oai/h2o-3/issues/7160) - Added option to normalize y-axis values. 
- [[#7163]](https://github.com/h2oai/h2o-3/issues/7163) - Added option to display logodds for binary models for Ice plots.
- [[#7164]](https://github.com/h2oai/h2o-3/issues/7164) - Added ability to save final graphing data to a frame for Ice plots.
- [[#7165]](https://github.com/h2oai/h2o-3/issues/7165) - Added option to specify a grouping variable for Ice plots.
- [[#7166]](https://github.com/h2oai/h2o-3/issues/7166) - Shows original observation values as points on the line for Ice plots.
- [[#7167]](https://github.com/h2oai/h2o-3/issues/7167) - Added option to toggle PDP vs Ice lines on or off.

#### Docs

- [[#7034]](https://github.com/h2oai/h2o-3/issues/7034) - Added documentation on the monotone spline for GAM.
- [[#7027]](https://github.com/h2oai/h2o-3/issues/7027) - Added links to the Additional Resources page to the sites where users can ask questions.
- [[#7031]](https://github.com/h2oai/h2o-3/issues/7031) - Updated the examples for the Residual Analysis Plot.
- [[#7037]](https://github.com/h2oai/h2o-3/issues/7037) - Updated the K8s deployment tutorial.
- [[#7043]](https://github.com/h2oai/h2o-3/issues/7043) - Improved Uplift DRF User Guide documentation. 
- [[#7049]](https://github.com/h2oai/h2o-3/issues/7049) - Shifted the links from the H2O-3 docs page to the User Guide “Additional Resources” page. 
- [[#7066]](https://github.com/h2oai/h2o-3/issues/7066) - Fixed MOJO importable/exportable table in User Guide.
- [[#7067]](https://github.com/h2oai/h2o-3/issues/7067) - Added a note that MOJOs won’t build if `interactions` are specified. 
- [[#7070]](https://github.com/h2oai/h2o-3/issues/7070) - Added information on how H2O handles date columns. 
- [[#7075]](https://github.com/h2oai/h2o-3/issues/7075) - Fixed code typos on Admissible ML page in User Guide.
- [[#7074]](https://github.com/h2oai/h2o-3/issues/7074) - Added information on the `-hdfs_config` tag.


### Zorn (3.36.0.4) - 3/30/2022

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zorn/4/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zorn/4/index.html</a>

#### Bug Fix

- [[#7046]](https://github.com/h2oai/h2o-3/issues/7046) - Fixed logic operations error in R package. 
- [[#7052]](https://github.com/h2oai/h2o-3/issues/7052) - Clarified that `enum` and `eigen` `categorical_encoding` values do not work for XGBoost.

#### Improvement

- [[#7177]](https://github.com/h2oai/h2o-3/issues/7177) - Added the Qini value metric to Uplift DRF.

#### Docs

- [[#7157]](https://github.com/h2oai/h2o-3/issues/7157) - Added information on the `make_metrics` command to the Performance and Prediction section of the User Guide.


### Zorn (3.36.0.3) - 2/16/2022

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zorn/3/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zorn/3/index.html</a>

#### Bug Fix

- [[#7098]](https://github.com/h2oai/h2o-3/issues/7098) - Fixed S3 file downloads not working by adding `aws_java_sdk_sts` as a dependency of H2O persist S3.
- [[#7102]](https://github.com/h2oai/h2o-3/issues/7102) - Added note to GBM, DRF, IF, and EIF that `build_tree_one_node=True` does not work with current release.
- [[#7108]](https://github.com/h2oai/h2o-3/issues/7108) - Extended AWS default credential chain instead of replacing it.
- [[#7112]](https://github.com/h2oai/h2o-3/issues/7112) - Fixed import failures for URLs longer than 152 characters.
- [[#7113]](https://github.com/h2oai/h2o-3/issues/7113) -  Fix AutoML ignoring `verbosity` setting.
- [[#7138]](https://github.com/h2oai/h2o-3/issues/7138) - Fixed Huber distribution bug for `deviance`.

#### Improvement

- [[#7187]](https://github.com/h2oai/h2o-3/issues/7187) - Removed “H2O API Extensions” from `h2o.init()` output.

#### Docs

- [[#7096]](https://github.com/h2oai/h2o-3/issues/7096) - Corrected typos and inconsistencies in Admissible ML documentation.
- [[#7119]](https://github.com/h2oai/h2o-3/issues/7119) - Updated copyright year in documentation.
- [[#7128]](https://github.com/h2oai/h2o-3/issues/7128) - Clarified feasible intervals for tweedie power.
- [[#7196]](https://github.com/h2oai/h2o-3/issues/7196) - Clarified Java requirements when running H2O on Hadoop.


### Zorn (3.36.0.2) - 1/25/2022

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zorn/2/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zorn/2/index.html</a>

#### Bug Fix

- [[#7125]](https://github.com/h2oai/h2o-3/issues/7125) - Updated XGBoostMojoModel to only consider the number of built trees, not the value of ``ntrees``.
- [[#7126]](https://github.com/h2oai/h2o-3/issues/7126) - Fixed issue in AutoEncoder’s early stopping automatic selection by setting ``AUTO = MSE`` instead of ``deviance``.
- [[#7133]](https://github.com/h2oai/h2o-3/issues/7133) - Fixed MOJO imports to retain information on weights column.
- [[#7143]](https://github.com/h2oai/h2o-3/issues/7143) - Fixed XGBoost errors on Infogram by improving support for XGBoost.
- [[#7145]](https://github.com/h2oai/h2o-3/issues/7145) - Fixed MOJO import automatically re-using original Model ID for current release cycle.
- [[#7149]](https://github.com/h2oai/h2o-3/issues/7149) - Fixed import of Parquet files from S3.
- [[#7155]](https://github.com/h2oai/h2o-3/issues/7155) - Fixed `h2o.group_by` warning present in documentation example caused by function only reading the first column when several are provided.
- [[#7174]](https://github.com/h2oai/h2o-3/issues/7174) - Added check to ensure that a model supports MOJOs to prevent production of bad MOJOs.
- [[#7179]](https://github.com/h2oai/h2o-3/issues/7179) - Fixed Python warnings before model training when training with offset, weights, and fold columns.
- [[#7181]](https://github.com/h2oai/h2o-3/issues/7181) - Fixed MOJO upload in Python.
- [[#7201]](https://github.com/h2oai/h2o-3/issues/7201) - Fixed error in uploading pandas DataFrame to H2O by enforcing `uft-8` encoding.
- [[#7273]](https://github.com/h2oai/h2o-3/issues/7273) - Customized FormAuthenticator to use relative redirects.


#### Improvement

- [[#7141]](https://github.com/h2oai/h2o-3/issues/7141) - Removed numpy dependency for Infogram.

#### New Feature

- [[#7232]](https://github.com/h2oai/h2o-3/issues/7232) - Added backward selection method for ModelSelection. 

#### Task

- [[#7123]](https://github.com/h2oai/h2o-3/issues/7123) - Added support to PredictCsv for testing concurrent predictions.

#### Docs

- [[#7136]](https://github.com/h2oai/h2o-3/issues/7136) -  Added backward mode documentation to ModelSelection.
- [[#7194]](https://github.com/h2oai/h2o-3/issues/7194) - Updated Kubernetes Headless Service and StatefulSet documentation.

### Zorn (3.36.0.1) - 12/29/2021

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zorn/1/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zorn/1/index.html</a>

#### Bug Fix

- [[#7214]](https://github.com/h2oai/h2o-3/issues/7214) - Fixed differences in H2O’s random behavior across Java versions by disabling Stream API in this task. 
- [[#7247]](https://github.com/h2oai/h2o-3/issues/7247) - Fixed CoxPH summary method in Python to return H2OTwoDimTable.
- [[#7273]](https://github.com/h2oai/h2o-3/issues/7273) - Fixed form authentication not working by enforcing relative redirects in Jetty.
- [[#7888]](https://github.com/h2oai/h2o-3/issues/7888) - Fixed exception raised in K-Means when a model is built using `nfolds` by disabling centroid stats for Cross-Validation.


#### Improvement

- [[#7188]](https://github.com/h2oai/h2o-3/issues/7188) - Removed `ymu` and `rank` visibility from FlowUI.
- [[#7209]](https://github.com/h2oai/h2o-3/issues/7209) - Exposed `lambda` in Rulefit to have better control over regularization strength. 
- [[#7217]](https://github.com/h2oai/h2o-3/issues/7217) - Implemented sequential replacement method with ModelSelection.
- [[#7222]](https://github.com/h2oai/h2o-3/issues/7222) - Improved rule extraction from trees in RuleFit.
- [[#7240]](https://github.com/h2oai/h2o-3/issues/7240) - Improved exception handling in AutoML and Grids to prevent model failure.
- [[#7395]](https://github.com/h2oai/h2o-3/issues/7395) - Ensured Infogram uses validation frame and cross-validation when enabled.
- [[#8096]](https://github.com/h2oai/h2o-3/issues/8096) - Added dynamic stacking metalearning strategy for Stacked Ensemble in AutoML.

#### New Feature

- [[#7246]](https://github.com/h2oai/h2o-3/issues/7246) - Added support and rule coverage to RuleFit.
- [[#7268]](https://github.com/h2oai/h2o-3/issues/7268) - Added support for importing GAM MOJO.
- [[#7279]](https://github.com/h2oai/h2o-3/issues/7279) - Added a convenience tool that converts MOJO to POJO from the command line.
- [[#7280]](https://github.com/h2oai/h2o-3/issues/7280) - Added support allowing users to modify floating point representation in POJO.
- [[#7287]](https://github.com/h2oai/h2o-3/issues/7287) - Added experimental support for importing POJO for in-H2O scoring.
- [[#7316]](https://github.com/h2oai/h2o-3/issues/7316) - Added official support for Java 16 and 17.
- [[#7323]](https://github.com/h2oai/h2o-3/issues/7323) - Added Java 16 and 17 to the cluster.
- [[#7333]](https://github.com/h2oai/h2o-3/issues/7333) - Added a compatibility K8s module that allows older versions of H2O to run on K8s.
- [[#7447]](https://github.com/h2oai/h2o-3/issues/7447) - Added ability to convert MOJO to POJO for tree models.
- [[#7515]](https://github.com/h2oai/h2o-3/issues/7515) - Added support enabling users to configure S3 with S3A configuration.
- [[#7574]](https://github.com/h2oai/h2o-3/issues/7574) - Implemented the Infogram model.
- [[#11818]](https://github.com/h2oai/h2o-3/issues/11818) - Implemented the Uplift DRF algorithm.

#### Task

- [[#7322]](https://github.com/h2oai/h2o-3/issues/7322) - Upgraded to Gradle 7 to support Java 16+.
- [[#7430]](https://github.com/h2oai/h2o-3/issues/7430) - Added R API for Infogram.

#### Docs

- [[#7212]](https://github.com/h2oai/h2o-3/issues/7212) - Added documentation on Infogram to the User Guide.
- [[#7279]](https://github.com/h2oai/h2o-3/issues/7279) - Added documentation on ModelSelection to the User Guide.
- [[#7275]](https://github.com/h2oai/h2o-3/issues/7275) - Added notebook on floating point issue for POJO and FAQ documentation on POJO split points.
- [[#7329]](https://github.com/h2oai/h2o-3/issues/7329) - Fixed bullet list formatting issues. 
- [[#7742]](https://github.com/h2oai/h2o-3/issues/7742) - Updated R Reference Guide list.

### Zizler (3.34.0.8) - 1/13/2022

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zizler/8/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zizler/8/index.html</a>

#### Bug Fix

- [[#7148]](https://github.com/h2oai/h2o-3/issues/7148) - Fixed MOJO import automatically re-using original Model ID.

#### Security

- [[#7147]](https://github.com/h2oai/h2o-3/issues/7147) - Upgraded to log4j 2.17.1.

### Zizler (3.34.0.7) - 12/21/2021

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zizler/7/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zizler/7/index.html</a>

#### Security

- Fixed CVE-2021-45105 log4j vulnerability.

### Zizler (3.34.0.6) - 12/15/2021

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zizler/6/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zizler/6/index.html</a>

#### Security

- Fixed CVE-2021-45046 log4j vulnerability.

### Zizler (3.34.0.5) - 12/13/2021

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zizler/5/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zizler/5/index.html</a>

#### Bug Fix

- [[#7213]](https://github.com/h2oai/h2o-3/issues/7213) - Fixed permutation variable importance to correctly work with weights.
- [[#7234]](https://github.com/h2oai/h2o-3/issues/7234) - Fixed data removal issue in GAM caused by fitting two different models on the same DataFrame.

#### Improvement

- [[#7233]](https://github.com/h2oai/h2o-3/issues/7233) - Added `coef()` and `coef_norm()` functions to MaxRGLM.
- [[#7251]](https://github.com/h2oai/h2o-3/issues/7251) - Added ability that labels observations that match rules in Rulefit.
- [[#7262]](https://github.com/h2oai/h2o-3/issues/7262) - Updated parquet parser to handle dates allowing H2O `import_file()` to import date columns from Spark DataFrame.
- [[#7276]](https://github.com/h2oai/h2o-3/issues/7276) - Consolidated Rulefit rules to remove unnecessary splits.
- [[#7439]](https://github.com/h2oai/h2o-3/issues/7439) -  Improved the efficiency of job polling in AutoML.
- [[#7474]](https://github.com/h2oai/h2o-3/issues/7474) - Deduplicated Rulefit rules in post-processing step.

#### New Feature

- [[#7235]](https://github.com/h2oai/h2o-3/issues/7235) - Added option to mimic the “ActiveProcessorCount” for older JVMs.

#### Task

- [[#7227]](https://github.com/h2oai/h2o-3/issues/7227) - Added warning in GLRM for when users set `model_id` and `representation_name` to the same string to help avoid a collision of model and frame using the same key.
- [[#7239]](https://github.com/h2oai/h2o-3/issues/7239) - Added `rank` and `ymu` model outputs to GLM.

#### Docs
- [[#7210]](https://github.com/h2oai/h2o-3/issues/7210) - Added link to the Change Log in the User Guide index.
- [[#7218]](https://github.com/h2oai/h2o-3/issues/7218) - Updated parameter list for MaxRGLM and outlined that MaxRGLM only support regression.
- [[#7228]](https://github.com/h2oai/h2o-3/issues/7228) -  Updated MaxRGLM examples to use new functions `coef()`, `coef_norm()`, and `result()`.
- [[#7236]](https://github.com/h2oai/h2o-3/issues/7236) - Added examples in R/Python on how to get reproducibility information.
- [[#7296]](https://github.com/h2oai/h2o-3/issues/7296) - Fixed local build warnings for Python Module documentation.

#### Security
- Upgraded to log4j 2.15.0 to address vulnerability CVE-2021-44228.


### Zizler (3.34.0.4) - 11/17/2021

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zizler/4/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zizler/4/index.html</a>

#### Bug Fix

- [[#7252]](https://github.com/h2oai/h2o-3/issues/7252) -  Fixed broken `weights_column` in GAM.
- [[#7256]](https://github.com/h2oai/h2o-3/issues/7256) - Fixed printing a DRF model when there are no out-of-bag samples.
- [[#7259]](https://github.com/h2oai/h2o-3/issues/7259) - Fixed the `pyunit_PUBDEV_5008_5386_glm_ordinal_large.py` test from failing.
- [[#7263]](https://github.com/h2oai/h2o-3/issues/7263) - Fixed AutoML XGBoost `learn_rate` search step.
- [[#7265]](https://github.com/h2oai/h2o-3/issues/7265) - Ensured that jobs are rendered correctly in Flow and that AutoML internal jobs can be monitored without crashing on the backend.
- [[#7269]](https://github.com/h2oai/h2o-3/issues/7269) - Fixed `gam_columns` failure in the `pyunit_PUBDEV_7185_GAM_mojo_ordinal.py` test. 
- [[#7290]](https://github.com/h2oai/h2o-3/issues/7290) - Outlined that `tree_method=“approx”` is not supported with `col_sample_rate` or `col_sample_by_level` in XGBoost.
- [[#7517]](https://github.com/h2oai/h2o-3/issues/7517) - Fixed multinomial classification in Rulefit.
- [[#7681]](https://github.com/h2oai/h2o-3/issues/7681) - Fixed inconsistencies in GLM `beta_constraints`.
- [[#7738]](https://github.com/h2oai/h2o-3/issues/7738) - Enabled ability to provide metalearner parameters for NaiveBayes and XGBoost.

#### Improvement

- [[#7358]](https://github.com/h2oai/h2o-3/issues/7358) - Added a custom model ID parameter to MOJO importing/uploading methods through R/Python API and if a custom model ID is not specified, the default model ID is propagated as the models name from the MOJO path.
- [[#7361]](https://github.com/h2oai/h2o-3/issues/7361) - Added warning for users who accidentally build a regression model when attempting building a binary classification model because they forgot to convert their target to categorical.
- [[#7372]](https://github.com/h2oai/h2o-3/issues/7372) - Tuned `scale_pos_weight` parameter for XGBooost in AutoML for imbalanced data.

#### New Feature

- [[#7274]](https://github.com/h2oai/h2o-3/issues/7274) - Added saving parameters to plot functions.

#### Task

- [[#7250]](https://github.com/h2oai/h2o-3/issues/7250) - Added GAM training/validation metrics.
- [[#7264]](https://github.com/h2oai/h2o-3/issues/7264) - Ensured H2O-3 builds with pip version >= 21.3.
- [[#7311]](https://github.com/h2oai/h2o-3/issues/7311) - Added result frame to MAXRGLM.

#### Docs

- [[#7267]](https://github.com/h2oai/h2o-3/issues/7267) - Localized MOJO support list for all the H2O-3 algorithms.
- [[#7278]](https://github.com/h2oai/h2o-3/issues/7278) - Added Gains/Lift documentation to the Performance and Prediction section of the User Guide.
- [[#7288]](https://github.com/h2oai/h2o-3/issues/7288) - Corrected metric in the Performance and Prediction “Sensitive to Outliers” section of the User Guide.
- [[#7377]](https://github.com/h2oai/h2o-3/issues/7377) - Clarified that `asnumeric()` converted ‘enum’ columns to underlying factor values and highlighted correct transformation approach.

### Zizler (3.34.0.3) - 10/7/2021

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zizler/3/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zizler/3/index.html</a>

#### Bug Fix

- [[#7291]](https://github.com/h2oai/h2o-3/issues/7291) - Fixed user login from key tab in standalone on Kerberos.
- [[#7300]](https://github.com/h2oai/h2o-3/issues/7300) - Improved error messages in Explain module by making the errors clearer.
- [[#7307]](https://github.com/h2oai/h2o-3/issues/7307) - Fixed H2OTable colTypes in Grid’s summary table.
- [[#7308]](https://github.com/h2oai/h2o-3/issues/7308) - Fixed infinite loop in hex.grid.HyperSpaceWalker.RandomDiscreteValueWalker.
- [[#7317]](https://github.com/h2oai/h2o-3/issues/7317) - Fixed AutoML ignoring optional Stacked Ensembles.
- [[#7319]](https://github.com/h2oai/h2o-3/issues/7319) - Fixed NPE thrown in AutoML when XGBoost is disabled/not available.
- [[#7320]](https://github.com/h2oai/h2o-3/issues/7320) - Fixed CRAN install.
- [[#8458]](https://github.com/h2oai/h2o-3/issues/8458) - Improved XGBoost API to ensure both `col_sample_rate` and `colsample_bylevel` (and other XGBoost parameters aliases) are set correctly. 
- [[#7375]](https://github.com/h2oai/h2o-3/issues/7375) - Fixed NPE thrown for `ModelJsonReader.findINJson` for cases when path does not exist.

#### Improvement

- [[#7314]](https://github.com/h2oai/h2o-3/issues/7314) - Exposed AutoML `get_leaderboard` as a method in Python.
- [[#7315]](https://github.com/h2oai/h2o-3/issues/7315) - Improved Python client by printing the stacktrace in case of ServerError allowing users to report informative issues for investigation.
- [[#7350]](https://github.com/h2oai/h2o-3/issues/7350) - Enhanced tests by testing the case through all encodings.

#### Task

- [[#7312]](https://github.com/h2oai/h2o-3/issues/7312) - Updated ANOVA GLM to save model summary as a frame.
- [[#7326]](https://github.com/h2oai/h2o-3/issues/7326) - Added GLM offset column support to GLM MOJO.

#### Docs

- [[#7302]](https://github.com/h2oai/h2o-3/issues/7302) - Updated the R/Python AutoML documentation parameters to match the descriptions in the User Guide.
- [[#7304]](https://github.com/h2oai/h2o-3/issues/7304) - Removed GLM from `balance_classes` parameter appendix page in the User Guide.
- [[#7309]](https://github.com/h2oai/h2o-3/issues/7309) - Updated the `asfactor` procedure documentation to show multiple column usage.



### Zizler (3.34.0.1) - 9/14/2021

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zizler/1/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zizler/1/index.html</a>

#### Bug Fix

- [[#7330]](https://github.com/h2oai/h2o-3/issues/7330) - Fixed matplotlib 3.4 compatibility issues with `partial_plot`.
- [[#7339]](https://github.com/h2oai/h2o-3/issues/7339) - Deprecated `is_supervised` parameter for h2o.grid method in R.
- [[#7341]](https://github.com/h2oai/h2o-3/issues/7341) - Fixed AutoML NPE by ensuring that models without metrics are not added to the leaderboard.
- [[#7360]](https://github.com/h2oai/h2o-3/issues/7360) - Redistributed the time budget for AutoML.
- [[#7365]](https://github.com/h2oai/h2o-3/issues/7365) - Fixed and reorganized the H2O Explain leaderboard and fixed the confusion matrix.
- [[#7366]](https://github.com/h2oai/h2o-3/issues/7366) - Decreased the number of displayed features in the heatmap for AutoML inside H2O Explain.
- [[#7378]](https://github.com/h2oai/h2o-3/issues/7378) - Fixed NPE raised from ``weight_column`` not being in the training model.
- [[#7380]](https://github.com/h2oai/h2o-3/issues/7380) - Fixed the ``weight=0`` documentation change error.
- [[#7383]](https://github.com/h2oai/h2o-3/issues/7383) - Fixed failing rotterdam tests.
- [[#7387]](https://github.com/h2oai/h2o-3/issues/7387) - Fixed GAM NPE from multiple runs with knots specified in a frame.
- [[#8458]](https://github.com/h2oai/h2o-3/issues/8458) - Fixed ``col_sample_rate`` not sampling for XGBoost when set to a value lower than 1.0.
- [[#7396]](https://github.com/h2oai/h2o-3/issues/7396) - Fixed wrong column type on MOJO models for Cross-Validation Metrics Summary. 
- [[#7408]](https://github.com/h2oai/h2o-3/issues/7408) - Prevented R connect from starting H2O locally.
- [[#7420]](https://github.com/h2oai/h2o-3/issues/7420) - Added StackedEnsembles to AutoML’s time budget to prevent unexpected training times.
- [[#7441]](https://github.com/h2oai/h2o-3/issues/7441) - Fixed the failing `pyunit_scale_pca_rf.py` test.
- [[#7475]](https://github.com/h2oai/h2o-3/issues/7475) - Improved AutoML behavior when multiple instances are created in parallel.
- [[#7787]](https://github.com/h2oai/h2o-3/issues/7787) - Solved corner cases involving mapping between encoded varimps and predictor columns for H2O Explain by making the varimp feature consolidation more robust.

#### Improvement

- [[#7381]](https://github.com/h2oai/h2o-3/issues/7381) - Ensured that AutoML uses the entire time budget for `max_runtime`.
- [[#7455]](https://github.com/h2oai/h2o-3/issues/7455) - Implemented custom progress widgets for Wave apps using H2O-3.
- [[#7461]](https://github.com/h2oai/h2o-3/issues/7461) - Allowed users to convert floats to doubles with PrintMojo to prevent possible parsing issues.
- [[#7465]](https://github.com/h2oai/h2o-3/issues/7465) - Updated GBM cross validation with ``early_stopping`` to use ``ntrees`` that produce the best score.
- [[#7466]](https://github.com/h2oai/h2o-3/issues/7466) - Enabled ``print_mojo`` to produce .png outputs.
- [[#7470]](https://github.com/h2oai/h2o-3/issues/7470) - Updated Python API for all algorithms and AutoML to retrieve the trained model or leader.
- [[#7476]](https://github.com/h2oai/h2o-3/issues/7476) - Removed algorithm-specific logic from base classes.
- [[#7478]](https://github.com/h2oai/h2o-3/issues/7478) - Added support for scoreContributions for imported MOJOs in Java.
- [[#7480]](https://github.com/h2oai/h2o-3/issues/7480) - Exposed AutoML args as writeable properties until first called to train.
- [[#7482]](https://github.com/h2oai/h2o-3/issues/7482) - Updated XGBoost ``print_mojo`` to now output weights.
- [[#7498]](https://github.com/h2oai/h2o-3/issues/7498) - Removed the Python client dependency on colorama.
- [[#7504]](https://github.com/h2oai/h2o-3/issues/7504) - Added the parameters and their default values to the ``_init_`` function of the Py code generator.
- [[#7535]](https://github.com/h2oai/h2o-3/issues/7535) - Reduced the workspace of the validation frame in GBM by sharing it with the training frame in cross validation.
- [[#7564]](https://github.com/h2oai/h2o-3/issues/7564) - Slightly reduced precision of predictions stored in holdout frames to significantly save on memory.
- [[#7633]](https://github.com/h2oai/h2o-3/issues/7633) - Removed warning in the Stacked Ensemble prediction function about missing ``fold_column`` frame.
- [[#7690]](https://github.com/h2oai/h2o-3/issues/7690) - Enabled returning data from Explain’s `varimp_heatmap` and `model_correlation_matrix`.
- [[#7708]](https://github.com/h2oai/h2o-3/issues/7708) - Exposed the ``top n`` and ``bottom n`` reason codes in Python/R and MOJO.
- [[#12171]](https://github.com/h2oai/h2o-3/issues/12171) - Fixed nightly build version mismatch that prevented the H2OCluster timezone being set to America/Denver.


#### New Feature

- [[#7336]](https://github.com/h2oai/h2o-3/issues/7336) - Implemented a java-self-check to allow users to run on latest Java.
- [[#7343]](https://github.com/h2oai/h2o-3/issues/7343) - Sped up GBM by optimizing the building of histograms.
- [[#7368]](https://github.com/h2oai/h2o-3/issues/7368) - Added a warning to the TreeSHAP reweighting feature if there are 0 weights and updated the API.
- [[#7418]](https://github.com/h2oai/h2o-3/issues/7418) - Added Maximum R Square Improvement (MAXR) algorithm to GLM.
- [[#7424]](https://github.com/h2oai/h2o-3/issues/7424) - Added warning for when H2O doesn’t have enough memory to run XGBoost.
- [[#7431]](https://github.com/h2oai/h2o-3/issues/7431) - Added the ability to specify a custom file name when saving a MOJO.
- [[#7448]](https://github.com/h2oai/h2o-3/issues/7448) - Added output version number of genmodel.jar when printing usage for PrintMojo.
- [[#7536]](https://github.com/h2oai/h2o-3/issues/7536) - Added MOJO to Rulefit.
- [[#7550]](https://github.com/h2oai/h2o-3/issues/7550) - Implemented ability to calculate Shapley values on a re-weighted tree.
- [[#7561]](https://github.com/h2oai/h2o-3/issues/7561) - Implemented H2O ANOVA GLM algorithm for GLM.
- [[#8283]](https://github.com/h2oai/h2o-3/issues/8283) - Improved and consolidated the handling of version mismatch between Python and Backend.
- [[#8500]](https://github.com/h2oai/h2o-3/issues/8500) -  Implemented permutation feature importance for black-box models.
- [[#8501]](https://github.com/h2oai/h2o-3/issues/8501) - Implemented Extended Isolation Forest algorithm.
- [[#9260]](https://github.com/h2oai/h2o-3/issues/9260) - Added support for saving a model directly to S3.

#### Task

- [[#7363]](https://github.com/h2oai/h2o-3/issues/7363) - Fixed the time limits for the Merge/Sort benchmark.
- [[#7454]](https://github.com/h2oai/h2o-3/issues/7454) - Switched removed pandas ``as_matrix`` method to ``.values`` and exposed the interim `pandas.DataFrame` object.
- [[#7533]](https://github.com/h2oai/h2o-3/issues/7533) - Fixed S3 credential for `pyunit_s3_model_save.py` test. 
- [[#7565]](https://github.com/h2oai/h2o-3/issues/7565) - Connected XGBoost aggregation functionality with sorting functionality.

#### Technical task

- [[#7449]](https://github.com/h2oai/h2o-3/issues/7449) - Replaced subsampling in Extended Isolation Forest.

#### Docs

- [[#7348]](https://github.com/h2oai/h2o-3/issues/7348) - Updated the AutoML FAQ.
- [[#7351]](https://github.com/h2oai/h2o-3/issues/7351) - Corrected the ``ignored_columns`` example.
- [[#7356]](https://github.com/h2oai/h2o-3/issues/7356) - Added RMarkdown, Jupyter Notebook, and HTML output example files to H2O Explain documentation.
- [[#7373]](https://github.com/h2oai/h2o-3/issues/7373) - Added Maximum R Improvements (MAXR) GLM documentation.
- [[#7392]](https://github.com/h2oai/h2o-3/issues/7392) - Added the loss function equations for each distribution and link type.
- [[#7405]](https://github.com/h2oai/h2o-3/issues/7405) - Updated the documentation about StackedEnsembles time constraints in AutoML.
- [[#7446]](https://github.com/h2oai/h2o-3/issues/7446) - Clarified that the Explain function only works for supervised models.
- [[#7471]](https://github.com/h2oai/h2o-3/issues/7471) - Added Examine Models section to AutoML documentation.
- [[#7484]](https://github.com/h2oai/h2o-3/issues/7484) - Added documentation for H2O ANOVA GLM algorithm.
- [[#7526]](https://github.com/h2oai/h2o-3/issues/7526) - Fixed the H2O Explain example in the documentation.
- [[#7596]](https://github.com/h2oai/h2o-3/issues/7596) - Updated and gathered Java links to a singular place in the User Guide.

### Zipf (3.32.1.7) - 8/31/2021

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zipf/7/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zipf/7/index.html</a>

#### Bug Fix

- [[#7419]](https://github.com/h2oai/h2o-3/issues/7419) - Fixed predicting issues with imported MOJOs trained with an offset-column.
- [[#7406]](https://github.com/h2oai/h2o-3/issues/7406) - Fixed slow tree building by implementing a switch to turn off the generation of plain language rules.
- [[#7357]](https://github.com/h2oai/h2o-3/issues/7357) - Fixed potential NPE thrown by setting `_orig_projection_array=[]`.
- [[#7346]](https://github.com/h2oai/h2o-3/issues/7346) - Fixed generic model deserialization.
- [[#7345]](https://github.com/h2oai/h2o-3/issues/7345) - Fixed predictions for splits NA vs REST with monotone constraints.

#### New Feature

- [[#7362]](https://github.com/h2oai/h2o-3/issues/7362) - H2O Standalone now uses log4j2 as the logger implementation.


### Zipf (3.32.1.6) - 8/19/2021

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zipf/6/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zipf/6/index.html</a>

#### Bug Fix

- [[#7390]](https://github.com/h2oai/h2o-3/issues/7390) - Fixed the POJO mismatch from MOJO and in-H2O scoring for an unseen categorical value.
- [[#7393]](https://github.com/h2oai/h2o-3/issues/7393) - Simplified duplicated XGBoost parameters in Flow.
- [[#7414]](https://github.com/h2oai/h2o-3/issues/7414) - Fixed broken data frame conversion behavior.

#### Improvement

- Added security updates.

#### New Feature

- [[#7371]](https://github.com/h2oai/h2o-3/issues/7371) - Exposed the ``scale_pos_weight`` parameter in XGBoost.

#### Task

- [[#7412]](https://github.com/h2oai/h2o-3/issues/7412) - Clarified the anomaly score formula used for score calculation within Isolation Forest and Extended Isolation Forest.

#### Docs

- [[#7553]](https://github.com/h2oai/h2o-3/issues/7553) - Added a note on memory usage when using XGBoost to User Guide.

### Zipf (3.32.1.5) - 8/4/2021

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zipf/5/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zipf/5/index.html</a>

#### Bug Fix

- [[#7399]](https://github.com/h2oai/h2o-3/issues/7399) - Modified legacy Dockerfile to add a non-root user.
- [[#7400]](https://github.com/h2oai/h2o-3/issues/7400) - Fixed an issue where running `java -jar h2o.jar -version` failed.
- [[#7403]](https://github.com/h2oai/h2o-3/issues/7403) - Fixed an issue where monotone constraints in GBM caused issues when reproducing the model.
- [[#7407]](https://github.com/h2oai/h2o-3/issues/7407) - Fixed an issue that caused DRF to create incorrect leaf nodes due to rounding errors.
- [[#7409]](https://github.com/h2oai/h2o-3/issues/7409) - Fixed an issue that caused CoxPH MOJO import to fail.
- [[#7411]](https://github.com/h2oai/h2o-3/issues/7411) - Fixed an issue where categorical splits NAvsREST were not represented correctly.
- [[#7413]](https://github.com/h2oai/h2o-3/issues/7413) - Fixed GBM reproducibility for correlated columns with NAs.
- [[#7416]](https://github.com/h2oai/h2o-3/issues/7416) - Fixed h2odriver so that it no longer uses invalid GC options.
- [[#7423]](https://github.com/h2oai/h2o-3/issues/7423) - Fixed GenericModel predictions for non-AUTO categorical encodings.
- [[#7434]](https://github.com/h2oai/h2o-3/issues/7434) - Fixed H2O interaction outcomes.
- [[#7460]](https://github.com/h2oai/h2o-3/issues/7460) - When `remove_collinear_columns=True`, fixed an issue where the dimension of gradient and coefficients changed when predictors were removed.

#### Docs

- [[#7415]](https://github.com/h2oai/h2o-3/issues/7415) - Updated changelog format.

### Zipf (3.32.1.4) - 7/8/2021

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zipf/4/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zipf/4/index.html</a>

#### Bug Fix

- [[#7427]](https://github.com/h2oai/h2o-3/issues/7427) - Fixed h2odriver invalid argument error on Java 11.
- [[#7429]](https://github.com/h2oai/h2o-3/issues/7429) - Fixed GLM `GRADIENT_DESCENT_SQERR` Solver validation.
- [[#7433]](https://github.com/h2oai/h2o-3/issues/7433) - Upgraded to latest version of Javassist (3.28).
- [[#7444]](https://github.com/h2oai/h2o-3/issues/7444) - Fixed H statistic gpu assertion error.
- [[#7456]](https://github.com/h2oai/h2o-3/issues/7456) - Fixed predict contributions failure in multi-MOJO environments.
- [[#7457]](https://github.com/h2oai/h2o-3/issues/7457) - Fixed bug in ordinal GLM class predictions.
- [[#7462]](https://github.com/h2oai/h2o-3/issues/7462) - Fixed Partial Dependent Plot not working with Flow.
- [[#7469]](https://github.com/h2oai/h2o-3/issues/7469) - Updated to current Python syntax.
- [[#7483]](https://github.com/h2oai/h2o-3/issues/7483) - Fixed bug in ordinal GLM class predictions.

#### Improvement

- [[#7509]](https://github.com/h2oai/h2o-3/issues/7509) - Added support for refreshing HDFS delegation tokens for standalone H2O.


#### New Feature

- [[#7540]](https://github.com/h2oai/h2o-3/issues/7540) - Obtained Friedman’s H statistic for XGBoost and GBM.

#### Task

- [[#7500]](https://github.com/h2oai/h2o-3/issues/7500) - Added a warning message when using `alpha` as a hyperparameter for GLM

#### Docs

- [[#7492]](https://github.com/h2oai/h2o-3/issues/7492) - Added section on how to delete objects in Flow.
- [[#7499]](https://github.com/h2oai/h2o-3/issues/7499) - Added a note to the productionizing docs that C++ is only available with additional support.


### Zipf (3.32.1.3) - 5/19/2021

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zipf/3/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zipf/3/index.html</a>

#### Bug Fix

- [[#7514]](https://github.com/h2oai/h2o-3/issues/7514) - Fixed the printing for `auc_pr` and `pr_auc` in cross-validation summaries.

#### New Feature

- [[#7519]](https://github.com/h2oai/h2o-3/issues/7519) - Added parameter `auc_type` to performance method to compute multiclass AUC.

#### Task

- [[#7503]](https://github.com/h2oai/h2o-3/issues/7503) - Upgraded XGBoost predictor to 0.3.18.
- [[#7505]](https://github.com/h2oai/h2o-3/issues/7505) - Increased the timeout duration on the R package jar download.

#### Docs

- [[#7530]](https://github.com/h2oai/h2o-3/issues/7530) - Fixed formatting errors for local builds.
- [[#7558]](https://github.com/h2oai/h2o-3/issues/7558) - Updated docs examples for baseline hazard, baseline survival, and concordance.

### Zipf (3.32.1.2) - 4/29/2021

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zipf/2/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zipf/2/index.html</a>

#### Bug Fix
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7800'>#7800</a>] - Stacked Ensemble will no longer ignore a column if any base model uses it.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7791'>#7791</a>] - Added a user-friendly reminder that the new explainability functions require newer versions of `ggplot2` in R.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7698'>#7698</a>] - NullPointerException error no longer thrown when used a saved and reloaded RuleFit model.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7693'>#7693</a>] - Can now extract metrics from the validation dataset with a Rulefit Model.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7573'>#7573</a>] - Fixed failures from Stacked Ensemble with Multinomial GLM within tests.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7572'>#7572</a>] - Fixed AutoML error when an alpha array is used for GLM.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7570'>#7570</a>] - Fixed “Rollup not possible" stats failure in GLM.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7552'>#7552</a>] - H2O will now still start despite system properties that begin with ‘ai.h2o.’.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7551'>#7551</a>] - H2O exits without logging any buffered messages instead of throwing a NullPointerException when starting H2O with an invalid argument.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7549'>#7549</a>] - ModelDescriptor field in MOJO is now Serializable.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7547'>#7547</a>] - AutoML no longer crashes if model builder produces H2OIllegalArgumentException in the parameter validation phase.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7543'>#7543</a>] - Weights in GLM grid search is no longer used as features.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7529'>#7529</a>] - Fixed Stacked Ensemble MOJO for cases when sub-model doesn’t have the same columns as the metalearner.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7524'>#7524</a>] - Efron-method now fully deterministic in CoxPH.
</li>
</ul>

#### Improvement
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7562'>#7562</a>] - User now allowed to specify the escape character for parsing CSVs.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7557'>#7557</a>] - Added H2O reconnection script for intermittent 401 errors to R.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7548'>#7548</a>] - Added ‘ice_root’ error documented in FAQ.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7531'>#7531</a>] - Added further regularization to the GLM metalearner.
</li>
</ul>

#### New Feature
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9372'>#9372</a>] - Warning now issued against irreproducible model when early stopping is enabled but neither `score_tree_interva`l or `score_each_iteration` are defined.
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7625'>#7625</a>] - Encrypted files that contain CSVs can now be imported.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7577'>#7577</a>] - Added guidelines for correct use of `remove_collinear_columns` for GLM.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7538'>#7538</a>] - Support added for CDP 7.2.
</li>
</ul>

#### Docs
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7582'>#7582</a>] - Added information about the `path` argument for exporting .xlsx files.
</li>
</ul>

### Zipf (3.32.1.1) - 3/25/2021

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zipf/1/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zipf/1/index.html</a>

#### Bug Fix
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9268'>#9268</a>] -         GBM histograms now ignore rows with NA responses.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8032'>#8032</a>] -         Variable Importances added to GLM Generic model.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7859'>#7859</a>] -         Fixed the ArrayIndexOutOfBoundsException issue with GLM CV.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7816'>#7816</a>] -         CoxPH performance no longer fails when a factor is used for the `event_column`.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7801'>#7801</a>] -         Existing frame no longer overwritten when data with the same query is loaded.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7736'>#7736</a>] -         Fixed how `gain` is calculated in XGBFI for GBM.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7711'>#7711</a>] -         Improved the error messages for `save_to_hive_table`.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7685'>#7685</a>] -         Added missing argument ’test’ for `h2o.explain_row()`.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7665'>#7665</a>] -         All trees now supported for XGBoost Print MOJO in Java.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7657'>#7657</a>] -         CoxPH `prediction` no longer fails when `offset_column` is specified.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7678'>#7678</a>] -         Added keys for Individual Conditional Expectation (ICE) plot in H2OExplanation class.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7635'>#7635</a>] -         `model@model$parameters$x` now reports actual feature names instead of `names`.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7632'>#7632</a>] -         `h2o.explain` no longer errors when AutoML object is trained with a `fold_column`.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7603'>#7603</a>] -         Fixed issues with python’s explanation plots not displaying fully.
</li>
</ul>
    
#### New Feature
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7933'>#7933</a>] -         Ignored columns that are actually used for model training are unignored and no longer prevent model training to start in Flow.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7904'>#7904</a>] -         Added baseline hazard function estimate to CoxPH model.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7891'>#7891</a>] -         Target Encoding now supports feature interactions.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7837'>#7837</a>] -         Added CoxPH concordance to both Flow and R/Python CoxPH summaries.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7821'>#7821</a>] -         Added a `topbasemodel` attribute to AutoML.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7811'>#7811</a>] -         Added new learning curve plotting function to R/Python.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7788'>#7788</a>] -         Added script for estimating the memory usage of a dataset.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7784'>#7784</a>] -         Added fault protections to grid search allowing saving of data and parameters, model checkpointing, and auto-recovery.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7761'>#7761</a>] -         Added support for Java 15.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7673'>#7673</a>] -         Added CDP7.1 support.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7666'>#7666</a>] -         Added support for XGBoost to Print MOJO as JSON.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7627'>#7627</a>] -         Added support for refreshing HDFS delegation tokens.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7613'>#7613</a>] -         Reverted XGBoost categorical encodings for contributions.
</li>
</ul>
    
#### Task
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8002'>#8002</a>] -         `max_hit_ratio_k` deprecated and removed.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7751'>#7751</a>] -         Added upper bound cap to supported Java version in H2O CRAN package requirements.
</li>
</ul>
    
#### Improvement
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8165'>#8165</a>] -         Users now allowed to include categorical column name in beta constraints.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8059'>#8059</a>] -         Multinomial PDP can now be plotted for more than one target class in Flow.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7903'>#7903</a>] -          Sped up CoxPH concordance score by using tree instead of the direct approach.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7822'>#7822</a>] -         XGBoost no longer fails when specifying custom `fold_column`.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7799'>#7799</a>] -         XGBoost CV models now built on multiple GPUs in parallel.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8459'>#8459</a>] -         Missing metrics added to GLM scoring history.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7631'>#7631</a>] -         Added validation checks for sampling rates for XGBoost for the R/Python clients.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7624'>#7624</a>] -         
No longer errors when trying to use a fold column where not all folds are represented.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7616'>#7616</a>] -         Added the `metalearner_transform` option to Stacked Ensemble. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7592'>#7592</a>] -         GBM main model now built in parallel to the CV models.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7589'>#7589</a>] -         Removed redundant extraction weights from GBM/DRF histogram.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7588'>#7588</a>] -         GBM now avoids scoring the last iteration twice when early stopping is enabled.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7586'>#7586</a>] -         POJO predictions for XGBoost now even closer to in-H2O predictions.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7585'>#7585</a>] -         Double-scoring of CV models in AutoML now avoided thus speeding up AutoML.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7579'>#7579</a>] -         AutoML now uses fewer neurons in DL grids and has improved the metalearner for Stacked Ensemble.
</li>
</ul>
    
####Technical task
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7783'>#7783</a>] -         Thin plate regression splines added to GAM.
</li>
</ul>
    
#### Docs
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7728'>#7728</a>] -         Added checkpoint description to GLM.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7680'>#7680</a>] -         Added thin plate regression spline documentation to GAM algorithm page.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7656'>#7656</a>] -         Added missing parameters to XGBoost algorithm page.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7652'>#7652</a>] -         Added more information about log files to User Guide.
</li>
</ul>


### Zermelo (3.32.0.5) - 3/16/2021

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zermelo/5/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zermelo/5/index.html</a>

#### Bug Fix
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7844'>#7844</a>] -         GAM no longer creates multiple knots at the same coordinates when the cardinality of the `gam_columns` is less than the number of `knots` specified by the user.
</li>
</ul>
    
#### Improvement
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7694'>#7694</a>] -         
Feature interactions can now be save as .xlxs files. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7614'>#7614</a>] -         Job polling will retry connecting to h2o nodes if connection fails.
</li>
</ul>

### Zermelo (3.32.0.4) - 2/1/2021

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zermelo/4/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zermelo/4/index.html</a>

#### Bug Fix
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7697'>#7697</a>] -         Partial Dependence Plot no longer failing for High Cardinality even when `user_splits` is defined.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7695'>#7695</a>] -         Fixed failing Delta Lake import for Python API.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7686'>#7686</a>] -         Fix Stacked Ensemble’s incorrect handling of fold column.
</li>
</ul>

    
#### Improvement
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7902'>#7902</a>] -         Added MOJO support for CoxPH.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7675'>#7675</a>] -         Escape all quotes by default when writing CSV.
</li>
</ul>
    
#### Docs
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7701'>#7701</a>] -         Added to docs that AUCPR can be plotted.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7684'>#7684</a>] -         Updated the Customer Algorithm graphic for the Architecture section of the User Guide.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7661'>#7661</a>] -         Updated the copyright year to 2021.
</li>
</ul>


### Zermelo (3.32.0.3) - 12/24/2020

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zermelo/3/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zermelo/3/index.html</a>

#### Bug Fix
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7868'>#7868</a>] -         The `pca_impl` parameter is no longer passed to PCA MOJO.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7749'>#7749</a>] -         Objects to be retained no longer removed during the `h2o.removeAll()` command.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7743'>#7743</a>] -         Starting GridSearch in a fresh cluster with new hyperparameters that overlap old ones will no longer cause the old models to be trained again.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7731'>#7731</a>] -         GridSearch no longer hangs indefinitely when not using the default value for paralellism.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7724'>#7724</a>] -         Fixed the parent dir lookup for HDFS grid imports.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7717'>#7717</a>] -         Fixed the CustomDistribution test error.
</li>
</ul>
    
#### New Feature
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12775'>#12775</a>] -         Cross-Validation predictions can now be saved alongside the model.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8366'>#8366</a>] -         Added multinomial and grid search support for AUC/PR AUC metrics.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7782'>#7782</a>] -         Now offers a standalone R client that doesn’t include the h2o jar.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7773'>#7773</a>] -         Created a Red Hat certification for H2O Docker Image.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7764'>#7764</a>] -         Fixed randomized split points for `histogram_type=“Random”` when nbins=2.       
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7729'>#7729</a>] -         Single quote regime for CSV parser exposed for importing & uploading files.
</li>
</ul>
    
#### Improvement
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7887'>#7887</a>] -         REST API disabled on non-leader Kubernetes nodes.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7769'>#7769</a>] -         GLM now uses proper logging instead of printlines.
</li>
</ul>
    
#### Docs
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7819'>#7819</a>] -         
Added non-tree-based models to the variable importance page in the user guide.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7775'>#7775</a>] -         Updated the AutoML citation in the User Guide to point to the H2O AutoML ICML AutoML workshop paper.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7762'>#7762</a>] -         Updated Python docstring examples about cross-validation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7740'>#7740</a>] -         Corrected `k` parameter description for PCA.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7723'>#7723</a>] -         Corrected the RuleFit Python example.
</li>
</ul>


### Zermelo (3.32.0.2) - 11/17/2020

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zermelo/2/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zermelo/2/index.html</a>

#### Bug Fix
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7849'>#7849</a>] -         Implemented deserialization of monotone constraints.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7798'>#7798</a>] -         Updated required version of ggplot2 in R package to 3.3.0.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7778'>#7778</a>] -         Fixed the parsing of GLM’s `rand_family` params in MOJO JSON.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7768'>#7768</a>] -         Fixed NPE that resulted when starting a grid with SequentialWalker in AutoML exploitation phase.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7765'>#7765</a>] -         Fixed MOJO version check message.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7759'>#7759</a>] -         When grid search has parallelism enabled, it now includes CV models.
</li>
</ul>
    
#### New Feature
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7900'>#7900</a>] -         Added feature interactions and importance for XGBoost and GBM.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7867'>#7867</a>] -         Added new `interaction_constraints` parameter to XGBoost.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7804'>#7804</a>] -         Added an option to not have quotes in the header during exportFile.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7758'>#7758</a>] -         Added ability to retrieve a list of all the models in an H2O cluster.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7745'>#7745</a>] -         Added custom pod labels for HELM charts.
</li>
</ul>
    
#### Task
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7807'>#7807</a>] -         Added `lambda_min` & `lambda_max` parameters to GLMModelOutputs.
</li>
</ul>
    
#### Improvement
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7894'>#7894</a>] -         Added default values to all algorithm parameters in the User Guide.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7890'>#7890</a>] -         Fixed the discrepancies between the Target Encoding User Guide page and Client.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7808'>#7808</a>] -         Added ONNX support to the documentation.
</li>
</ul>
    
#### Engineering Story
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7796'>#7796</a>] -         Added a new method which properly locks H2O Frames during conversion from Spark Data Frames to H2O Frames in Sparkling Water.
</li>
</ul>
    
#### Docs
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7806'>#7806</a>] -         On the Grid Search User Guide page, fixed the missing syntax highlight in the Python example of the Random Grid Search section.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7805'>#7805</a>] -         Added `rule_generation_ntrees` parameter to the RuleFit page.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7767'>#7767</a>] -         Added documentation for GBM and XGBoost on feature interactions and importance.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7757'>#7757</a>] -         Added a Python example to the `stratify_by` parameter. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7747'>#7747</a>] -         Added a Feature Engineering section to the Data Manipulation page in the User Guide.
</li>
</ul>


### Zermelo (3.32.0.1) - 10/8/2020

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zermelo/1/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zermelo/1/index.html</a>

#### Bug Fix
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7972'>#7972</a>] -         Fixed StackedEnsemble’s retrieval of the seed parameter value.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7893'>#7893</a>] -         Deserialization values of MOJO ModelParameter now work when the Value Type is int[].
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7881'>#7881</a>] -         H2O no longer uses lazy-loading for sequential zip parse.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7879'>#7879</a>] -         Updated model_type argument names for Rulefit in R.
</li>
</ul>
    
#### New Feature
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8393'>#8393</a>] -         Quantile distributions added to monotone constraints.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8318'>#8318</a>] -          TargetEncoder integrated into ModelBuilder.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7885'>#7885</a>] -         Python client no longer instructs the user to declare a root handler in library mode.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7851'>#7851</a>] -         Hostname used as certificate alias to lookup machine-specific certificate allowing Hadoop users to connect to Flow over HTTPS.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7846'>#7846</a>] -         Added the model explainability interface for H2O models and AutoML objects in both R & Python.
</li>
<li>
[<a href='https://github.com/h2oai/h2o-3/issues/7919'>#7919</a>] -         Added the RuleFit algorithm for interpretability.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7834'>#7834</a>] -         Implemented a basic HELM chart.
</li>
</ul>
    
#### Task
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7878'>#7878</a>] -         Rulefit model added to algorithm section of UserGuide.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7855'>#7855</a>] -         Added an Explainability page to the User Guide outlining the new `h2o.explain()` and `h2o.explain_row()` functions.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7838'>#7838</a>] -         Updated the AutoML User Guide page to include the new Explainability and Preprocessing sections.
</li>
</ul>
    
#### Improvement
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12783'>#12783</a>] -         Added support for Python 3.7+.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7922'>#7922</a>] -         Exposes names of score0 output values in MOJO.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7909'>#7909</a>] -         Added function to plot a Precision Recall Curve.
</li>
<li>
[<a href='https://github.com/h2oai/h2o-3/issues/7899'>#7899</a>] -         RuleFit model represented by the set of rules obtained from trees during training.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7876'>#7876</a>] -         Performance improved for exporting a Frame to CSV.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7872'>#7872</a>] -         GPU backend allowed in XGBoost when running multinode even when `build_tree_one_node` is enabled.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7863'>#7863</a>] -         Updated all URLs in R package to use HTTPS.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7852'>#7852</a>] -         Upgraded to XGBoost 1.2.0.
</li>
</ul>
    
#### Technical task
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8271'>#8271</a>] -         Added cross-validation to GAM allowing users to find the best alpha/lambda values when building a GAM model.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7967'>#7967</a>] -         Added TargetEncoder support for multiclass problems.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7896'>#7896</a>] -         Added new TargetEncoder parameter that allows users to remove original features automatically.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7862'>#7862</a>] -          Implemented minimal support for TargetEncoding in AutoML.
</li>
</ul>
    
#### Docs
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8097'>#8097</a>] -          Updated the descriptions of AutoML in R & Python packages.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7860'>#7860</a>] -         Made the default for `categorical_encoding` in XGBoost explicit in the documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7831'>#7831</a>] -         Updated the import datatype section of the Python FAQ in the User Guide.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7826'>#7826</a>] -         Updated the default values for `min_rule_length` and `max_rule_length` on the RuleFit page of the User Guide.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7825'>#7825</a>] -         Updated the `validation_frame` definition for unsupervised algorithms in the User Guide.
</li>
</ul>

### Zeno (3.30.1.3) - 9/28/2020

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zeno/3/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zeno/3/index.html</a>

<h4>Bug Fix</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7861'>#7861</a>] -         CRAN - Use HTTPS for all downloads within the R package.
</li>
</ul>


### Zeno (3.30.1.2) - 9/3/2020

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zeno/2/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zeno/2/index.html</a>

<h4>Bug Fix</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11601'>#11601</a>] -         The ‘h2o.unique()’ command will now only return the unique values within a column.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8012'>#8012</a>] -         k-LIME easy predict wrapper now uses Regression or KLime as a model category instead of just KLime.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7982'>#7982</a>] -         Fixed the CRAN check warnings on r-devel for cross-references in the R documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7935'>#7935</a>] -         Documentation added detailing the supported encodings for CSV files.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7931'>#7931</a>] -         GLM parameters integrated into GAM parameters.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7901'>#7901</a>] -         Fixed broken URLs in R documentation that caused CRAN failures.
</li>
</ul>
    
<h4>New Feature</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8017'>#8017</a>] -         Added the concordance statistic for CoxPH models.
</li>
</ul>
    
<h4>Task</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8157'>#8157</a>] -         When using multiple alpha/lambda values for calling GLM from GAM, GLM now returns the best results across all alpha/lambda values. Also added the ‘cold_start’ parameter added to GLM.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7918'>#7918</a>] -         Added documentation for new GAM hyperparameter ’subspaces’.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7913'>#7913</a>] -         GLM new parameter ‘cold_start’ added to User Guide and GLM booklet.
</li>
</ul>
    
<h4>Improvement</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7985'>#7985</a>] -         Reduced the memory cost of the `drop_duplicate` operation by cleaning up data early.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7916'>#7916</a>] -          When calculating unique() values on a column that is the result of an AstRowSlice operation, the domain is now collected in-place and no longer results in an error.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7908'>#7908</a>] -         Categorical encoding documentation updated by adding ‘EnumLimited’ & ’SortByReponse’ to KMeans and removing ‘Eigen’ from XGBoost.
</li>
</ul>
    
<h4>Technical task</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8270'>#8270</a>] -         Tests added to verify grid search functionality for GAM and allows the user to create more complex hyper spaces for grid search by adding ‘subspaces’ key and functionality to grid search backend.
</li>
</ul>
    
<h4>Docs</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7906'>#7906</a>] -         Added documentation on how to retrieve reproducibility information.
</li>
</ul>


### Zeno (3.30.1.1) - 8/10/2020

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zeno/1/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zeno/1/index.html</a>

<h4>Bug Fix</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8521'>#8521</a>] -         H2OFrames with fields containing double quotes/line breaks can now be converted to Pandas dataframe. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8149'>#8149</a>] -         Impossible to set Max_depth to unlimited on DRF classifer
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8004'>#8004</a>] -         Model generation for MOJO/POJO are disabled when interaction columns are used in GLM.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7993'>#7993</a>] -         Reproducibility Information Table now hidden in H2O-Flow.
</li>
</ul>
    
<h4>New Feature</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11793'>#11793</a>] -         Added support for `offset_column` in the Stacked Ensemble metalearner.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11794'>#11794</a>] -         Added support for `weights_column` in the Stacked Ensemble metalearner.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8826'>#8826</a>] -         Added continued support to Generalized Additive Models for H2O.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8397'>#8397</a>] -         The value of model parameters can be retrieved at the end of training, allowing users to retrieve an automatically chosen value when a parameter is set to AUTO.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8353'>#8353</a>] -         H2O Frame is now able to be saved into a Hive table.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8171'>#8171</a>] -         XGBoost can now be executed on an external Hadoop cluster.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7999'>#7999</a>] -         Added the `contamination` parameter to Isolation Forest which is used to mark anomalous observations.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7998'>#7998</a>] -         Introduced the `validation_response_column` parameter for Isolation Forest which allows users to name the response column in the validation frame.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7992'>#7992</a>] -         Added official support for Java 14 in H2O.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7942'>#7942</a>] -         Added external cluster startup timeout for XGBoost.
</li>
</ul>
    
<h4>Task</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7990'>#7990</a>] -         Hadoop Docker image run independent of S3.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7966'>#7966</a>] -         Upgraded the build/test environment to support R 4.0 and Roxygen2.7.1.1.
</li>
</ul>
    
<h4>Improvement</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8698'>#8698</a>] -         Implemented TF-IDF algorithm to reflect how important a word is to a document or collection of documents.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8691'>#8691</a>] -         GridSearch R API test added for Isolation Forest.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8193'>#8193</a>] -         ‘AUTO’ option added for GLM & GAM family parameter.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8142'>#8142</a>] -         XGBoost Variable Importances now computed using a Java predictor.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8091'>#8091</a>] -         StackedEnsemble can now be created using only monotone models if user specifies `monotone_constraints` in AutoML.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8071'>#8071</a>] -         Enabled using imported models as base models in Stacked Ensembles.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7988'>#7988</a>] -         Removed deprecated H2O-Scala module.
</li>
</ul>
    
<h4>Technical Task</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8447'>#8447</a>] -          Added Java backend to support MOJO in GAM.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8028'>#8028</a>] -         Added support for `early_stopping` parameter in GAM and GLM.
</li>
</ul>
    
<h4>        Engineering Story
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7938'>#7938</a>] -          Sparkling Water Booklet removed from the H2O-3 repository.
</li>
</ul>
    
<h4>Docs</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8082'>#8082</a>] -         Added H2O Client chapter to the User Guide which includes section on Sklearn integration.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8000'>#8000</a>] -         Added documentation in the Isolation Forest section for the `contamination` parameter.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7991'>#7991</a>] -          Added documentation in GLM & GAM, and the `family` & `link` algorithm parameters to include how `family` can now be set equal to AUTO.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7984'>#7984</a>] -         Added `gains lift_bins` to the parameter appendix and added and example to the parameter in the Python documentation. Added an example for the Kolmogorov-Smirnov metric to the Python documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7983'>#7983</a>] -         Updated GAM and GLM documentation to include support for `early_stopping`.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7978'>#7978</a>] -         Added the Kolmogorov-Smirnov metric formula to the Performance and Prediction chapter.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7960'>#7960</a>] -         Added the `negativebinomial` value to the `family` parameter page.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7959'>#7959</a>] -         Added the `ordinal` and `modified_huber` values to the `distribution` parameter page.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7957'>#7957</a>] -         Updated deprecated parameter `loading_name` to `representation_name` and fixed the broken init link in the GLRM section of the User Guide.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7955'>#7955</a>] -         Added a note in the User Guide Stacked Ensemble section about building a monotonic Stacked Ensemble.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7940'>#7940</a>] -         Added documentation for how `balance_classes` is triggered.
</li>
</ul>


### Zahradnik (3.30.0.7) - 7/21/2020

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zahradnik/7/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zahradnik/7/index.html</a>

<h4>New Feature</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8207'>#8207</a>] -         Added support for partitionBy column in partitioned parquet or CSV files. 
</li>
</ul>
    
<h4>Task</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7994'>#7994</a>] -         Warning added for user if both a lamba value and lambda search are provided in GLM.
</li>
</ul>
    
<h4>Improvement</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12662'>#12662</a>] - Added `max_runtime_secs` parameter to Stacked Ensemble.
</li>
<li>[<a href='https://github.com/h2oai/private-h2o-3/issues/28'>private-#28</a>] - Upgraded Jetty 9 and switched default webserver to Jetty 9.
</li>
</ul>


### Zahradnik (3.30.0.6) - 6/30/2020 

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zahradnik/6/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zahradnik/6/index.html</a>
            
<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8009'>#8009</a>] - GLM Plug values are now propagated to MOJOs/POJOs.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8008'>#8008</a>] - In the Python documentation, the HGLM example now references `random_columns` by indices rather than by column name.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/7997'>#7997</a>] - Fixed a link to H2O blogs in the R documentation.
</li>
</ul>
            
<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8233'>#8233</a>] - Added support for the Kolmogorov-Smirnov metric for binary classification models.
</li>
</ul>
                                                                                                                                                                                                                                                                                                
<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8014'>#8014</a>] - Added documentation in the Performance and Prediction chapter for the Kolmogorov-Smirnov metric.
</li>
</ul>

### Zahradnik (3.30.0.5) - 6/18/2020

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zahradnik/5/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zahradnik/5/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8308'>#8308</a>] - Fixed an issue that denied all requests to display H2O Flow in an iframe.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8075'>#8075</a>] - Importing with `use_temp_table=False` now works correctly on Teradata.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8050'>#8050</a>] - Building a GLM model with `interactions` and `lambda = 0` no longer produces a "Categorical value out of bounds" error. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8048'>#8048</a>] - Fixed an inconsistency that occurred when using `predict_leaf_node_assignment` with a path and with a terminal node. For trees with a max_depth of up to 63, the results now match. For max_depth of 64 or higher (for path and nodes that are "too deep"), H2O will no longer produce incorrect results. Instead it will return "NA" for tree paths and "-1" for node IDs. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8042'>#8042</a>] - Leaf node assignment now works correctly for trees with a depth >= 31. Note that for trees with a max_depth of 64 or higher, H2O will return "NA" for tree paths and "-1" for node IDs. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8039'>#8039</a>] - `allow_insecure_xgboost` now works correctly on Hadoop.
</li>
</ul>

<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8206'>#8206</a>] - HTML documentation is now available as a downloadable zip file.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8037'>#8037</a>] - Users can now retrieve the prediction contributions when running `mojo_predict_pandas` in Python. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8025'>#8025</a>] - H2O documentation is now available in an h2odriver distribution zip file. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8024'>#8024</a>] - Quantiles models during the training of other models are now recognized as a regular model.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8018'>#8018</a>] - The H2O-SCALA module is deprecated and will be removed in a future release.
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9202'>#9202</a>] - Added support for models built with any `family` when running makeGLMModel.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8052'>#8052</a>] - K8S Docker images for h2o-3 are now available. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8023'>#8023</a>] - Warnings are now produced during model building when using the Python client.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8495'>#8495</a>] - Added examples for saving and loading grids in the User Guide. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8051'>#8051</a>] - Improved the examples in the Performance and Prediction chapter. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8049'>#8049</a>] - In the AutoML Random Grid Search Parameters topic, removed the no-longer-supported `min_sum_hessian_in_leaf` parameter from the XGBoost table. Also added clarification on how GHL models are handled in an AutoML random grid search run.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8035'>#8035</a>] - In the Python documentation, add examples for Grid Metrics.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8013'>#8013</a>] - The value of T as described in the description for `categorical_encoding="enum_limited"` is 10, not 1024.
</li>
</ul>


### Zahradnik (3.30.0.4) - 6/1/2020

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zahradnik/4/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zahradnik/4/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8275'>#8275</a>] - h2o.merge() now works correctly when you joining an H2O frame where the join is on a <dbl> column to another frame.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8184'>#8184</a>] - Fixed an issue that caused h2o.get_leaderboard to fail after creating an AutoML object, disconnecting the client, starting a new session, and then reconecting to the running H2O cluster for the re-attached H2OAutoML object.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8147'>#8147</a>] - Stacked Ensemble now inherits distributions/families supported by the metalearner. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8137'>#8137</a>] - Fixed an issue that caused AutoML to fail when the target included special characters.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8073'>#8073</a>] - CAcert is now supported with the Python API.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8069'>#8069</a>] - Water Meter and Form Login now work correctly.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8066'>#8066</a>] - In Aggregator, added support for retrieving the Mappings Frame.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8056'>#8056</a>] - Added support for using monotone constraints with Tweedie distribution in GBM.
</li>
</ul>

<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10207'>#10207</a>] - Added a new drop_duplicates function to drop duplicate observations from an H2O frame.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9371'>#9371</a>] - Partial dependence plots are now available for multiclass problems.
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8134'>#8134</a>] - Users now receive a warning if they try to get variable importances in Stacked Ensemble.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8111'>#8111</a>] - In XGBoost, removed the min_sum_hessian_in_leaf and min_data_in_leaf options, which are no longer supported by XGBoost. Also added the `colsample_bynode` option.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8089'>#8089</a>] - data.table warning messages are now suppressed inside h2o.automl() in R.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8120'>#8120</a>] - Added a "Training Models" section to the User Guide, which describes train() and train_segments(). 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8113'>#8113</a>] - Updated XGBoost to indicate that this version requires CUDA 9, and included information showing users how to check their CUDA version.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8112'>#8112</a>] - Added information about GAM support to the missing_values_handling parameter appendix entry.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8107'>#8107</a>] - Updated the Minio Instance topic.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8064'>#8064</a>] - `monotone_constraints` can now be used with `distribution=tweedie`.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8062'>#8062</a>] - Updated the PDP topic to include support for multinomial problems and updated the examples. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8053'>#8053</a>] - In the API-related Changes topic, noted that `min_sum_hessian_in_leaf` and `min_data_in_leaf`  are no longer supported in XGBoost. 
</li>
</ul>


### Zahradnik (3.30.0.3) - 5/12/2020

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zahradnik/3/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zahradnik/3/index.html</a>

<h4>Bug Fix</h4>

<ul>

<li>[<a href='https://github.com/h2oai/h2o-3/issues/8146'>#8146</a>] - Improved validation and error messages for CoxPH.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8140'>#8140</a>] - In XGBoost, the `predict_leaf_node_assignment` parameter now works correctly with multiclass.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8121'>#8121</a>] - Fixed an issue that caused GBM to fail when it encountered a bin that included a single value and the rest NAs.
</li>
</ul>
    
<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8537'>#8537</a>] - Updated the AutoML example in the R package.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8199'>#8199</a>] - PDPs now allow y-axis scaling options.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8175'>#8175</a>] - Improved speed for training and prediction of Stacked Ensembles.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12851'>#12851</a>] - Added tables showing parameter values and random grid space ranges to the AutoML chapter.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8294'>#8294</a>] - Improved the Hive import documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8138'>#8138</a>] - Improved documentation for Quantiles in the User Guide. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8133'>#8133</a>] - Fixed the documented default value for `min_split_improvement` parameter in XGBoost.
</li>
</ul>



### Zahradnik (3.30.0.2) - 4/28/2020

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zahradnik/2/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zahradnik/2/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8237'>#8237</a>] - Fixed an issue that caused H2O to crash while debugging Python code using intellij/pycharm.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8211'>#8211</a>] - Fixed an issue that caused an assertion error while running Grid Search.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8203'>#8203</a>] - Training of a model based on a data frame that includes Target Encodings no longer fails due to a locked frame.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8198'>#8198</a>] - Added train_segments() to the R html documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8196'>#8196</a>] - Target Encoder now unlocks the output frame.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8182'>#8182</a>] - Fixed the BiasTerm in XGBoost Contributions after upgrading to XGBoost 1.0.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8152'>#8152</a>] - GBM and XGBoost no longer ignore a column that includes a constant and NAs.
</li>
</ul>

<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8284'>#8284</a>] - Added the following options for customizing and retrieving threshold values.
<ul>
<li>`threshold` allows you to specify the threshold value used for calculating the confusion matrix.
<li>`default_threshold` allows you to change the threshold that is used to binarise the predicted class probabilities.</li>
<li>`reset_model_threshold` allows you to reset the model threshold.</li>
</ul>
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8261'>#8261</a>] - Introduced Kubernetes integration. Docker image tests are now available on K8S and published to Docker Hug.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8229'>#8229</a>] - A progress bar is now available during Shap Contributions calculations.
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9209'>#9209</a>] - An H2O Frame containing weights can now be specified when running `make_metrics`. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8361'>#8361</a>] - Added POJO and MOJO support for all encodings in GBM.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8192'>#8192</a>] - Users will now receive an error if they attempt to run https in h2o.init() when starting a local cluster. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8173'>#8173</a>] - Added an `-allow_insecure_xgboost` option to h2o and h2odriver that allows XGBoost multinode to run in a secured cluster.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8169'>#8169</a>] - Only the leader node is exposed on K8S.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8620'>#8620</a>] - Updated the Target Encoding topic and examples based on the improved API. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8293'>#8293</a>] - Added a new "Supported Data Types" topic to the Algorithms chapter.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8195'>#8195</a>] - Added a new "Kubernetes Integration" topic to the Welcome chapter.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8194'>#8194</a>] - Fixed the links for the constrained k-means Python demos.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8191'>#8191</a>] - Fixed the R example in the GAM chapter.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8187'>#8187</a>] - Added clarification for when `min_mem_size` and `max_mem_size`` are set to NULL/None in h2o.init(). 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8179'>#8179</a>] - The link to the slideshare in the DRF chapter now points to https instead of http.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8177'>#8177</a>] - Added information about the h2o.get_leaderboard() function to the AutoML chapter of the User Guide.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8176'>#8176</a>] - Updated the MOJO Quickstart showing how to use PrintMojo to visualize MOJOs without requiring Graphviz.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8168'>#8168</a>] - The import_mojo() function now uses "path" instead of "dir" when downloading, importing, uploading, and saving models. Updated the examples in the documentation.
</li>
</ul>


### Zahradnik (3.30.0.1) - 4/3/2020

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-zahradnik/1/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-zahradnik/1/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8638'>#8638</a>] - Fixed an issue that caused performing multiple h2o.init() to fails with R on Windows.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8545'>#8545</a>] - Increased the default clouding time to avoid times out that resulted in a Cloud 1 under 4 error. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8296'>#8296</a>] - Removed obsolete exactLambdas parameter from GLM.
</li>
</ul>

<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12884'>#12884</a>] - Added support for a fractional response in GLM.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8826'>#8826</a>] - Added support for Generalized Additive Models (GAMs) in H2O. The documentation for this newly added algorithm can be found <a href='http://docs.h2o.ai/h2o/latest-stable/h2o-docs/data-science/gam.html'>here</a>.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8730'>#8730</a>] - Added support for parallel training (e.g. spark_apply in rsparkling or Python/R).
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8404'>#8404</a>] - Added support for Continuous Bag of Words (CBOW) models in Word2Vec.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8369'>#8369</a>] - H2O can now predict OOME during parsing and stop the job if OOME is imminent. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8333'>#8333</a>] - Add GBM POJO support for SortByResponse and enumlimited.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8290'>#8290</a>] - Added support for Leaf Node Assignments in XGBoost and Isolation Forest MOJOs.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8285'>#8285</a>] - Added support for importing Stacked Ensemble MOJO models for scoring. (Note that this only applies to Stacked Ensembles that include algos with MOJO support.)
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8232'>#8232</a>] - Added support for the `single_node_mode` parameter in CoxPH.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8228'>#8228</a>] - H2O now provides the original algorithm name for MOJO import.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8215'>#8215</a>] - Created a segmented model training interface in R. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8214'>#8214</a>] - Added a print method for the H2OSegmentModel object type in R.
</li>
</ul>

<h4>Task</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8401'>#8401</a>] - Removed the previously deprecated DeepWater Estimator function.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8252'>#8252</a>] - Now using Java-based scoring for XGBoostModels.
</li>
</ul>
    
<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11521'>#11521</a>] - In the H2O R package, `data.table` is now enabled by default (if installed). 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9327'>#9327</a>] - In AutoML, users can try tuning the learning rate for the best model found during exploration in XGBoost and GBM. Note that the new `exploitation_ratio` parameter is still experimental. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8781'>#8781</a>] - Added out-of-the-box support for starting an h2o cluster on Kubernetes. Refer to this <a href='https://github.com/h2oai/h2o-3/blob/master/h2o-k8s/README.md'>README</a> for more information. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8553'>#8553</a>] - Improved the way AUC-PR is calculated.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8430'>#8430</a>] - Added an option to upload binary models from Python and R.
</li>
</ul>
                                                                                                                                                                                                                                                    
<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8564'>#8564</a>] - Added examples for Grid Search in the Python Module documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8481'>#8481</a>] - Added examples to the R Reference Guide.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8287'>#8287</a>] - Added documentation for the fractional binomial family in the GLM section.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8286'>#8286</a>] - Added documentation for the new GAM algorithm. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8249'>#8249</a>] - Updated tab formatting for the `cluster_size_constraints` parameter appendix entry.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8231'>#8231</a>] - Updated the Target Encoding R example.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8230'>#8230</a>] - Included confusion matrix threshold details for binary and multiclass classification in the Performance and Prediction chapter.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8227'>#8227</a>] - Added documentation for new `upload_model` function.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8221'>#8221</a>] - Improved documentation around citing H2O in publications. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8209'>#8209</a>] - Added documentation for `single_node_mode` in CoxPH. 
</li>
</ul>


### Yule (3.28.1.3) - 4/2/2020

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-yule/3/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-yule/3/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8300'>#8300</a>] - Fixed an issue that occurred during Hive SQL import with `fetch_mode=SINGLE`; improved Hive SQL import speed; added an option to specify the number of chunks to parse.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8251'>#8251</a>] - Hive delegation token refresh now recognizes `-runAsUser`.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8243'>#8243</a>] - Fixed `base_model` selection for Stacked Ensembles in Flow.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8241'>#8241</a>] - The Parquet parser now supports arbitrary precision decimal types.
</li>
</ul>

<h4>Story</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8246'>#8246</a>] - The H2O Hive parser now recognizes varchar column types.
</li>
</ul>

<h4>Task</h4>

<ul>

<li>[<a href='https://github.com/h2oai/h2o-3/issues/8223'>#8223</a>] - Hive tokens are now refreshed without distributing the Steam keytab.
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8469'>#8469</a>] - Users can now specify the `max_log_file_size` when starting H2O. The log file size currently defaults to 3MB.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8279'>#8279</a>] - Fixed the of parameters for TargetEncoder in Flow.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8247'>#8247</a>] -  HostnameGuesser.isInetAddressOnNetwork is now public.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8235'>#8235</a>] - Improved mapper-side Hive delegation token acquisition. Now when H2O is started from Steam, the Hive delegation token will already be acquired when the cluster is up.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8257'>#8257</a>] - Added to docs that `transform` only works on numerical columns.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8218'>#8218</a>] - Added documentation for the new num_chunks_hint option that can be specified with `import_sql_table`.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8217'>#8217</a>] - Added documentation for the new `max_log_file_size` H2O starting parameter.
</li>
</ul>




### Yule (3.28.1.2) - 3/17/2020

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-yule/2/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-yule/2/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8847'>#8847</a>] - The `base_models` attribute in Stacked Ensembles is now populated in both Python and R. 
<br/>
Note that in Python, if there are no `base_models` in `_parms`, then `actual_params` is used to retrieve base_models, and it contains the names of the models. In R, `ensemble@model$base_models` is populated with a vector of base model names.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8344'>#8344</a>] - Fixed an issue that caused the leader node to be overloaded when parsing 30k+ Parquet files.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8332'>#8332</a>] - Fixed an issue that caused `model end_time` and `run_time` properties to return a value of 0 in client mode.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8280'>#8280</a>] - TargetEncoderModel's summary no longer prints the fold column as a column that is going to be encoded by this model.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8273'>#8273</a>] - When h2omapper fails before discovering SELF (ip & port), the log messages are no longer lost. 
</li>
</ul>

<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8506'>#8506</a>] - Added DeepLearning MOJO support in Generic Models. 
</li>
</ul>

<h4>Improvement\</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9031'>#9031</a>] - Changed the output format of `get_automl` in Python from a dictionary to an object.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8266'>#8266</a>] - Users can now specify `-hdfs_config` multiple times to specify multiple Hadoop config files.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8264'>#8264</a>] - Fixed an issue that caused the clouding process to time out for the Target Encoding module and resulted in a `Cloud 1 under 4` error.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8445'>#8445</a>] - Improved FAQ describing how to use the H2O-3 REST API from Java.
</li>
</ul>

### Yule (3.28.1.1) - 3/5/2020

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-yule/1/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-yule/1/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8314'>#8314</a>] - Added missing AutoML global functions to the Python and R documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8312'>#8312</a>] - In the Python client, improved the H2OFrame documentation and properly labeled deprecated functions.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8303'>#8303</a>] - Fixed an issue that caused imported MOJOs to produce different predictions than the original model.
</li>
</ul>

<h4>Engineering Story</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8310'>#8310</a>] - Removed Sparling Water external backend code from H2O.
</li>
</ul>


<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8309'>#8309</a>] - In the R client docs for h2o.head() and h2o.tail(), added an example showing how to control the number of columns to display in dataframe when using a Jupyter notebook with the R kernel.
</li>
</ul>

### Yu (3.28.0.4) - 2/23/2020

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-yu/4/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-yu/4/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9016'>#9016</a>] - DeepLearning MOJOs are now thread-safe. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8406'>#8406</a>] - Fixed an issue that caused h2oframe.apply to fail when run in Python 3.7. Note that Python 3.7 is still not officially supported, but support is a WIP.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8375'>#8375</a>] - XGBoost now correctly respects monotonicity constraints for all tree_methods.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8373'>#8373</a>] - Decision Tree descriptions no longer include more descriptions than `max_depth` splits.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8365'>#8365</a>] - Fixed an issue that caused `import_hive_table` to fail with a JDBC source and a partitioned table. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8364'>#8364</a>] - Improved the DKVManager sequential removal mechanism.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8356'>#8356</a>] - In XGBoost, added a message indicating that the `exact` tree method is not supported in multinode. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8329'>#8329</a>] - XGBoost ContributionsPredictor is now serializable.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8328'>#8328</a>] - Fixed a CRAN warning related to ellipsis within arguments in the R package.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8325'>#8325</a>] - Added support for specifying AWS session tokens.
</li>
</ul>

<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9179'>#9179</a>] - Added support for Constrained K-Means clustering. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8674'>#8674</a>] - In Stacked Ensembles, added support for "xgboost" and "naivebayes" in the `metalearner_algorithm` parameter.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8334'>#8334</a>] - Added support for `build_tree_one_node` in XGBoost.
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8507'>#8507</a>] - In the R client, users can now optionally specify the number of columns to display in `h2o.frame`, `h2o.head`, and `h2o.tail`. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8443'>#8443</a>] - Fixed an issue that caused AutoML to fail to run if XGBoost was disabled.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8382'>#8382</a>] - Stacktraces are no longer returned in  `h2o.getGrid` when failed models are present.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8327'>#8327</a>] - Added `createNewChunks` with a "sparse" parameter in ChunkUtils. 
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8675'>#8675</a>] - Added an FAQ to the MOJO and POJO quick starts noting that MOJOs and POJOs are thread safe for all supported algorithms.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8420'>#8420</a>] - Added the new `cluster_size_constraints` parameter to the KMeans chapter. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8350'>#8350</a>] - Updated docs to specify that `mtries=-2` gives all features.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8323'>#8323</a>] - Updated EC2 and S3 Storage topic to include the new, optional AWS session token.
</li>
</ul>


### Yu (3.28.0.3) - 2/5/2020

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-yu/3/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-yu/3/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8888'>#8888</a>] - In the R client, fixed a parsing bug that occurred when using quotes with .csv files in as.data.frame().
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8815'>#8815</a>] - Fixed an Unsupported Operation Exception in UDP-TCP-SEND. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8522'>#8522</a>] - GLM now supports coefficients on variable importance when model standardization is disabled.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8446'>#8446</a>] - In the Python client, rbind() can now be used on all numerical types.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8440'>#8440</a>] - In XGBoost, fixed an error that occurred during model prediction when OneHotExplicit was specified during model training. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8429'>#8429</a>] - Performing grid search over Target Encoding parameters now works correctly.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8391'>#8391</a>] - Fixed an issue that caused import_hive_table to not classload the JDBC driver.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8389'>#8389</a>] - MOJOs can now be built from XGBoost models built with an offset column.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8363'>#8363</a>] - Fixed an issue that cause the R and Python clients to return the wrong sensitivity metric value.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8362'>#8362</a>] - Fixed an incorrect sender port calculation in TimestampSnapshot.
</li>
</ul>

<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9128'>#9128</a>] - In AutoML, multinode XGBoost is now enabled by default.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8410'>#8410</a>] - Users can now specify a custom JDBC URL to retrieve the Hive Delegation token using hiveJdbcUrlPattern.
</li>
</ul>

<h4>Task</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8385'>#8385</a>] - In XGBoost fixed a deprecation warning for reg:linear.
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8442'>#8442</a>] - import_folder() can now be used when running H2O in GCS.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8407'>#8407</a>] - Added support for registering custom servlets.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8377'>#8377</a>] - In XGBoost, when a parameter with a synonym is updated, the synonymous parameter is now also updated. 
</li>
</ul>

<h4>Engineering Story</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8388'>#8388</a>] - AutoBuffer.getInt() is now public.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8412'>#8412</a>] - Python examples for plot method on binomial models now use the correct method signature.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8411'>#8411</a>] - Updated custom_metric_func description to indicate that it is not supported in GLM. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8395'>#8395</a>] - Updated the AutoML documentation to indicate that multinode XGBoost is now turned on by default.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8379'>#8379</a>] - Fixed the description for the Hadoop -nthreads parameter.
</li>
</ul>


### Yu (3.28.0.2) - 1/20/2020

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-yu/2/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-yu/2/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8571'>#8571</a>] - Fixed an issue that resulted in a "DistributedException java.lang.ClassNotFoundException: BAD" message.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8499'>#8499</a>] - Users can now specify either a model or a model key when checkpointing.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8490'>#8490</a>] - Fixed an issue that resulted in an endless loop when CsvParser parser $ sign was enclosed in quotes.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8480'>#8480</a>] - In GBM and DRF, fixed an AIOOBE error that occurred when the dataset included negative zeros (-0.0).
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8467'>#8467</a>] - Fixed a race condition in the addWarningP method on Model class.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8455'>#8455</a>] - h2odriver now gets correct version of Hadoop dependencies.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8439'>#8439</a>] - Fixed a race condition in addVec.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8435'>#8435</a>] - Parallel Grid Search threads now call the Hyperspace iterator one at a time.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8431'>#8431</a>] - sklearn wrappers now expose wrapped estimator as a public property.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8428'>#8428</a>] - Fixed an issue in reading user_splits in Java.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8421'>#8421</a>] - Fixed an issue that caused rank vectors of Spearman correlation to have different chunk layouts.
</li>
</ul>

<h4>Task</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8583'>#8583</a>] - Added a JSON option of PrintMojo.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8520'>#8520</a>] - Improved the error message that displays when a user attempts to import data from an HDFS directory that is empty.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8456'>#8456</a>] - H2O can now read Hive table metadata two ways: either via direct Metastore access or via JDBC.
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9167'>#9167</a>] - Improved heuristics used for finding IP addresses on Hadoop in order to select the right subnet automatically. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8605'>#8605</a>] - Added support for `offset_column in XGBoost.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8551'>#8551</a>] - Users can now create tree visualizations without installing additional packages.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8503'>#8503</a>] - Added a new `download_model` function for downloading binary models in the R and Python clients. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8475'>#8475</a>] - Improved XGBoost performance.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8474'>#8474</a>] - When computing the correlation matrix of one or two H2OFrames (using `cor()`), users can now specify a method of either Pearson (default) or Spearman.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8438'>#8438</a>] - Users are now warned when they attempt to run AutoML with a validation frame and with nfolds > 0.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8436'>#8436</a>] - AutoML no longer trains a "Best of Family Stacked Ensemble" when only one family is specified.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12973'>#12973</a>] - Removed `ignored_columns` from the list of available paramters in AutoML.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8647'>#8647</a>] - Fixed a broken link in the JAVA FAQ.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8552'>#8552</a>] - Improved the documentation for Tree Class in the Python Client docs.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8484'>#8484</a>] - Clarified the difference between h2o.performance() and h2o.predict() in the Performance and Prediction chapter of the User Guide.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8478'>#8478</a>] - Incorporated HGLM documentation updates into the GLM booklet.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8441'>#8441</a>] - Added an FAQ for GC allocation failure in the FAQ > Clusters section.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8434'>#8434</a>] - In the Stacked Ensembles chapter, improved the metalearner support FAQ.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8419'>#8419</a>] - Added `offset_column` to the list of supported parameters in XGBoost.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8418'>#8418</a>] - Added information about recent API changes in AutoML to the <a href="http://docs.h2o.ai/h2o/latest-stable/h2o-docs/api-changes.html">API-Related Changes</a> section in the User Guide.
</li>
</ul>


### Yu (3.28.0.1) - 12/16/2019

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-yu/1/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-yu/1/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12823'>#12823</a>] - AutoML reruns using, for example, the same project name, no project name, etc., now produce consistent results.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8924'>#8924</a>] - Fixed an issue that occcurred when running an AutoML instance twice using the same project_name. AutoML no longer appends new models to the existing leaderboard, which caused the models for the first run to attempt to get rescored against the new learderboard_frame.  
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8696'>#8696</a>] - Updated the list of stopping metric options for AutoML in Flow. Also added support for the aucpr stopping metric in AutoML.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8673'>#8673</a>] - When training a K-Means model, the framename is no longer missing in the training metrics.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8642'>#8642</a>] - In AutoML, the `project_name` is now restricted to the same constraints as h2o frames. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8576'>#8576</a>] - In GBM, fixed an NPE that occurred when sample rate < 1.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8575'>#8575</a>] - The AutoML backend no longer accepts `ignored_columns` that contain one of response column, fold column, or weights column.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8505'>#8505</a>] - XGBoost MOJO now works correctly in Spark.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8504'>#8504</a>] - The REST API ping thread now starts after the cluster is up.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8502'>#8502</a>] - Fixed an NPE at hex.tree.TreeHandler.fillNodeCategoricalSplitDescription(TreeHandler.java:272)
</li>
</ul>

<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12219'>#12219</a>] - Extended MOJO support for PCA
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9121'>#9121</a>] - We are very excited to add HGLM (Hierarchical GLM) to our open source offering. As this is the first release, we only implemented the Gaussian family. However, stay tuned or better yet, tell us what distributions you want to see next. Try it out and send us your feedback!
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9117'>#9117</a>] - MOJO Import is now available for XGBoost.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8917'>#8917</a>] - Improved integration of the H2O Python client with Sklearn.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8896'>#8896</a>] - Users can now specify monotonicity constraints in AutoML.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8884'>#8884</a>] - Users can now save and load grids to continue a Grid Search after a cluster restart.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8860'>#8860</a>] - Users can now specify a `parallelism` parameter when running grid search. A value of 1 indicagtes sequential building (default); a value of 0 is used for adapative parallelism; and any value greater than 1 sets the exact number of models built in parallel.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8837'>#8837</a>] - Added a function to calculate Spearman Correlation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8793'>#8793</a>] - Users can now specify the order in which training steps will be executed during an AutoML run. This is done using the new `modeling_plan` option. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8745'>#8745</a>] - The `calibration_frame` and `calibrate_model` options can now be spcified in XGBoost.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8707'>#8707</a>] - Added support for OneHotExplicit categorical encoding in EasyPredictModelWrapper.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8568'>#8568</a>] - Added aucpr to the AutoML leaderboard, stopping_metric, and sort_metric.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8566'>#8566</a>] - An AutoML leaderboard extension is now available that includes model training time and model scoring time.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8558'>#8558</a>] - Exposed the location of Chunks in the REST API.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8544'>#8544</a>] - Added a `rest_api_ping_timeout` option, which can stop a cluster if nothing has touched the REST API for the specified timeout.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8535'>#8535</a>] - Added support for Java 13.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8513'>#8513</a>] - H2O no longer performs an internal self-check when converting trees in H2O. 
</li>
</ul>

<h4>Task</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8840'>#8840</a>] - Fixed an XGBoost error on multinode with AutoML.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8818'>#8818</a>] - Added checkpointing to XGBoost.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8664'>#8664</a>] - Users can now perform random grid search over target encoding hyperparameters
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8582'>#8582</a>] - Improved Grid Search testing in Flow.
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11862'>#11862</a>] - When specifying a `stopping_metric`, H2O now supports lowercase and uppercase characters.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9424'>#9424</a>] - Added a warning message to AutoML if the leaderboard is empty due to too little time for training.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9019'>#9019</a>] - In AutoML, blending frame details were added to event_log.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8879'>#8879</a>] - If early stopping is enabled, GBM can reset the ntree value. In these cases, added an `ntrees_actual` (Python)/`get_ntrees_actual` (R) method to provide the actual ntree value (whether CV is enabled or not) rather than the original ntree value set by the user before building a model. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8808'>#8808</a>] - Refactored AutoML to improve integration with Target Encoding.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8708'>#8708</a>] - Exposed `get_automl` from `h2o.automl` in the Python client.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8701'>#8701</a>] - In GBM POJOs, one hot explicit  EasyPredictModelWrapper now takes care of the encoding, and the user does not need to explicitly apply it.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8670'>#8670</a>] - Added support for numeric arrays to IcedHashMap.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8581'>#8581</a>] - Improved the AutoML Flow UI.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8574'>#8574</a>] - The `mae`, `rmsle`, and `aucpr` stopping metrics are now available in Grid Search.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8567'>#8567</a>] - When creating a hex.genmodel.easy.EasyPredictModelWrapper with contributions enabled, H2O now uses slf4j in the library, giving more control to users about when/where warnings will be printed.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8491'>#8491</a>] - Moved the order of AUCPR in the list of values for `stopping_metric` to right after AUC.
</li>
</ul>

<h4>Engineering Story</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8541'>#8541</a>] - Removed unused code in UDPClientEvent.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8957'>#8957</a>] - Added examples to the Python Module documentation DRF chapter.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8920'>#8920</a>] - Added examples to the Binomial Models section in the Python Python Module documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8905'>#8905</a>] - Added examples to the Multimonial Models section in the Python Python Module documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8903'>#8903</a>] - Added examples to the Clustering Methods section in the Python Module documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8902'>#8902</a>] - Added examples to the Regression section in the Python documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8892'>#8892</a>] - Added examples to the Autoencoder section in the Python documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8891'>#8891</a>] - Added examples to the Tree Class section in the Python documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8872'>#8872</a>] - Added examples to the Assembly section in the Python documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8867'>#8867</a>] - Added examples to the Node, Leaf Node, and Split Leaf Node sections in the Python documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8864'>#8864</a>] - Added examples to the H2O Module section in the Python documentation
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8821'>#8821</a>] - Added examples to the H2OFrame section in the Python documentation
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8804'>#8804</a>] - Documented support for `checkpointing` in XGBoost.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8802'>#8802</a>] - Added examples to the GroupBy section in the Python documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8792'>#8792</a>] - Update to the supported platform table in the XGBoost chapter.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8784'>#8784</a>] - Added R/Python examples to the metrics in Performance and Prediction section of the User Guide.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8782'>#8782</a>] - Added Parameter Appendix entries for CoxPH parameters.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8744'>#8744</a>] - Added examples to the GBM section in the Python documentation
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8728'>#8728</a>] - Added a new Reference entry to the Target Encoding documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8721'>#8721</a>] - Added examples to the KMeans section in the Python documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8712'>#8712</a>] - Added examples to the CoxPH section in the Python documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8697'>#8697</a>] - Added examples to the Deep Learning section in the Python documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8667'>#8667</a>] -  Added examples to the Stacked Ensembles section in the Python documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8661'>#8661</a>] - Added new `use_spnego` option to the Starting H2O in R topic.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8654'>#8654</a>] - Added examples to the Target Encoding section in the Python documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8652'>#8652</a>] - Added examples to the Aggregator section in the Python documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8651'>#8651</a>] - Updated the XGBoost extramempercent FAQ.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8636'>#8636</a>] - Added examples to the PCA section in the Python documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8621'>#8621</a>] - Added a new section for Installing and Starting H2O in the Python Client documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8615'>#8615</a>] - Added examples to the SVD section in the Python documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8604'>#8604</a>] - Improve the R and Python documentation for `search_criteria` in Grid Search.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8546'>#8546</a>] - Added an example using `predict_contributions` to the MOJO quick start.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8524'>#8524</a>] - Added examples to the PSVM section in the Python documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8512'>#8512</a>] - Added documentation for HGLM in the GLM chapter. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8498'>#8498</a>] - Improved AutoML documentation: 
<ul>
<li>aucpr is now an available stopping metric and sort metric for AutoML.</li>
<li>monotone_constraints can now be specified in AutoML.</li>
<li>Added modeling_plan option to list of AutoML parameters.</li>
</ul>
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8497'>#8497</a>] - MOJOs are now available for PCA.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8496'>#8496</a>] - MOJO models are now available for XGBoost.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8494'>#8494</a>] - calibration_frame and calibrate_model are now available in XGBoost.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8493'>#8493</a>] - Added Java 13 to list of supported Java versions.
</li>
</ul>

### [Older Releases](Changes-prior-3.28.0.1.md)
