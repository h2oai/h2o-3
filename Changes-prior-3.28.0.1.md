### [Newer Releases](Changes.md)

### Yau (3.26.0.11) - 12/05/2019

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-yau/11/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-yau/11/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9051'>#9051</a>] - The Python client now fails with descriptive message when attempting to run on an unsupported Java version.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8740'>#8740</a>] - Fixed an issue that caused h2o to fail when running on Hadoop with `-internal_secure_connections`.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8722'>#8722</a>] - H2OGenericEstimator can now be instantiated with no parameters.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8692'>#8692</a>] - Multi-node H2O XGBoost now returns reproducible results.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8645'>#8645</a>] - Fixed the backend default values for the `inflection_point` and `smoothing` parameters in Target Encoder.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8634'>#8634</a>] - Users can now specify the `noise` parameter when running Target Encoding in the R client or in Flow.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8608'>#8608</a>] - MOJO reader now uses stderr instead of stdout to show warnings.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8584'>#8584</a>] - Fixed an issue that allowed SPNEGO athentication to pass with any HTTP-Basic header.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8578'>#8578</a>] - When connecting to H2O via the Python client, users can now specify `allowed_properties="cacert"`.
</li>
</ul>

<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9406'>#9406</a>] - Added BroadcastJoinForTargetEncoding.
</li>
</ul>

<h4>Task</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8669'>#8669</a>] - Introduced AllCategorical and Threshold TE application strategies.
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8588'>#8588</a>] - Added a test to check XGBoost variable importance when trained on frames with shuffled input columns.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8587'>#8587</a>] - The package name for ai.h2o.org.eclipse.jetty.jaas.spi is now independent of the Jetty version.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8580'>#8580</a>] - The `offset_column` is now propogated to MOJO models.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8570'>#8570</a>] - Improved documentation for `stopping_metric` as it pertains to AutoML.
</li>
</ul>


### Yau (3.26.0.10) - 11/7/2019

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-yau/10/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-yau/10/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/private-h2o-3/issues/41'>private-#41</a>] - Fixed an issue that caused H2O to ignore security configurations when running on Hadoop 3.x.
</li>
</ul>

<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8614'>#8614</a>] - Added a `disable_flow` option that can be specified when starting H2O to disable access to H2O Flow.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8599'>#8599</a>] - Version details are now exposed in cloud information.
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8801'>#8801</a>] - Removed duplicate definition for sample_rate in DRF, as this is already defined in shared tree model parameters.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8601'>#8601</a>] - Fixed documentation for Logloss scorer.
</li>
</ul>

### Yau (3.26.0.9) - 10/29/2019

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8803'>#8803</a>] - Fixed an issue that caused sort on a multinode cluster (for example, 2 nodes) to be much slower than a single node cluster. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8702'>#8702</a>] - Fixed an issue that caused class conflicts between the released jars for h2o-genmodel-ext-xgboost and other Java packages. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8683'>#8683</a>] - Export checkpoint no longer fails to export all models created during a grid search.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8630'>#8630</a>] - In the Python client, H2OFrame.drop no longer modifies parameters.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8629'>#8629</a>] - Fixed an issue in the Python Client that caused model.actual_params to sometimes return a <property> object instead of a dict.
</li>
</ul>

<h4>Task</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8663'>#8663</a>] - Fixed an issue that caused XGBoost to exhaust all memory on a node (-xmx+(1.2*-xmx)) on wide datasets.
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8786'>#8786</a>] - Created a Technical Note (TN) describing how to use MOJO Import when importing models from a different H2O version. This TN is available here: <a href="https://github.com/h2oai/h2o-3/discussions/15523">https://github.com/h2oai/h2o-3/discussions/15523</a>. 
</li>
</ul>


### Yau (3.26.0.8) - 10/17/2019

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12867'>#12867</a>] - Fixed and ESPC row_layout assertion error that occurrend when run FrameTest.java.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8761'>#8761</a>] - In AutoML fixed an issue that resulted in poor predictions from SE on MNIST.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8742'>#8742</a>] - When saving files in Python, H2O now assumes the provided path is a directory even when an ending "/" is not included in the path. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8700'>#8700</a>] - The custom distribution function in GBM now works correctly for custom multinomial distributions.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8695'>#8695</a>] - In Target Encoding, added blending of posterior and prior during imputation of unseen values.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8688'>#8688</a>] - Removed H2O.STORE.clear() in FrameTest.java so that the deepSlice test could be enabled.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8681'>#8681</a>] - Python users can now specify `verify_ssl_certificates` and `cacert` when connecting to H2O.  
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8672'>#8672</a>] - Target Encoding now works correctly in Flow.
</li>
</ul>

<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9136'>#9136</a>] - Base models can have different training_frames in blending mode in Stacked Ensembles.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8894'>#8894</a>] - Imported MOJO models now show parameters of the original model.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8807'>#8807</a>] - Added the ability to clone ModelBuilder.
</li>
</ul>

<h4>Task</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8735'>#8735</a>] - R client users can now specify the Boolean `use_spnego` parameter when starting H2O.
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9358'>#9358</a>] - System level proxy is now bypassed when connecting to an H2O instance on localhost from R/Python.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8714'>#8714</a>] - Improved performance by sending data to and from external backend after a specified block size rather than by each item. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8689'>#8689</a>] - Disable HTTP TRACE requests.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8686'>#8686</a>] - Removed the "experimental" note in the AutoML chapter. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8684'>#8684</a>] - Fixed a broken link in XGBoost documentation.
</li>
</ul>

### Yau (3.26.0.6) - 10/1/2019

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-yau/6/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-yau/6/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8769'>#8769</a>] - download_csv/download_all_logs now works correctly on HTTPS when using the Python client.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8765'>#8765</a>] - Fixed an error in PredictCSV unused column detection. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8739'>#8739</a>] - Fixed a potential deadlock issue with the AutoML leaderboard.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8732'>#8732</a>] - Model summary and model_performance output now display correctly in Zeppelin.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8731'>#8731</a>] - Added support for SPNEGO in h2odriver.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8729'>#8729</a>] - Fixed a missing chunk issue on the external backend.
</li>
</ul>

<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8812'>#8812</a>] - In AutoML, you can now retrieve the leadernode using the REST API. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8768'>#8768</a>] - Added support for MAPR 6.0 and 6.1.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8743'>#8743</a>] - Added support for CDH 6.3.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8737'>#8737</a>] - Added POJO support for one hot explicit encoding in GBM.
</li>
</ul>

<h4>Task</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8752'>#8752</a>] - Added ability for countingErrorConsumer example to accumulate counters, not just for variables, but for each variable X and for each variable's value.
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8854'>#8854</a>] - Added a new `melt` function. This is similar to Pandas `melt` and converts an H2OFrame to key-value representation while (optionally) skipping NA values. (This is the inverse operation to pivot.)
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8771'>#8771</a>] - Added POJO support for XGBoost models.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8755'>#8755</a>] - Removed the x-h2o-context-path header from H2O. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8750'>#8750</a>] - Upgraded H2O Flow to 0.10.7.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8838'>#8838</a>] - Moved MOJO Models topic from the Algorithms chapter in the User Guide to the Productionizing chapter.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8773'>#8773</a>] - Added information about GPU usage for XGBoost.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8741'>#8741</a>] - Added MapR 6.0 and 6.1 to list of supported Hadoop platforms.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8725'>#8725</a>] - List all supported Java versions rather than saying Java 8 and greater. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8720'>#8720</a>] - Updated Deep Learning parameter descriptions for `rate`, `rate_annealing`, and `rate_decay`. Also added these to the Parameters Appendix.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8719'>#8719</a>] - Updated the User Guide to indicate that POJOs are available for XGBoost.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8711'>#8711</a>] - Added CDH 6.3 to list of supported Hadoop platforms.
</li>
</ul>


### Yau (3.26.0.5) - 9/16/2019

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-yau/5/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-yau/5/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8749'>#8749</a>] - Fixes a critical bug in Flow: Flow loads but user cannot perform any action.
</li>
</ul>


### Yau (3.26.0.4) - 9/12/2019

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-yau/4/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-yau/4/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9258'>#9258</a>] - Fixed several broken metric methods in the Python and R clients.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8856'>#8856</a>] - The temp folder is no longer deleted after running `h2o.mojo_predict_df`. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8767'>#8767</a>] - Flow now works correctly in environments that already have a context path prefixed.
</li>
</ul>

<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8858'>#8858</a>] - Introduced a Transform operation together with Target Encoding Model. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8820'>#8820</a>] - Added support for CDH 5.15 and and CDH 5.16.
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8839'>#8839</a>] - In the Flow > Models menu, moved MOJO Model to below the list of algorithms and re-labeled it "Import MOJO Model."
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8830'>#8830</a>] - Removed unnecessary read confirmation timeout.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8794'>#8794</a>] - Unified the Target Encoding API arguments with other models - using (x,y).
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8785'>#8785</a>] - Target Encoder ignores non-categorical encoded columns.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8779'>#8779</a>] - Added "fetch mode" option to Flow. As a result, Hive users can now import tables from hive1.X from within Flow. Note that Hive 1.x doesn't support OFFSET. So for Hive 1.x, use import_hive_table or use non-distributed JDBC import (i.e., `fetch node = single`).
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8810'>#8810</a>] - Added `plug_values` to the Parameters Appendix.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8783'>#8783</a>] - Added Python examples to CoxPH options.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8764'>#8764</a>] - Added Teradata to list of supported JDBC types.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8756'>#8756</a>] - Added CDH 5.15 and CDH 5.16 to list of supported Hadoop platforms.
</li>
</ul>


### Yau (3.26.0.3) - 8/23/2019

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-yau/3/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-yau/3/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12974'>#12974</a>] - Fixed an issue that caused an H2OResponseError after initialization of H2OSingularValueDecompositionEstimator.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9303'>#9303</a>] - AstGroup is no longer inconsistent after running it multiple times.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9213'>#9213</a>] - Fixed an issue that caused a mismatch between manual standard deviation and reported standard deviation for cross validation scores. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9027'>#9027</a>] - H2OFrame.split_frame() no longer leaks a _splitter object.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8913'>#8913</a>] - h2o.scale no longer modifies a frame in place. Instead, it now returns a new frame.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8901'>#8901</a>] - Users can export a model using java-rest-bindings.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8880'>#8880</a>] - Sorted grid search results by F2, F0point5 now correctly match the corresponding model metric.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8877'>#8877</a>] - Fixed an issue that caused XGBoost cox2 benchmark to fail with an NPE.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8863'>#8863</a>] - Tables with long titles now displaying properly for users who have installed Pandas.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8852'>#8852</a>] - ModelParametersSchemaV3 now displays the correct help messages.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8849'>#8849</a>] - Fixed an issue that caused the MRTask to fail due to race-condition in creating a new Frame. Note that this issue only occurred when assertions were enabled.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8844'>#8844</a>] - Fixed an issue that caused GLM plots to fail in the Python client.
</li>
</ul>

<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12359'>#12359</a>] - Added another mode to treat missing values: plug values. This value must be given by the user. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8828'>#8828</a>] - Implemented a re-try mechanism for requesting the flatfile on Hadoop.
</li>
</ul>

<h4>Task</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9033'>#9033</a>] - Added Flow support for 2D partial plots.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8850'>#8850</a>] - In Flow, fixed issues with the NPM audit report.
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9064'>#9064</a>] - Added support for predict_leaf_node_assignment() in XGBoost.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8914'>#8914</a>] - In Isolation Forest, improved documentation for aggregate depth and split ratios and described how these two values are calculated.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8908'>#8908</a>] - Removed MissingValuesHandling from XGBoost.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8929'>#8929</a>] - Added links to custom distribution and custom loss function demos.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8906'>#8906</a>] - Removed Java 7 from list of supported Java versions.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8900'>#8900</a>] - Added Shapley example to predict_contributions documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8889'>#8889</a>] - Added H2ONode, H2OLeafNode, and H2OSplitNode to the Python client documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8883'>#8883</a>] - In XGBoost, removed "enum" from the list of available categorical_encoding options.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8873'>#8873</a>] - In GLM improved the documentation for handling of categorical values.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8862'>#8862</a>] - Added predict_leaf_node_assignment to list of supported parameters in XGBoost.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8843'>#8843</a>] - The documentation for PSVM now indicates that it can be used for classification only.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8842'>#8842</a>] - The User Guide now includes all options that can be specified when running h2o.init() from the Python client.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8841'>#8841</a>] - Added bind_to_localhost to list of paramters for h2o.init() in the Python client docs.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8817'>#8817</a>] - Updated GLM parameters. "PlugValues" can now be specified for missing_values_handling, and when specified, a new `plug_values` option is available.  
</li>
</ul>



### Yau (3.26.0.2) - 7/26/2019

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-yau/2/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-yau/2/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12948'>#12948</a>] - Fixed an NPE error that occurred on models StackedEnsemble in AutoML.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9043'>#9043</a>] - Improve the error message for rbind failures that resulted when rbinding datasets with long categorical levels. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8939'>#8939</a>] - In Flow, the scoring history deviance graph no longer displays if a custom distribution is not set.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8923'>#8923</a>] - pr_auc() now works correctly in the Python client.
</li>
</ul>

<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9366'>#9366</a>] - Added support for Target Encoding MOJOs. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9037'>#9037</a>] - Added support for Target Encoding transformation of data without a response column.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8996'>#8996</a>] - Added TargetEncoderBuilder (estimator) and TargetEncoderModel (transformer). 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8950'>#8950</a>] - Added detailed MOJO metrics for DRF, Isolation Forest, and GLM MOJO models.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8948'>#8948</a>] - Added AUCPR to the list of available stopping_metric options. 
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9190'>#9190</a>] - In Flow, users can now upload a MOJO, and a generic model will automatically be created from it. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8951'>#8951</a>] - Removed duplicated code for obtaining logs in Java. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8947'>#8947</a>] - Improved error handling in the downloadLogs method. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8942'>#8942</a>] - Disabled autocomplete on the Flow login form.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8958'>#8958</a>] - Added an entry for upload_custom_metric in the Parameters Appendix.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8954'>#8954</a>] - Added list of parameters that can be specified when building a Generic Model (MOJO import).
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8945'>#8945</a>] - Updated documentation for MOJO Import.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8934'>#8934</a>] - Added "aucpr" to the list of available stopping_metric options.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8926'>#8926</a>] - Added an entry for export_checkpoints_dir in the Parameters Appendix. 
</li>
</ul>


### Yau (3.26.0.1) - 7/15/2019

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-yau/1/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-yau/1/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12456'>#12456</a>] - Removed an unncessary warning in predict function that occcured when a test set was missing `fold_column`.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9265'>#9265</a>] - AutoML no longer continues training models after a job cancellation. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9173'>#9173</a>] - Fixed an issue that caused h2o Docker image builds to fail.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9078'>#9078</a>] - In XGBoost, parallel sparse matrix conversion is no longer using a non-threadsafe API.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9062'>#9062</a>] - AutoML uses a default value of 5 for `score_tree_interval` with all algorithms.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9055'>#9055</a>] - Fixed an issue that caused the Python client API to break when passing a frame to the constructor.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9029'>#9029</a>] - In Flow, you can now specify `blending_frrame`  and `max_runtime_per_model` when running AutoML.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9050'>#9050</a>] - Frame Summary is now available when running the Python client in Zeppelin.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8974'>#8974</a>] - Fixed an issue that caused      H2O.CLOUD._memary(idx).getTimestamp to return 0 rather than the timestamp of the remote node.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8970'>#8970</a>] - Fixed a link function NPE in MOJOs.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8959'>#8959</a>] - Fixed the frame.tocsv signature. Instead of passing true, false, this now takes CSVStreamParams.
</li>
</ul>

<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10966'>#10966</a>] - Added support  for a custom Loss Metric in GBM.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12929'>#12929</a>] - When running AutoML in R or Python, and EventLog is now available. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12930'>#12930</a>] - When polling an AutoML run, an EventLog displays now rather than a progress bar. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12945'>#12945</a>] - CoxPH is now available in the Python client.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12966'>#12966</a>] - Added support for SVM in the h2o-3 R and Python clients.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9138'>#9138</a>] - Added Isolation Forest to Flow.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9120'>#9120</a>] - In XGBoost improved performance of moving sparse matrices to off-heap memory.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9112'>#9112</a>] - Logs from H2O can now be downloaded in plain text format. 
</li>
</ul>

<h4>Task</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12863'>#12863</a>] - Deprecated support for Java 7.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9020'>#9020</a>] - Fixed an issue that caused h2o.scale to corrupt the frame when run over a frame with categorical columns.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9012'>#9012</a>] - Removed the Deep Water booklet from H2O-3 builds. 
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12187'>#12187</a>] - AutoML runtime information is now stored and available in an EventLog.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12737'>#12737</a>] - Users can now pass an ID to training_frame in h2o.StackedEnsemble.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9216'>#9216</a>] - Added early stopping options to Isolation Forest.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9188'>#9188</a>] - Users can now build 2D Partial Dependence plots with the R and Python clients.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9147'>#9147</a>] - When loading MOJOs that were trained on older versions of H2O-3 into newer versions of H2O-3, users can now access all the information that was saved in the model object and use the MOJO to score. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9087'>#9087</a>] - Users can now specify a `row_index` parameter when building PDPs. This allows partial dependence to be calculated for a row.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9077'>#9077</a>] - Users can now specify a `row_index` parameter when building PDPs in Flow.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9058'>#9058</a>] - Enabled Java scoring for XGBoost MOJOs.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9040'>#9040</a>] - User can now delete an AutoML instance and all its dependencies from any client (including models and other dependencies). 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9014'>#9014</a>] - h2o.mojo_predict_csv() and h2o.mojo_predict_pandas() now accept a setInvNumNA parameter.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9010'>#9010</a>] - Added support for TreeShap in DRF.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8985'>#8985</a>] - Added a `feature_frequencies` function in GBM, DRF, and IF, which retrieves the number of times a feature was used on a prediction path in a tree model.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8984'>#8984</a>] - Users can now retrieve variable split information in the Isolation Forest output.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8990'>#8990</a>] - Created a SharedTreeMojoModelWithContributions class, which provides a central location of contribs for DRF and GBM MOJO.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8989'>#8989</a>] - ScoreContributionsTask is no longer abstract.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9174'>#9174</a>] - Clarified in the GLM docs that h2o-3 determines the values of alpha and theta by minimizing the negative log-likelihood plus the same Regularization Penalty. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9130'>#9130</a>] - Create initial, alpha version of SVM documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9076'>#9076</a>] - Added `upload_custom_distribution` to the Parameters Appendix.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9026'>#9026</a>] - Removed note in XGBoost documentation indicating that "Multi-node support is currently available as a Beta feature."
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9023'>#9023</a>] - SVM R client documentation is now available.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9021'>#9021</a>] - Explained how the nthreads parameter can impact reproducibility.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9018'>#9018</a>] - Added stopping parameters to the Isolation Forest chapter.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8994'>#8994</a>] - Fixed the parameters listing display for predict and predict_leaf_node_assignment in the Python documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8992'>#8992</a>] - DRF is now included in the list of supported algorithms for predict_contributions.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8988'>#8988</a>] - Added more examples to the Predict topic.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8980'>#8980</a>] - Improved Data Manipulation Python documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8979'>#8979</a>] - Improved Modeling functions in the Python documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8977'>#8977</a>] - Improved the tree_class Python documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8976'>#8976</a>] - Improved the Model Metrics Python documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8975'>#8975</a>] - Improved GLM documentation by informing users that they can only specify a list in the GLM `interactions` parameter. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8971'>#8971</a>] - Updated Flow documentation to include Isolation Forest.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8968'>#8968</a>] - Improved the Python documentation for h2o.frame(). 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/8967'>#8967</a>] - Added examples to the TargetEncoding Python documentation.
</li>
</ul>


### Yates (3.24.0.5) - 6/18/2019

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-yates/5/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-yates/5/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9237'>#9237</a>] - Fixed a segmentation fault that occurred when running XGBoost with `booster=gblinear`.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9096'>#9096</a>] - Users can now rbind two frames when one frame contains all missing values in some of its columns.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9081'>#9081</a>] - ClearDKVTask now detects shared resources when deleting frames and models.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9038'>#9038</a>] - Fixed a TypeError in Python debugging.
</li>
</ul>

<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9115'>#9115</a>] - Fixed an issue that caused MOJO loading to fail when categorical values contained a newline character.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9100'>#9100</a>] - Users can now export a file directly to a compressed format (gzip) and choose a delimiter.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9082'>#9082</a>] - Users can now specify which certificate alias to use when starting H2O with SSL. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9048'>#9048</a>] - Added Conda install instructions to the download page.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9039'>#9039</a>] - Users can now specify a custom separator for CSV export. 
</li>
</ul>

<h4>Task</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9169'>#9169</a>] - Fixed GLM std-error and Tweedie calculations.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9157'>#9157</a>] - Implemented dispersion factor optimization for Tweedie GLM.
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9168'>#9168</a>] - The MOJO Tree Visualizer and Tree API no longer show categorical splits as numeric and string.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9122'>#9122</a>] - Improved the user experience with Target Encoding in R by providing more meaningful error messages.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9110'>#9110</a>] - Users can now tokenize a frame to the Scala API to enable that using H2O's Word2Vec.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9105'>#9105</a>] - Defined several default values in the R API for Target Encoding.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9103'>#9103</a>] - Improved the user experience with Target Encoding in Python by providing more meaningful error messages.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9101'>#9101</a>] - Set default values for blending hyperparameters in Target Encoding when using the Python client. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9097'>#9097</a>] - Fixed an issue that resulted in a "NaN undefined" label in the Flow cluster status.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9092'>#9092</a>] - Exposed ClearDKVTask via REST API.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9083'>#9083</a>] - H2O-3 now provides a warning when using MOJO prediction with a test/validation dataset that has missing columns.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9056'>#9056</a>] - Upgraded the JTransforms library.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9232'>#9232</a>] - Added a Best Practices sub section to Starting H2O in the User Guide.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9156'>#9156</a>] - Added Target Encoding options to the Parameters appendix.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9114'>#9114</a>] - Updated the description for the Tweedie family in the User Guide and in the GLM booklet.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9093'>#9093</a>] - Removed ologlog and oprobit from list of `link` options that can be specified in GLM.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9063'>#9063</a>] - Upated documentation to indicate that predict_leaf_node_assignment is not supported with XGBoost.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9034'>#9034</a>] - Added the new `-jks_alias` option to list of options that can be specified when starting H2O.
</li>
</ul>

### Yates (3.24.0.4) - 5/28/2019

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-yates/4/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-yates/4/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11194'>#11194</a>] - Fixed an error that occurred when applying as.matrix() to an h2o dataframe with numeric values of size ~ 600K x 300.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/15445'>#15445</a>] - Introduced a new xgboost.predict.native.enable property, which ensures that H2OXGBoostEstimator will no longer always predicts the same value.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9186'>#9186</a>] - Users can now parse files from s3 using s3's directory URL with s3 protocol.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9154'>#9154</a>] - Fixed an issue that caused h2o.getModelTree to produce an "invalid object for slot nas" error when XGBoost produced a root-node only decision tree. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9153'>#9153</a>] - Improved performance of H2OXGBoost on OS X.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9150'>#9150</a>] - In Stacked Ensembles, fixed a categorical encoding mismatch error when building the ensemble. Users can now use SE on top of base models that are trained with categorical encoding.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9146'>#9146</a>] - In Isolation Forest, you can now specify that mtries = the number of features.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9142'>#9142</a>] - Fixed an issue that caused XGBoost to produce a tree with split features being all NA.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9141'>#9141</a>] - In h2o.getModelTree, when retrieving a threshold for values that are all NAs, updated the description to state that the "Split value is NA."
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9140'>#9140</a>] - Fixed an issue that caused trivial features with NAs to be given inflated importance when monotonicity constraints was enabled. As a result, variable importance values were incorrect.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9139'>#9139</a>] - Fixed an NPE issue at water.init.HostnameGuesser when trying to launch a Sparkling Water cluster.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9134'>#9134</a>] - Removed internal_cv_weights from h2o.predict_contributions() output when the prediction was used on a fold column from a model run with nfolds.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9109'>#9109</a>] - Models that use Label Encoding no longer predict incorrectly on test data.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9107'>#9107</a>] - Predictions now work correctly on a subset of training features when using categorical_encoding. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9098'>#9098</a>] - Fixed an issue that caused XGBoost to format non-integer numbers (doubles, floats) using Locale.ENGLISH to ensure that a decimal point "." was used instead of a comma ",".
This locale setting grouped large numbers by thousands and split the groups with ",", which was unparseable to XGBoost.
</li>
</ul>

<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9151'>#9151</a>] - Added support for CDH 6.2.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9127'>#9127</a>] - Users can now specify an external IP for h2odriver callback.
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9111'>#9111</a>] - Added a "toCategoricalCol" helper function for column type conversion.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9108'>#9108</a>] - Renamed "Generic Models" to "MOJO Import" in the documentation. 
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9144'>#9144</a>] - Added CDH 6.2 to list of supported Hadoop platforms.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9119'>#9119</a>] - Added the import_hive_table() and import_mojo() functions to the R HTML documentation.
</li>
</ul>


### Yates (3.24.0.3) - 5/7/2019

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-yates/3/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-yates/3/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12817'>#12817</a>] - Updated H2O-3 Plotting Functionality to be Compatible with Matplotlib Version 3.0.0.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9240'>#9240</a>] - Flow now shows the correct long value of a seed.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9230'>#9230</a>] - Fixed an issue that cause Rapids string operations on enum (categorical) columns to yield counterintuitive results.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9223'>#9223</a>] - Fixed an issue that caused monotonicity constraint in XGBoost to fail with certain parameters
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9218'>#9218</a>] - Fixed an ArrayIndexOutOfBounds error. that occurred when parsing quotes in CSV files.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9210'>#9210</a>] - Fixed an error with Grid Search that caused the API to print errors not related to model CURRENTLY being added to the grid, but for all previous failures. This occurred even when the model was not added to the grid due to failure.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9195'>#9195</a>] - Fixed an exception that occurred when requesting Jobs from h2o.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9187'>#9187</a>] - When using Python 2.7, fixed an issue with non-ascii character handling in the as_data_frame() method. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9177'>#9177</a>] - Predicting on a dataset that has a response column with domain in a different order no longer leads to memory leaks.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9175'>#9175</a>] - Fixed an issue with retrieving details of a GLM model in Flow due to lack of support for long seeds.
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9207'>#9207</a>] - Simplified the directory structure of logs within downloaded zip archives.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9198'>#9198</a>] - Upgrades XGBoost to latest stable build.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9191'>#9191</a>] - Users can how import and upload MOJOs in R and Python using `import_mojo()` and `upload_mojo()`. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9176'>#9176</a>] - It is now possible to retrieve a list of features from a trained model.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12872'>#12872</a>] - Enhanced the GBM Reproducibility FAQ.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9170'>#9170</a>] - Added information about the Target Encoding smoothing parameter to the User Guide. 
</li>
</ul>

### Yates (3.24.0.2) - 4/16/2019

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-yates/2/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-yates/2/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9399'>#9399</a>] - In the R client, fixed a caching issue that caused tests to fail when running commands line by line after running the entire test at once.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9255'>#9255</a>] - Fixed an issue that caused the  h2o.upload_custom_metric to fail when using python3.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9254'>#9254</a>] - Fixed an issue that caused h2o.upload_custom_metric to fail on data that includes strings.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9253'>#9253</a>] - Fixed an issue with the K-Means_Example.flow.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9252'>#9252</a>] - The IP:port that is shown for logging now matches the IP:port that is described in the makeup of the cluster.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9247'>#9247</a>] - In XGBoost, fixed an AIOOB issue that occurred when running large data.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9234'>#9234</a>] - H2O-hive is now published to Maven central.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9231'>#9231</a>] - The Rapids as.factor operation no longer automatically converts non-ASCII strings to sanitized forms.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9229'>#9229</a>] - Fixed an AIOOB error in the AUC builder. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9225'>#9225</a>] - AUCBuilder now finds the first bin to merge when merging per-chunk histograms.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9217'>#9217</a>] - When running H2O on Hadoop, Hadoop now writes only to its container directory.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9208'>#9208</a>] - Users now receive a warning if two different versions of H2O are trying to communicate on the same node.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9205'>#9205</a>] - Fixed an issue that caused the H2O Python package to fail to load on a fresh install from pip.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9193'>#9193</a>] - Fixed an error that occurred when running multiple concurrent Group-By operations.
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9312'>#9312</a>] - The new GCP Marketplace offering contains the option to add a network tags script.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12887'>#12887</a>] - Added Python examples to the Target Encoding topic.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9224'>#9224</a>] - Fixed links to Sparkling Water topics in the Sparkling Water FAQ. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9201'>#9201</a>] - In CoxPH chapter, changed the link for the available R demo.
</li>
</ul>


### Yates (3.24.0.1) - 3/31/2019

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-yates/1/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-yates/1/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12988'>#12988</a>] - The AutoMLTest.java test suite now runs correctly on a local machine.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9430'>#9430</a>] - Fixed an issue in as_date that occurred when the column included NAs.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9411'>#9411</a>] - AutoML no longer fails if one of the Stacked Ensemble models is deleted.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9391'>#9391</a>] - Removed elipses after the H2O server link when launching the Python client.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9390'>#9390</a>] - In Deep Learning, fixed an issue that occurred when running one-hot-encoding on categoricals. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9359'>#9359</a>] - When running GBM in R without specifically setting a seed, users can now extract the seed that was used to build the model and reproduce that model. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9355'>#9355</a>] - In predictions, fixed an issue that resulted in a "Categorical value out of bounds error" when calling a model.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9337'>#9337</a>] - The Python API no longer reverses the labels for positive and negative values in the standardized coefficients plot legend.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9278'>#9278</a>] - In R, fixed an issue that cause group_by mean to only calculate one column when multiple columns were specified.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9274'>#9274</a>] - Fixed an issue that caused the confusion_matrix method to return matrices for other metrics.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9267'>#9267</a>] - Fixed an issue that resulted in a "Categorical value out of bounds error" when calling a model using Python.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9264'>#9264</a>] - Improved the error message that displays when a user attempts to modify an Enum/categorical column as if it were a string. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9257'>#9257</a>] - Rows that start with a # symbol are no longer dropped during the import process.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9256'>#9256</a>] - Fixed an SVM import failure.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9248'>#9248</a>] - Fixed an issue that caused the default StackedEnsemble prediction to fail when applied to a test dataset without a response column.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9245'>#9245</a>] - Fixed handling of BAD state in CategoricalWrapperVec.
</li>
</ul>

<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11559'>#11559</a>] - Added Blending mode to Stacked Ensembles, which can be specified with the `blending_frame` parameter. With Blending mode, you do not use cross-validation preds to train the metalearner. Instead you score the base models on a holdout set and use those predicted values. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12655'>#12655</a>] - Model output now includes column names and types. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12663'>#12663</a>] - AutoML now includes a max_runtime_secs_per_model option.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/15504'>#15504</a>] - In GLM, added support for negative binomial family.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12828'>#12828</a>] - ExposeD Java target encoding to R.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12902'>#12902</a>] - For GBM and XGBoost models, users can now generate feature contributions (SHAP values). 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12968'>#12968</a>] - Added support for Generic Models, which provide a means to use external, pretrained MOJO models in H2O for scoring. Currently only GBM, DRF, IF, and GLM MOJO models are supported. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9439'>#9439</a>] - Added the blending_frame parameter to Stacked Ensembles in Flow.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9423'>#9423</a>] - Added an include_algos parameter to AutoML in the R and Python APIs. Note that in Flow, users can specify exclude_algos only.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9284'>#9284</a>] - In the R and Python clients, added a function that calculates the chunk size based on raw size of the data, number of CPU cores, and number of nodes.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9280'>#9280</a>] - Added ability to import from Hive using metadata from Metastore.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9266'>#9266</a>] - Users can now choose the database where import_sql_select creates a temporary table.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9259'>#9259</a>] - Added support for monotonicity constraints for binomial GBMs.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9250'>#9250</a>] - Users can now define custom HTTP headers using an `-add_http_header` option. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9238'>#9238</a>] - XGBoost MOJO now uses Java predictor by default.
</li>
</ul>

<h4>Task</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11858'>#11858</a>] - Fixed an issue that caused the pyunit_lending_club_munging_assembly_large.py and pyunit_assembly_munge_large.py tests to sometimes fail when run inside a Docker container. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12728'>#12728</a>] - Simplified and improved the GLM COD implementation.
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12357'>#12357</a>] - SQLite support is available via any JDBC driver in streaming mode.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12841'>#12841</a>] - Updated Retrofit and okHttp dependecies.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12961'>#12961</a>] - Target Encoding is now available in the Python client.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9443'>#9443</a>] - Moved StackedEnsembleModel to hex.ensemble packages. In prior versions, this was in a root hex package.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9431'>#9431</a>] - Secret key ID and secret key are available for s3:// AWS protocol. 
<ul><li>This can be done in the R client using:
<br>h2o.setS3Credentials(accessKeyId, accesSecretKey) </li>
<br><li>and in the Python client using:
<br>from h2o.persist import set_s3_credentials
<br>set_s3_credentials(access_key_id, secret_access_key)</li></ul>
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9403'>#9403</a>] - Users can now specify AWS credentials at runtime. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9367'>#9367</a>] - The new blending_frame parameter is now available in AutoML.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9289'>#9289</a>] - Fixed an error in the Javadoc for the Frame.java sort function.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9261'>#9261</a>] - Fixed Hive delegation token generation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9236'>#9236</a>] - Reordered the algorithms train in AutoML and prioritized hardcoded XGBoost models.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11853'>#11853</a>] - Removed FAQ indicating that Java 9 was not yet supported.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12968'>#12968</a>] - Added a "Generic Models" chapter to the Algorithms section.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9440'>#9440</a>] - Added the blending_frame parameter to Stacked Ensembles documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9341'>#9341</a>] - Added information about the Negative Binomial family to the GLM booklet and the user guide.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9331'>PUBDV-6289</a>] - Improved the R and Python client documentation for the `sum` function.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9292'>#9292</a>] - Added include_algos,e xclude_algos, max_models, and max_runtime_secs_per_model examples to the Parameters appendix.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9262'>#9262</a>] - In the User Guide and R an Python documentation, replaced references to "H2O Cloud" with "H2O Cluster". 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9249'>#9249</a>] - Added information about predict_contributions to the Performance and Prediction chapter.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9243'>#9243</a>] - In the GBM chapter, noted that monotone_constraints is available for Bernoulli distributions in addition to Gaussian distributions.
</li>
<li>Improved the GBM Reproducibility FAQ.</li>
</ul>




### Xu (3.22.1.6) - 3/13/2019

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-xu/6/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-xu/6/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9288'>#9288</a>] - In GBM, added a check to ensure that monotonicity constraints can only be used when distribution="gaussian".
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9282'>#9282</a>] - Fixed an issue that caused decreasing monotonic constraints to fail to work correctly. Min-Max bounds are now properly propagated to the subtrees.
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9281'>#9281</a>] - Added internal validation of monotonicity of GBM trees.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9286'>#9286</a>] - Updated the description of monotone_constraints for GBM. This option can only be used for gaussian distributions.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9277'>#9277</a>] - Improved documentation for the EC2 and S3 storage topic for AWS Standalone instances (http://docs.h2o.ai/h2o/latest-stable/h2o-docs/cloud-integration/ec2-and-s3.html#aws-standalone-instance).
</li>
</ul>

### Xu (3.22.1.5) - 3/4/2019

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-xu/5/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-xu/5/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9338'>#9338</a>] - Fixed an issue that caused stratified_split to fail when run on same column twice.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9330'>#9330</a>] - Fixed an error that occurred when retreiving AutoML leader model with max_models = 1 in R. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9328'>#9328</a>] - Fixed an issue that ersulted in an extra NA row in the GLM variable importance frame.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9322'>#9322</a>] - h2odriver now works correctly on MapR.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9320'>#9320</a>] - Flow no longer displays an error when searching for a file without first providing a path. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9317'>#9317</a>] - GBM monotonicity constraints now correctly preserves the exact monotonicity. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9332'>#9332</a>] - Fixed the warning message that displays for categorical data with more then 10,000,000 values.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9316'>#9316</a>] - Users can now download logs from R after connecting via Steam.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9309'>#9309</a>] - In AutoML, created new partition rules for generating new validation and leaderboard frames when cross validation is disabled and validation/leaderboard frames are not provided:
<ul>
<li>If only the validation frame is missing: training/validation = 90/10.
</li>
<li>If only the leaderboard frame is missing: training/leaderboard = 90/10.
</li>
<li>If both the validation and leaderboard frames are missing: training/validation/leaderboard = 80/10/10.
</li>
</ul>
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9301'>#9301</a>] - Fixed resolution of `spark-shell --packages "ai.h2o:h2o-algos:<vesion>"` by Spark Ivy resolver.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9290'>#9290</a>] - Fixed an issue that caused h2o driver to fail to start when Hive was not configured. 
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9350'>#9350</a>] - In Isolation Forest, fixed an issue that caused the minimum and maximum path length to not be correctly calculated when there are no OOB observations.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9326'>#9326</a>] - A `check_constant_response` option is available in DRF and GBM. When enabled (default), then an exception is thrown if the response column is a constant value.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12417'>#12417</a>] - When running XGBoost on Hadoop, recommend that users set -extramempercent to 120. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9334'>#9334</a>] - Added the new check_constant_response option to the GBM and DRF chapters. Also added an example usage to the Parameters Appendix.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9319'>#9319</a>] - Added a description of the AUCPR metric to the Model Performance section in the User Guide.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9308'>#9308</a>] - Fixed the Random Grid Search in Python example in the Grid Search chapter.
</li>
</ul>


### Xu (3.22.1.4) - 2/15/2019

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-xu/4/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-xu/4/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9379'>#9379</a>] - Users can now save and load Isolation Forest models.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9357'>#9357</a>] - In K-Means, fixed and issue in which time columns were treated as if they were categorical.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9354'>#9354</a>] - Fixed Autoencoder `calculateReconstructionErrorPerRowData` error and set the default value of the result MSE to -1.
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/private-h2o-3/issues/47'>private-#47</a>] - When using h2o.import_sql_table to read from a Hive table, the username and password no longer appear in the logs.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9412'>#9412</a>] - Monotone constraints are now exposed in Flow.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9344'>#9344</a>] - The check for constants in response columns is now optional for all models.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12879'>#12879</a>] - Added to the documentation that MOJO/POJO predict cannot parse columns enclosed in double quotes (for example, ""2"").
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9445'>#9445</a>] - Updated the description for Gini in the User Guide. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9436'>#9436</a>] - Fixed the equation for Tweedie Deviance in the GLM booklet and in the User Guide.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9420'>#9420</a>] - Added a "Tokenize Strings" topic to the Data Manipulation chapter. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9376'>#9376</a>] - Added `predict_leaf_node_assignment` information to the User Guide in the Performance and Prediction chapter.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9368'>#9368</a>] - Noted in the documentation that the `custom` and `custom_increasing` stopping metric options are not available in the R client.
</li>
</ul>

### Xu (3.22.1.3) - 1/25/2019

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-xu/3/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-xu/3/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9433'>#9433</a>] - Improved error handling for a wrong Hive JDBC connector error.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9388'>#9388</a>] - Fixed an issue that caused H2O clusters to fail to come up on Cloudera 6 with HTTPS.
</li>
</ul>

<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9404'>#9404</a>] - Added Hive with Kerberos support for H2O on Hadoop. 
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9401'>#9401</a>] - Updated the default value for min_rows in the User Guide when used with XGBoost, DRF, and Isolation Forest.
</li>
</ul>

### Xu (3.22.1.2) - 1/18/2019

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-xu/2/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-xu/2/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/15450'>#15450</a>] - In Flow, fixed an issue that caused POJOs, MOJOs, and genmodel.jar to fail to download. This occurred when Flow was launched via Enterprise Steam and in any deployment where user_context was specified.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9442'>#9442</a>] - Fixed an issue that caused H2OTree to fail with Isolation Forest models trained on data with categorical columns. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9441'>#9441</a>] - When a new tree is assembled from a model, the root node now includes information about the split feature in the description array.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9438'>#9438</a>] -  Fixed an issue where Flow failed to provide the ability to ignore certain columns.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9427'>#9427</a>] - In Flow, fixed an issue where users were not able to select a frame when splitting a dataset. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9422'>#9422</a>] - Setting the `ignored_columns` parameter via the Python API now works correctly.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9421'>#9421</a>] - Fixed an issue that caused H2O to hang in Sparkling Water deployments.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9419'>#9419</a>] - Splitting frames now works correctly in Flow. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9418'>#9418</a>] - Import SQL Table now works correctly in Flow.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9416'>#9416</a>] - Fixed an issue with imports in Flow.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9415'>#9415</a>] - Fixed interaction pairs for GLM in Flow.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9413'>#9413</a>] - Fixed broken "Combine predictions with frame" in Flow.
</li>
</ul>

<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12977'>#12977</a>] - Added support for HDP 3.1.
</li>
</ul>

<h4>Task</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9448'>#9448</a>] - Fixed the pyunit_pubdev_3500_max_k_large.py unit test.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9447'>#9447</a>] - Fixed the runit_PUBDEV_5705_drop_columns_parser_gz.R unit test. 
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9452'>#9452</a>] - Increased the XGBoost stress test timeout.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9431'>#9431</a>] - Implemented secret key credentials for s3:// AWS protocol.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9414'>#9414</a>] - Renamed .jade files to .pug. 
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9454'>#9454</a>] - Added HDP 3.0 and 3.1 to list of supported Hadoop versions.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9429'>#9429</a>] - Updated wording for Kmeans Scoring History Graph. This graph shows the number of iterations vs. within the cluster’s sum of squares.
</li>
</ul>


### Xu (3.22.1.1) - 12/28/2018

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-xu/1/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-xu/1/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12108'>#12108</a>] - PCA tests now work correctly with the "from h2o.estimators.pca import H2OPrincipalComponentAnalysisEstimator" import statement.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12805'>#12805</a>] - Fixed an AutoMLTest test that was leaking keys in KeepCrossValidationFoldAssignment test.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12923'>#12923</a>] - Reduced the Invocation JMH level setup/teardown to only the training model.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12956'>#12956</a>] - In XGBoost, the default value of L2 regularization for tree models is now 1, which is consistent with native XGBoost.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12986'>#12986</a>] - Fixed an issue that caused Stacked Ensembles to fail with GLM metalearner when the same H2O instance was used to train a GLM multinomial classification model with more classes than what is used in Stacked Ensembles.
</li>
</ul>

<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12133'>#12133</a>] - Users can now specify `custom` and `custom_increasing` when setting the `stopping_criteria` parameter in GBM and DRF. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12625'>#12625</a>] - Checkpoints can now be exported when running Grid Search or AutomL. 
</li>
</ul>

<h4>Task</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12746'>#12746</a>] - Added support for CDH 6.0, which includes Hadoop 3 support. Be sure to review <a href="https://www.cloudera.com/documentation/enterprise/6/release-notes/topics/rg_cdh_600_release_notes.html">https://www.cloudera.com/documentation/enterprise/6/release-notes/topics/rg_cdh_600_release_notes.html</a> for more information.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12802'>#12802</a>] - Fixed an AutoMLTest that was leaking keys.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12926'>#12926</a>] - Added a test that runs multiple `nfolds>0` DRF models in parallel.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12983'>#12983</a>] - Added support for CDH 6.1
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12674'>#12674</a>] - Hadoop builds now work with Jetty 8 and 9.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12749'>#12749</a>] - R examples in the R package docs now  use Hadley's style guide.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12894'>#12894</a>] - Added documentation for the new stopping_metric options in GBM and DRF.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12984'>#12984</a>] - Added CDH 6 and 6.1 to list of supported Hadoop versions. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12985'>#12985</a>] - In the XGBoost chapter, updated the default value for reg_lambda to be 1.
</li>
</ul>

### Xia (3.22.0.5) - 1/16/2019

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-xia/5/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-xia/5/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9421'>#9421</a>] - Fixed an H2O hang issue in Sparkling Water deployments.
</li>
</ul>

### Xia (3.22.0.4) - 1/4/2019

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-xia/4/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-xia/4/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/15450'>#15450</a>] - In Flow, fixed an issue that caused POJOs, MOJOs, and genmodel.jar to fail to download. This occurred when Flow was launched via Enterprise Steam and in any deployment where user_context was specified.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9453'>#9453</a>] - On the external backedn, H2O now explicitly passes the timestamp from the Spark Driver node.
</li>
</ul>

### Xia (3.22.0.3) - 12/21/2018

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-xia/3/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-xia/3/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12682'>#12682</a>] - Fixed an issue with the REST API. Calling "get model" no longer returns 0 for the timestamp of the model. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12808'>#12808</a>] - The PySparking client no longer hangs after re-connecting to the H2O external backend.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12838'>#12838</a>] - Fixed an OOM issue in h2o.arrange.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12905'>#12905</a>] - Fixed an issue that caused importing Pargue files with large Double data to fail.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12918'>#12918</a>] - After applying group_by to a time stamped column, the original time stamp format is now retained. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12921'>#12921</a>] - In AutoML, cross-validation metrics are now used for early stopping by default. Because of this, the validation_frame argument is now ignored unless nfolds==0 and, in that case, will be used for early stopping. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12936'>#12936</a>] - Fixed an issue that caused the MOJO visualizer to fail for Isolation Forest models.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12939'>#12939</a>] - StackedEnsembleMojoModel is now serializable. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/15449'>#15449</a>] - In the R client, fixed an error that occurrred when running getModelTree.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/15450'>#15450</a>] - In Flow, fixed an issue that caused POJOs, MOJOs, and genmodel.jar to fail to download. This occurred when Flow was launched via Enterprise Steam and in any deployment where user_context was specified. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12947'>#12947</a>] - Fixed the formula used for calculating L2 distance.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12952'>#12952</a>] - The Python client now allows users to enable XGBoost compare with any H2O frame. The convert_H2OFrame_2_DMatrix method accepts any H2O frame and can convert it to valid data for native XGBoost. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12954'>#12954</a>] - H2O XGBoost now reports correct variable importances. The variable importances are computed from the gains of their respective loss functions during tree construction.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12955'>#12955</a>] - Users can now save PDP plots. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/15454'>#15454</a>] - Fixed an issue that resulted in a SQL exception when connecting H2O to a SQL server and importing a table. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12969'>#12969</a>] - Fixed an issue with GCS support on Hadoop environments. 
</li>
</ul>

<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/14868'>#14868</a>] - Added monotonic variables for GBM.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12877'>#12877</a>] - EasyPredictModelWrapper now calculates reconstruction errors for AutoEncoder. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12931'>#12931</a>] - When running a grid search, a timesteamp column was added that shows when each model was added to the grid summary table. 
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12717'>#12717</a>] - In GBM, users can now specify the `monotone_constraints` parameter.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12944'>#12944</a>] - Prediction contributions from each tree from MOJO to easywrapper are now exposed.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12946'>#12946</a>] - Updated Gradle to version 5.0.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12950'>#12950</a>] - Fixed the output of rankTsv in the AutoML leaderboard.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11266'>#11266</a>] - Updated the Prediction section to include information on how the prediction threshold is selected for classification problems.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12943'>#12943</a>] - Updated the description of enum_limited to indicate that T=1024.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12978'>#12978</a>] - In the GBM chapter, added `monotone_constraints` to list of available parameters.
</li>
</ul>

### Xia (3.22.0.2) - 11/21/2018

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-xia/2/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-xia/2/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10196'>#10196</a>] - Fixed an issue that caused ARFF parser to parse some file incorrectly.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11616'>#11616</a>] - When performing a grid search in Python, fixed an issue that caused all models to return a model.type of "supervised."
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12220'>#12220</a>] - When running DRF in the Python client, checkpointing on new data now works correctly. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12721'>#12721</a>] - Fixed an issue that caused the confusion matrix recall and precision values to be switched.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12883'>#12883</a>] -  In the Python client, fixed an issue that caused the `offset_column` parameter to be ignored when it was passed in the GLM train statement.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12889'>#12889</a>] - The H2O Tree Handler now works correctly on Isolation Forest models.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12893'>#12893</a>] - When running AutoML, fixed an issue that resulted in a "Failed to get metric: auc from ModelMetrics type BinomialGLM" message.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12896'>#12896</a>] - In Flow, Precision and Recall definitions are no longer inverted in the confusion matrix.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12898'>#12898</a>] - Fixed the error message that displays when converting from a pandas dataframe to an h2oframe in Python 3.6.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12900'>#12900</a>] - In XGBoost, fixed an issue that resulted in a "Maximum amount of file descriptors hit" message.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12906'>#12906</a>] - Fixed the description of sample_rate in Isolation Forest. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12909'>#12909</a>] - Cross validation models are no longer deleted by default.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12911'>#12911</a>] - When viewing an AutoML leaderboard, fixed an issue that resulted in an ArrayIndexOutOfBoundsException if `sort_metric` was specified but no model was built.
</li>
</ul>

<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12621'>#12621</a>] - Added monotonicity constraints to H2O XGBoost.
</li>
</ul>

<h4>Task</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12886'>#12886</a>] -  When generating MOJOs, h2o-genmodel.jar now includes a check for MOJO version 1.3 to determine whether the ho2-genmodel.jar and the MOJO version can work together. Prior versions of h2o-3 did not include MOJO 1.3, and as a result, MOJOs silently returned predicted values executed on an empty vector. 
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12561'>#12561</a>] - With a new `skipped_columns` option, users can now specify to drop specific columns before parsing. Note that this functionality is not supported for SVMLight or Avro file formats. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12908'>#12908</a>] - The GLM multinomial coefficient table now includes the original levels as column names.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10134'>#10134</a>] - Created new Performance & Prediction and Variable Importance sections in the User Guide.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12184'>#12184</a>] - Updatd the default value of `categorical_encoding` for XGBoost. This defaults to Auto (which is one_hot_encoding).
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12860'>#12860</a>] - In the parameter entry for `weights_column`, updated the example to exclude the weight column in the list of predictors.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12864'>#12864</a>] - In the DRF FAQ, updated the "What happens when you try to predict on a categorical level not seen during training?" question.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12873'>#12873</a>] - TargetingEncoder is now included in the Python module docs.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12888'>#12888</a>] - In GLM, updated the documentation to indicate that coordinate_descent is no longer experimental. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12910'>#12910</a>] - Added default values for `max_depth`, `sample_size`, and `sample_rate`. Also added a parameter description entry for `sample_size`, showing an Isolation Forest example.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12927'>#12927</a>] - Added the new `monotone_constraints` option to the XGBoost chapter.
</li>
</ul>


### Xia (3.22.0.1) - 10/26/2018

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-xia/1/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-xia/1/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11898'>#11898</a>] - In Python, the metalearner method is only available for Stacked Ensembles.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12517'>#12517</a>] - Fixed an issue that caused micro benchmark tests to fail to run in the jmh directory.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12522'>#12522</a>] - Fixed an issue that caused H2O to fail to export dataframes to S3.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12600'>#12600</a>] - Added the `keep_cross_validation_models` argument to Grid Search.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12601'>#12601</a>] - Improved efficiency of the `keep_cross_validation_models` parameter in AutoML
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12632'>#12632</a>] - Simplified the comparison of H2OXGBoost with native XGBoost when using the Python client.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/15502'>#15502</a>] - Fixed JDBC ingestion for Teradata databases.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12677'>#12677</a>] - In the Python client and the Java API, multiple runs of the same AutoML instance no longer fail training new "Best Of Family" SE models that would include the newly generated models.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12725'>#12725</a>] - Fixed an issue that resulted in an AssertionError when calling `cbind` from the Python client.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12733'>#12733</a>] - AutoML now enforces case for the `sort_metric` option when using the Java API.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12755'>#12755</a>] - In AutoML, StackEnsemble models are now always trained, even if we reached `max_runtime_secs` limit.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12756'>#12756</a>] - In the R client, added documentation for helper functions. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12774'>#12774</a>] - Renamed `x` to `X` in the H2O-sklearn fit method to be consistent with the sklearn API.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12776'>#12776</a>] - Merging datasets now works correctly.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12782'>#12782</a>] - Building on Maven with h2o-ext-xgboost on versions later than 3.18.0.11 no longer results in a dependency error. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12784'>#12784</a>] - Fixed a Java 11 ORC file parsing failure.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12803'>#12803</a>] - Upgraded the version of the lodash package used in H2O Flow.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12815'>#12815</a>] - `-ip localhost` now works correctly on WSL.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12819'>#12819</a>] - CSV/ARFF Parser no longer treats blank lines as data lines with NAs.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12824'>#12824</a>] - Starting h2o-3 from the Python Client no longer fails on Java 10.0.2.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12843'>#12843</a>] - Fixed an issue that caused StackedEnsemble MOJO model to return an  "IllegalArgumentException: categorical value out of range" message.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12844'>#12844</a>] - Removed the "nclasses" parameter from tree traversal routines. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12846'>#12846</a>] - Exposed H2OXGBoost parameters used to train a model to the Python API. Previously, this information was visible in the Java backend but was not passed back to the Python API. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12847'>#12847</a>] - Removed "illegal reflective access" warnings when starting H2O-3 with Java 10.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12852'>#12852</a>] - In Stacked Ensembles, changes made to data during scoring now apply to all models.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12853'>#12853</a>] - When running AutoML in Flow, updated the list of algorithms that can ber selected in the "Exclude These Algorithms" section.
</li>
</ul>

<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12042'>#12042</a>] - Individual predictions of GBM trees are now exposed in the MOJO API. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12245'>#12245</a>] - Exposed target encoding in the Java API.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12266'>#12266</a>] - The `keep_cross_validation_fold_assignment` option is now available in AutoML.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12470'>#12470</a>] - Added support for the Isolation Forest algorithm in H2O-3. Note that this is a Beta version of the algorithm.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12527'>#12527</a>] - Added the  `keep_cross_validation_fold_assignment` option to AutoML in Flow.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12540'>#12540</a>] - `h2o.connect` no longer ignores `strict_version_check=FALSE` when connecting to a Steam cluster.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12551'>#12551</a>] - Created an R demo for CoxPH. This is available <a href="https://github.com/h2oai/h2o-3/blob/master/h2o-r/demos/rdemo.word2vec.craigslistjobtitles.R">here</a>.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12630'>#12630</a>] - It is now possible to combine two models into one MOJO, with the second model using the prediction from the first model as a feature. These models can be from any algorithm or combination of algorithms except Word2Vec. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12704'>#12704</a>] - Implemented h2oframe.fillna(method='backward').
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12825'>#12825</a>] - Improved speed-up of AutoML training on smaller datesets in client mode (Sparkling Water).
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12827'>#12827</a>] - Exposed Java Target Encoding in the Python client.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12836'>#12836</a>] - Users can now specify a `-features` parameter when starting h2o from the command line. This allows users to remove experimental or beta algorithms when starting H2O-3. Available options for this parameter include `beta`, `stable`, and `experimental`. 
</li>
</ul>

<h4>Task</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11391'>#11391</a>] - Added XGBoost to AutoML.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12552'>#12552</a>] - Added an option to allow users to use a user-specified JDBC driver.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12578'>#12578</a>] - Exposed `pr_auc` to areas where you can find AUC, including scoring_history, model summary. Also added h2o.pr_auc() in R.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12753'>#12753</a>] - Added support for Java 11.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12849'>#12849</a>] - Improved the AutoML documentation in the User Guide.
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12451'>#12451</a>] - Added a `MAX_USR_CONNECTIONS_KEY` argument to limit number of sessions for import_sql_table. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12528'>#12528</a>] - Improved performance gap when importing data using Hive2.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12575'>#12575</a>] - Improved and cleaned up output for the h2o.mojo_predict_csv and h2o.mojo_predict_df functions.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12598'>#12598</a>] - Users can now visualize XGBoost trees when running predictions.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12616'>#12616</a>] - Added weights to partial depenced plots. Also added a level for missing values.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12675'>#12675</a>] - Users can now download the genmodel.jar in Flow for completed models. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12738'>#12738</a>] - In AutoML, changed the default for `keep_cross_validation_models` and `keep_cross_validation_predictions` from True to False.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12740'>#12740</a>] - Added support for predicting using the XGBoost Predictor.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12761'>#12761</a>] - In XGBoost, optimized the matrix exchange between Java and native C++ code.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12765'>#12765</a>] - Improved the h2o-3 README for installing in R and IntelliJ IDEA.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12778'>#12778</a>] - Introduced a simple "streaming" mode that allows H2O to read from a table using basic SQL:92 constructs.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12780'>#12780</a>] - In AutoML, `stopping_metric` is now based on `sort_metric`.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12801'>#12801</a>] - The requirements.txt file now includes the Colorama version.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12809'>#12809</a>] - In lockable.java, delete is now final in order to prevent inconsistent overrides.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12812'>#12812</a>] - Reverted AutoML naming change from Auto.Algo to Auto.algo.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12848'>#12848</a>] - In AutoML, automatic partitioning of the valiation frame now uses 10% of the training data instead of 20%. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12850'>#12850</a>] - Changed model and grid indexing in autogenerated model names in AutoML to be 1 instead of 0 indexed.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12865'>#12865</a>] - Allow public access to H2O instances started from R/Python. This can be done with the new `bind_to_localhost` (Boolean) parameter, which can be specified in `h2o.init()`. 
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11389'>#11389</a>] - Added Scala and Java examples to the Building and Extracting a MOJO topic.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11472'>#11472</a>] - Added a Scala example to the Stacked Ensembles topic.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12798'>#12798</a>] - Added Tree class method to the Python module documentation. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12500'>#12500</a>] - Removed references to UDP in the documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12523'>#12523</a>] - Removed Sparkling Water topics from H2O-3 User Guide. These are in the Sparkling Water User Guide. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12533'>#12533</a>] - Added a Resources section to the Overview and included links to the awesome-h2o repository, H2O.ai blogs, and customer use cases.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12549'>#12549</a>] - Updated GCP Installation documentation with infomation about quota limits. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12565'>#12565</a>] - Updated Gains/Lift documentation. 16 groups are now used by default. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12611'>#12611</a>] - Added Python examples to the Cross-Validation topic in the User Guide.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12617'>#12617</a>] - Added `loss_by_col` and `loss_by_col_idx` to list of GLRM parameters.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12664'>#12664</a>] - Updated documentation for `class_sampling_factors`. `balance_classes` must be enabled when using `class_sampling_factors`.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12691'>#12691</a>] - Added a Python example for initializing and starting h2o-3 in Docker.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12709'>#12709</a>] - Updated the Admin menu documentation in Flow after adding "Download Gen Model" option.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12757'>#12757</a>] - In GBM and DRF, `enum_limited` is a supported option for `categorical_encoding`.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12810'>#12810</a>] - Added the -notify_local flag to list of flags available when starting H2O-3 from the command line.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12830'>#12830</a>] - Added documentation for Isolation Forest (beta).
</li>
</ul>

### Wright (3.20.0.10) - 10/16/2018

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-wright/10/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-wright/10/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12474'>#12474</a>] - AutoML now correctly. respects the max_runtime_secs setting.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12708'>#12708</a>] - Fixed a multinomial COD solver bug.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12771'>#12771</a>] - Fixed an issue that caused importing of ARFF files to fail if the header was too large and/or with large datasets with categoricals.
</li>
</ul>

### Wright (3.20.0.9) - 10/1/2018

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-wright/9/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-wright/9/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12781'>#12781</a>] - Fixed an issue that caused H2O to fail when loading a GLRM model. 
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12788'>#12788</a>] - log4j.properties can be loaded from classpath.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12789'>#12789</a>] - Buffer configuration is now available for http/https connections.
</li>
</ul>


### Wright (3.20.0.8) - 9/21/2018

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-wright/8/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-wright/8/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12707'>#12707</a>] - Fixed an issue that occurred when parsing columns that include double quotation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12732'>#12732</a>] - The `max_runtime_secs` option is no longer ignored when using the Python client.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12758'>#12758</a>] - Fixed an XGBoost Sparsity detection test to make it deterministic.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12759'>#12759</a>] - Hadoop driver class no longer fails to parse new Java version string.
</li>
</ul>

<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12713'>#12713</a>] - Added a GBM/DRF Tree walker API in the R client.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12714'>#12714</a>] - The R API for obtaining and traversing model trees in GBM/DRF is available in Python.
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12562'>#12562</a>] - Added  support for user defined split points in partial dependence plots.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12603'>#12603</a>] - Confusion matrices can now be generated in Flow. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12752'>#12752</a>] - Java version error messages now reference versions 7 and 8 instead of 1.7 and 1.8. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12754'>#12754</a>] - A Python tree traversal demo is available at <a href="https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/tree_demo.ipynb">https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/tree_demo.ipynb</a>. 
</li>
</ul>


### Wright (3.20.0.7) - 8/31/2018

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-wright/7/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-wright/7/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12679'>#12679</a>] - Fixed an issue that caused a mismatch between GLRM MOJO predict and GLRM predict.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12693'>#12693</a>] - Fixed an issue that caused H2O XGBoost grid search to fail even when sizing the sessions 4xs the data size and using extramempercent of 150.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12700'>#12700</a>] - When performing multiple AutoML runs using the H2O R client, viewing the first AutoML leaderboard no longer results in an error.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12716'>#12716</a>] - H2O now only binds to the local interface when started from R/Python.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12723'>#12723</a>] - Fixed an issue that caused DeepLearning and XGBoost MOJOs to get a corrupted input row. This occurred when GenModel's helper functions that perform 1-hot encoding failed to take correctly into considerations cases where useAllFactorLevels = false and corrupted the first categorical value in the input row.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12724'>#12724</a>] - Added gamma, tweedie, and poisson objective functions to the XGBoost Java Predictor.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12729'>#12729</a>] - Fixed an issue in HDFS file import. In rare cases the import could fail due to temporarily inconsistent state of H2O distributed memory.
</li>
</ul>


### Wright (3.20.0.6) - 8/24/2018

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-wright/6/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-wright/6/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12580'>#12580</a>] - H2oApi.frameColumn in h2o-bindings.jar now correctly parses responses. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12606'>#12606</a>] - biz.k11i:xgboost-predictor:0.3.0 is now ported to the h2oai repo and released to Maven Central. This allows for easier deployment of H2O and Sparkling Water.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12640'>#12640</a>] - In GLM, the coordinate descent solver is now only disabled for when family=multinomial. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12646'>#12646</a>] - Fixed an issue that caused the H2O parser to hang when reading a Parquet file.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12657'>#12657</a>] -  Fixed an issue that resulted in an AutoML "Unauthorized" Error when running through Enterprise Steam via R.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12672'>#12672</a>] - Leaf Node assignment no longer produces the wrong paths for degenerated trees.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12676'>#12676</a>] - Updated the list of Python dependencies on the release download page and in the User Guide.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12679'>#12679</a>] - Fixed an issue that resulted in a mismatch between GLRM predict and GLRM MOJO predict.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12696'>#12696</a>] - Launching H2O on a machine with greater than 2TB no longer results in an integer overflow error.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12699'>#12699</a>] - The HTTP parser no longer reads fewer rows when the data is compressed.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12703'>#12703</a>] - AstFillNA Rapids expression now returns H2O.unimp() on backward methods.
</li>
</ul>

<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12590'>#12590</a>] - In GBM and DRF, tree traversal and information is now accessible from the R and Python clients. This can be done using the new h2o.getModelTree function.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12634'>#12634</a>] - In GBM, added a new staged_predict_proba function.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12666'>#12666</a>] - MOJO output now includes terminal node IDs. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12684'>#12684</a>] - GBM/DRF, the H2OTreeClass function now allows you to specify categorical levels. 
</li>
</ul>

<h4>Task</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12697'>#12697</a>] - Updated the XGBoost dependency to ai.h2o:xgboost-predictor:0.3.1. 
</li>
</ul>

<h2>Improvement</h2>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12689'>#12689</a>] - Terminal node IDs can now be retrieved in the predict_leaf_node_assignment function.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12688'>#12688</a>] - The User Guide now indicates that only Hive versions 2.2.0 or greater are supported for JDBC drivers. Hive 2.1 is not currently supported.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12690'>#12690</a>] - In GLM, the documentation for the Coordinate Descent solver now notes that Coordinate Descent is not available when family=multinomial. 
</li>
</ul>

### Wright (3.20.0.5) - 8/8/2018

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-wright/5/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-wright/5/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12406'>#12406</a>] - Hive smoke tests no longer time out on HDP.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12647'>#12647</a>] - AutoML now correctly ignores columns specified in Flow.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12648'>#12648</a>] - In Flow, the Import SQL Table button now works correctly.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12660'>#12660</a>] - XGBoost cross validation now works correctly.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12665'>#12665</a>] - Fixed an issue that caused AutoML to fail in Flow due to the keep_cross_validation_fold_assignment option.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12668'>#12668</a>] - Multinomial Stacked Ensemble no longer fails when either XGBoost or Naive Bayes is the base model.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12670'>#12670</a>] - Fixed an issue that caused XGBoost to generate the wrong metrics for multinomial cases.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12673'>#12673</a>] - Increased the client_disconnect_timeout value when ClientDisconnectCheckThread searches for connected clients.
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12667'>#12667</a>] - Added automated Flow test for AutoML.
</li>
</ul>


### Wright (3.20.0.4) - 7/31/2018

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-wright/4/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-wright/4/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12418'>#12418</a>] - In Flow, increased the height of the summary section for the column summary.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12576'>#12576</a>] - Cross-validation now works correctly in XGBoost.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12594'>#12594</a>] - Documentation for the MOJO predict functions (mojo_predict_pandas and mojo_predict_csv) is now available in the Python User Guide. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12599'>#12599</a>] - Regression comparison tests no longer fail between H2OXGBoost and native XGBoost.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12615'>#12615</a>] - GBM/DRF MOJO scoring no longer allocates unnecessary objects for each scored row.
</li>
</ul>

<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12591'>#12591</a>] - In GBM, added point estimation as a metric.
</li>
</ul>

<h4>Task</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12585'>#12585</a>] - Reduced the size of the h2o.jar.
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12295'>#12295</a>] - The h2o.importFile([List of Directory Paths]) function will now import all the files located in the specified folders.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12496'>#12496</a>] - Added Standard Error of Mean (SEM) to Partial Dependence Plots.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12574'>#12574</a>] - Added two new formatting options to hex.genmodel.tools.PrintMojo. The --decimalplaces (or -d) option allows you to set the number of places after the decimal point. The --fontsize (or -f) option allows you to set the fontsize. The default fontsize is 14.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12588'>#12588</a>] - Optimized the performance of ingesting large number of small Parquet files by using sequential parse.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12604'>#12604</a>] - Added support for weights in a calibration frame.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12607'>#12607</a>] - Added a new port_offset command. This parameter lets you specify the relationship of the API port ("web port") and the internal communication port. The previous implementation expected h2o port = api port + 1. Because there are assumptions in the code that the h2o port and API port can be derived from each other, we cannot fully decouple them. Instead, this new option lets the user specify an offset such that h2o port = api port + offset. This enables the user to move the communication port to a specific range, which can be firewalled.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12620'>#12620</a>] - Improved speed of ingesting data from HTTP/HTTPS data sources in standalone H2O.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12550'>#12550</a>] - The User Guide now specifies that XLS/XLSX files must be BIFF 8 format. Other formats are not supported.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12586'>#12586</a>] - Added to docs that when downloading MOJOs/POJOs, users must specify the entire path and not just the relative path.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12629'>#12629</a>] - Added documentation for the new port_offset command when starting H2O.
</ul>

### Wright (3.20.0.3) - 7/10/2018

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-wright/3/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-wright/3/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12221'>#12221</a>] - The `fold_column` option now works correctly in XGBoost. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12423'>#12423</a>] - Calling `describe` on empty H2O frame no longer results in an error in Python. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/15443'>#15443</a>] - In XGBoost, when performing a grid search from Flow, the correct cross validation AUC score is now reported back.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12473'>#12473</a>] - Fixed an issue that cause XGBoost to fail with Tesla V100 drivers 70 and above and with CUDA 9.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12513'>#12513</a>] - H2O's XGBoost results no longer  differ from native XGBoost when dmatrix_type="sparse". 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12531'>#12531</a>] - In the R documentation, fixed the description for h2o.sum to state that this function indicates whether to return an H2O frame or one single aggregated sum.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12532'>#12532</a>] - H2O data import for Parquet files no longer fails on numeric decimalTypes.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12542'>#12542</a>] - Fixed an error that occurred when viewing the AutoML Leaderboard in Flow before the first model was completed.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12545'>#12545</a>] - When connecting to a Linux H2O Cluster from a Windows machine using Python, the `import_file()` function can now correctly locate the file on the Linux Server.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12548'>#12548</a>] - H2O now reports the project version in the logs.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12556'>#12556</a>] - In CoxPH, fixed an issue that caused training to fail to create JSON output when the dataset included too many features.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12563'>#12563</a>] - Users can now switch between edit and command modes on Scala cells.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12577'>#12577</a>] - Fixed an issue with the way that RMSE was calculated for cross-validated models.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12583'>#12583</a>] - In GLRM, fixed an issue that caused differences between the result of h2o.predict and MOJO predictions.
</li>
</ul>

<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12539'>#12539</a>] - Added a new `-report_hostname` flag that can be specified along with `-proxy` when starting H2O on Hadoop. When this flag is enabled, users can replace the IP address with the machine's host name when starting Flow. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12553'>#12553</a>] - Added support for the Amazon Redshift data warehouse. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12581'>#12581</a>] - Added support for CDH 5.9. 
</li>
</ul>

<h4>Task</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12489'>#12489</a>] - Accessing secured (Kerberized) HDFS from a standalone H2O instance works correctly.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12515'>#12515</a>] - AutoML Python tests always use max models to avoid running out of time. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12541'>#12541</a>] - CoxPH now validates that a `stop_column` is specified. `stop_column` is a required parameter.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12547'>#12547</a>] - Fixed an issue that caused a GCS Exception to display when H2O was launched offline.
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12434'>#12434</a>] - In Flow, improved the display of the confusion matrix for multinomial cases. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12524'>#12524</a>] - Users will now see a Precision-Recall AUC when training binomial models.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12525'>#12525</a>] - Synchronous and Asynchronous Scala Cells are now allowed in H2O Flow. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12546'>#12546</a>] - H2O now autodetects string columns and skips them before calculating `groupby`. H2O also warns the user when this happens.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12291'>#12291</a>] - The h2o.mojo_predict_csv and h2o.mojo_predict_df functions now appear in the R HTML documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12558'>#12558</a>] - In GLM, documented that the Poisson family uses the -log(maximum likelihood function) for deviance.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12566'>#12566</a>] - Fixed the R example in the "Replacing Values in a Frame" data munging topic. Columns and rows do not start at 0; R has a 1-based index.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12567'>#12567</a>] - Fixed the R example in the "Group By" data munging topic. Specify the "Month" column instead of the "NumberOfFlights" column when finding the number of flights in a given month based on origin.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12570'>#12570</a>] - Added the new `-report_hostname` flag to the list of Hadoop launch parameters.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12571'>#12571</a>] - Added Amazon Redshift to the list of supported JDBC drivers.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12582'>#12582</a>] - Added CDH 5.9 to the list of supported Hadoop platforms. 
</li>
</ul>

### Wright (3.20.0.2) - 6/15/2018

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-wright/2/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-wright/2/index.html</a>


<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10843'>#10843</a>] - Fixed an issue that resulted in a null pointer exception for H2O ensembles. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12122'>#12122</a>] - In AutoML, ignored_columns are now passed in the API call when specifying both x and a fold_column in during training.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12483'>#12483</a>] - Fixed a bug in documentation that incorrectly referenced 'calibrate_frame' instead of 'calibration_frame'. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12490'>#12490</a>] - java -jar h2o.jar no longer fails on Java 7.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12493'>#12493</a>] - Fixed a typo in the AutoML pydocs for sort_metric.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12510'>#12510</a>] - Exported CoxPH functions in R.
</li>
</ul>

<h4>Task</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12482'>#12482</a>] - Added balance_classes, class_sampling_factors, and max_after_balance_size options to AutoML in Flow.
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10651'>#10651</a>] - Updated the project URL, bug reports link, and list of authors in the h2o R package DESCRIPTION file. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12405'>#12405</a>] - Update description of the h2o R package in the DESCRIPTION file.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12432'>#12432</a>] - AutoML now produces an error message when a response column is missing.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12484'>#12484</a>] - Fixed intermittent test failures for AutoML.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12486'>#12486</a>] - Removed frame metadata calculation from AutoML.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12494'>#12494</a>] - Removed the keep_cross_validation_models = False argument from the AutoML User Guide examples.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12495'>#12495</a>] - Users can now set a MAX_CM_CLASSES parameter to set a maximum number of confusion matrix classes.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12480'>#12480</a>] - Updated the AutoML screenshot in Flow to show the newly added parameters. 
</li>
</ul>


### Wright (3.20.0.1) - 6/6/2018

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-wright/1/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-wright/1/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11188'>#11188</a>] - In Scala, the `new H2OFrame()` API no longer fails when using http/https URL-based data sources.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11743'>#11743</a>] - Fixed an issue that caused the Java client JVM to get stuck with a latch/lock leak on the server.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12210'>#12210</a>] - Fixed an issue that caused intermittent NPEs in AutoML. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12225'>#12225</a>] - In parse, each lock now includes the owner rather than locking with null.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12227'>#12227</a>] - LDAP documentation now contains the correct name of the Auth module.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12293'>#12293</a>] - h2o.jar no longer includes a Jetty 6 dependency.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12328'>#12328</a>] - `model_summary` is now available when running Stacked Ensembles in R.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12344'>#12344</a>] - XGBoost now correctly respects the H2O `nthreads` parameter.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12354'>#12354</a>] - Fixed an invalid invariant in the recall calculation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12358'>#12358</a>] - h2o-genmodel.jar can now be loaded into Spark's spark.executor.extraClassPath.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12367'>#12367</a>] - AutoML now correctly detects the leaderboard frame in H2O Flow.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12388'>#12388</a>] - In XGBoost, fixed an issue that resulted in a "Check failed: param.max_depth < 16 Tree depth too large" error.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12414'>#12414</a>] - Zero decimal values and NAs are now represented correctly in XGBoost.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12415'>#12415</a>] - Response variable datatype checks are now extended to include TIME datatypes.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12459'>#12459</a>] - The `-proxy` argument is now available as part of the h2odriver.args file.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12466'>#12466</a>] - Fixed `stopping_metric` values in user guide. Abbreviated values should be specified using upperchase characters (for example, MSE, RMSE, etc.).
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12471'>#12471</a>] - Proxy Mode of h2odriver now supports a notification file (specified with the `-notify` argument).
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12478'>#12478</a>] - Fixed an issue that caused h2o.predict to throw an exception in H2OCoxPH models with interactions with stratum.
</li>
</ul>

<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10797'>#10797</a>] - Added MOJO support in Python (via jar file).
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11805'>#11805</a>] - Added the `sort_metric` argument to AutoML.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11817'>#11817</a>] - Users now have the option to save CV predictions and CV models in AutoML.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11845'>#11845</a>] - Added an `h2o.H2OFrame.rename` method to rename columns in Python.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11867'>#11867</a>] - MOJO and POJO support are now available for AutoML. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11895'>#11895</a>] - Added support for the Cox Proportional Hazard (CoxPH) algorithm. Note that this is currently available in R and Flow only. It is not yet available in Python.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12049'>#12049</a>] - Added h2o.get_automl()/h2o.getAutoML function to R/Python APIs. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12244'>#12244</a>] - Added the `balance_classes`, `class_sampling_factors`, and max_after_balance_size` arguments to AutoML. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12275'>#12275</a>] - When running GLM in Flow, users can now see the  InteractionPairs option.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12291'>#12291</a>] - Added support for MOJO scoring on a CSV or data frame in R. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12318'>#12318</a>] - Added an "export model as MOJO" button to Flow for supported algorithms. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12384'>#12384</a>] - Added support for XGBoost MOJO deployment on Windows 10.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12393'>#12393</a>] - GBM and DRF MOJOs and POJOs now return leaf node assignments.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12460'>#12460</a>] - Added the `sort_metric` option to AutoML in Flow.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12461'>#12461</a>] - keep_cross_validation_predictions and keep_cross_validation_models are now available when running AutoML in Flow. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12476'>#12476</a>] - Deep Learning MOJO now extends Serializable.
</li>
</ul>

<h4>Story</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12265'>#12265</a>] - In CoxPH, when a categorical column is only used for a numerical-categorical interaction, the algorithm will enforce useAllFactorLevels for that interaction.
</li>
</ul>

<h4>Task</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11453'>#11453</a>] - When running AutoML and XGBoost, fixed an issue that caused the adapting test frame to be different than the train frame.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11705'>#11705</a>] - Removed Domain length check for Stacked Ensembles.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11931'>#11931</a>] - GLRM predict no longer generates different outputs when performing predictions on training and testing dataframes. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12235'>#12235</a>] - Added support for ingesting data from Hive2 using SQLManager (JDBC interface). Note that this is experimental and is not yet suitable for large datasets.
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11264'>#11264</a>] - Replaced the Jama SVD computation in PCA with netlib-java library MTJ.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11402'>#11402</a>] - Created more tests in AutoML to ensure that all fold_assignment values and fold_column work correctly.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11454'>#11454</a>] - Fixed an NPE the occurred when clicking on View button while running AutoML.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11463'>#11463</a>] - Bundled Windows XGboost libraries.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11500'>#11500</a>] - Search-based models are no longer duplicated when AutoML is run again on the same dataset with the same seed.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11597'>#11597</a>] - When running Stacked Ensembles in R, added support for a vector of base_models in addition to a list.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11833'>#11833</a>] - Added support for Java 9.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12255'>#12255</a>] - Fixed an issue that resulted in an additional progress bar when running h2o.automl() in R.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12278'>#12278</a>] - Fixed an issue that resulted in an additional progress bar when running AutoML in Python.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12306'>#12306</a>] - The runint_automl_args.R test now always builds at least 2 models. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12325'>#12325</a>] - Improved XGBoost speed by not recreating DMatrix in each iteration (during training).
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12342'>#12342</a>] - `offset_column` is now exposed in EasyPredictModelWrapper.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12343'>#12343</a>] - Improved single node XGBoost performance.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12352'>#12352</a>] - Added support for pip 10.0.0.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12363'>#12363</a>] - In GLM, gamma distribution with 0's in the response results in an improved message: "Response value for gamma distribution must be greater than 0."
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12365'>#12365</a>] - Added metrics to AutoML leaderboard. Binomial models now also show mean_per_class_error, rmse, and mse. Multinomial problems now also show logloss, rmse and mse. Regression models now also show mse.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12397'>#12397</a>] - Exposed `model dump` in XGBoost MOJOs.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12401'>#12401</a>] - Improved rebalance for Frames. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12416'>#12416</a>] - Introduced the precise memory allocation algorithm for XGBoost sparse matrices.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12438'>#12438</a>] - Improved SSL documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12462'>#12462</a>] - The Exclude Algorithms section in Flow AutoML is now always visible, even if you have not yet selected a training frame.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12467'>#12467</a>] - Removes unused parameters, fields, and methods from AutoML. Also exposed buildSpec in the AutoML REST API.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11853'>#11853</a>] - Updated documentation to indicate support for Java 9.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12026'>#12026</a>] - Added the new `pca_impl` parameter to PCA section of the user guide. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12036'>#12036</a>] - Added a Checkpointing Models section to the User Guide. This describes how checkpointing works for each supported algorithm.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12268'>#12268</a>] - In the "Getting Data into H2O" section, added a link to the new Hive JDBC demo.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12274'>#12274</a>] - The Import File example now also shows how to import from HDFS.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12302'>#12302</a>] - Fixed markdown headings in the example Flows. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12340'>#12340</a>] - All installation examples use H2O version 3.20.0.1.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12362'>#12362</a>] - Added a "Data Manipulation" topic for target encoding in R.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12364'>#12364</a>] - Added new keep_cross_validation_models and keep_cross_validation_predictions options to the AutoML documentation. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12373'>#12373</a>] - Added an example of using XGBoost MOJO with Maven. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12377'>#12377</a>] - In the XGBoost chapter, added information describing how to disable XGBoost.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12417'>#12417</a>] - When running XGBoost on Hadoop, added a note that users should set -extramempercent to a much higher value. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12440'>#12440</a>] - Added a section for the CoxPH (Cox Proportional Hazards) algorithm.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12442'>#12442</a>] - Added a topic describing how to install H2O-3 from the Google Cloud Platform offering.
</li>
</ul>


### Wolpert (3.18.0.11) - 5/24/2018

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-wolpert/11/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-wolpert/11/index.html</a>

<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12445'>#12445</a>] - Enabled Java 10 support for CRAN release.
</li>
</ul>

<h4>Task</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12446'>#12446</a>] - GLM tests no longer fail on Java 10.
</li>
</ul>

### Wolpert (3.18.0.10) - 5/22/2018

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-wolpert/10/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-wolpert/10/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12421'>#12421</a>] - Fixed an issue for adding Double.NaN to IntAryVisitor via addValue().
</li>
</ul>

<h4>Task</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12422'>#12422</a>] - Removed all code that referenced Google Analytics.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12427'>#12427</a>] - Disabled version check in H2O-3.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12429'>#12429</a>] - Removed all Google Analytics references and code from Flow.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12430'>#12430</a>] - Removed all Google Analytics references and code from Documentation.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12408'>#12408</a>] - The Security chapter in the User Guide now describes how to enforce system-level command-line arguments in h2odriver when starting H2O.
</li>
</ul>



### Wolpert (3.18.0.9) - 5/11/2018

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-wolpert/9/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-wolpert/9/index.html</a>


<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12162'>#12162</a>] - Fixed an issue that caused distributed XGBoost to not be registered in the REST API
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12196'>#12196</a>] - Fixed an issue that caused XGBoost to crash due "too many open files."
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12310'>#12310</a>] - Frames are now rebalanced correctly on multinode clusters.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12330'>#12330</a>] - Fixed an issue that prevented H2O libraries to load in DBC.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12371'>#12371</a>] - Added more robust checks for Colorama version.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12374'>#12374</a>] - Added more robust checks for Colorama version in H2O Python client.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12382'>#12382</a>] - A response column is no longer required when performing Deep Learning grid search with autoencoder enabled.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12391'>#12391</a>] - Fixed a KeyV3 error message that incorrectly referenced KeyV1.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12407'>#12407</a>] - The external backend now stores sparse vector values correctly.
</li>
</ul>

<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12322'>#12322</a>] - Added a new rank_within_group_by function in R and Python for ranking groups and storing the ranks in a new column.
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12366'>#12366</a>] - Improved warning messages in AutoML.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12400'>#12400</a>] - System administrators can now create a configuration file with implicit arguments of h2odriver and use it to make sure the h2o cluster is started with proper security settings. 
</li>
</ul>


### Wolpert (3.18.0.8) - 4/19/2018

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-wolpert/8/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-wolpert/8/index.html</a>

<h4>Task</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12331'>#12331</a>] - Release for CRAN submission.
</li>
</ul>

### Wolpert (3.18.0.7) - 4/14/2018

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-wolpert/7/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-wolpert/7/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12351'>#12351</a>] - Fixed a MOJO/POJO scoring issue caused by a serialization bug in EasyPredictModelWrapper.
</li>
</ul>

### Wolpert (3.18.0.6) - 4/13/2018

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-wolpert/6/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-wolpert/6/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12350'>#12350</a>] - In XGBoost, fixed a memory issue that caused training to fail even when running on small datasets.            
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12307'>#12307</a>] - When files have a Ctr-M character as part of data in the row and Ctr-M also signifies the end of line in that file, it is now parsed correctly.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12324'>#12324</a>] - H2O-3 no longer displays the server version in HTTP response headers.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12326'>#12326</a>] - Updated the Mockito library.
</li>
</ul>

<h4>Task</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12315'>#12315</a>] - Conda packages are now availabe on S3, enabling installation for users who cannot access anaconda.org.
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12339'>#12339</a>] - Added an offset to predictBinomial Easy wrapper.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12099'>#12099</a>] - Updated the AutoML chapter of the User Guide to include a link to H2O World AutoML Tutorials and updated code examples that do not use leaderboard_frame.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12323'>#12323</a>] - Fixed links to POJO/MOJO tutorials in the GBM FAQ > Scoring section.
</li>
</ul>

### Wolpert (3.18.0.5) - 3/28/2018

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-wolpert/5/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-wolpert/5/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11811'>#11811</a>] - AutoML no longer trains a Stacked Ensemble with only one model.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11903'>#11903</a>] - GBM and GLM grids no longer fail in AutoML for multinomial problems. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12138'>#12138</a>] -  Users can now merge/sort frames that contain string columns.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12174'>#12174</a>] - Fixed an issue that occured with multinomial GLM POJO/MOJO models.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12203'>#12203</a>] - Users can no longer specify a value of 0 for the col_sample_rate_change_per_level parameter. The value for this parameter must be greater than 0 and <= 2.0.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12205'>#12205</a>] - The H2O-3 Python client no longer returns an incorrect answer when running a conditional statement.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12233'>#12233</a>] - Added support for CDH 5.14.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12234'>#12234</a>] - Fixed an issue that caused XGBoost to fail when running the airlines dataset on a single-node H2O cluster.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12237'>#12237</a>] - The H2O-3 parser can now handle utf-8 characters that appear in the header.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12261'>#12261</a>] - The H2O-3 parser no longer treats the "Ctr-M" character as an end of line on Linux. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12281'>#12281</a>] - H2O no longer generates a warning when predicting without a weights column.
</li>
</ul>

<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12269'>#12269</a>] - The AutoML leaderboard no longer prints NaNs for non-US locales.
</li>
</ul>

<h4>Task</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12107'>#12107</a>] - Added a demo of XGBoost in Flow.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12253'>#12253</a>] - Improved the ordinal regression parameter optimization by changing the implementation.
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10871'>#10871</a>] - In Flow, improved the vertical scrolling for training and validation metrics for thresholds.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12232'>#12232</a>] - Added more logging regarding the WatchDog client.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12250'>#12250</a>] - Replaced  unknownCategoricalLevelsSeenPerColumn with ErrorConsumer events in POJO log messages.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12267'>#12267</a>] - Improved the logic that triggers rebalance.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12271'>#12271</a>] - AutoML now uses correct datatypes in the AutoML leaderboard TwoDimTable.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12164'>#12164</a>] - Added ``beta constraints`` and ``prior`` entries to the Parameters Appendix, along with examples in R and Python. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12236'>#12236</a>] - Added CDH 5.14 to the list of supported Hadoop platforms in the User Guide.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12280'>#12280</a>] - Updated the documenation for the Ordinal ``family`` option in GLM based on the new implementation. Also added new solvers to the documenation: GRADIENT_DESCENT_LH and GRADIENT_DESCENT_SQERR.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12283'>#12283</a>] - Added information about Extremely Randomized Trees (XRT) to the DRF chapter in the User Guide.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12288'>#12288</a>] - On the H2O-3 and Sparkling Water download pages, the link to documentation site now points to the most updated version.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12298'>#12298</a>] - The ``target_encode_create`` and ``target_encode_apply`` are now included in the R HTML documentation.
</li>
</ul>

<h4>Fault</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/15497'>#15497</a>] - Fixed an issue that caused SQLManager import to break on cluster with over 100 nodes.
</li>
</ul>


### Wolpert (3.18.0.4) - 3/8/2018

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-wolpert/4/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-wolpert/4/index.html</a>

- Fixed minor release process issue preventing Sparkling Water release.

### Wolpert (3.18.0.3) - 3/2/2018

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-wolpert/3/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-wolpert/3/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11975'>#11975</a>] - In Flow, the metalearner_fold_column option now correctly displays a drop-down of column names.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12154'>#12154</a>] -  Fixed an issue that caused data import and building models fail when using Flow in IE 11.1944 on Windows 10 Enterprise.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12194'>#12194</a>] - Stacked Ensemble no longer fails when using a grid or list of GLMs as the base models.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12071'>#12071</a>] - Fixed an issue that caused an error when during Parquet data ingest.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12204'>#12204</a>] - In Random Forest, added back the distribution and offset_column options for backward compatibility. Note that these options are deprecated and will be ignored if used. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12208'>#12208</a>] - MOJO export to a file now works correctly.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12211'>#12211</a>] - Fixed an NPE that occurred when checking if a request is Xhr.
</li>
</ul>

<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11884'>#11884</a>] - Added support for ordinal regression in GLM. This is specified using the `family` option.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12146'>#12146</a>] - Added the exclude_algos option to AutoML in Flow.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12179'>#12179</a>] - Added a Leave-One-Out Target Encoding option to the R API. This can help improve supervised learning results when there are categorical predictors with high cardinality. Note that a similar function for Python will be available at a later date.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12195'>#12195</a>] - POJO now logs error messages for all incorrect data types and includes default values rather than NULL when a data type is unexpected.
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12212'>#12212</a>] - Moved AutoML to the top of the Model menu in Flow.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12177'>#12177</a>] - In the GLM chapter, added Ordinal to the list of `family` options. Also added Ologit, Oprobit, and Ologlog to the list of `link` options, which can be used with the Ordinal family.
</li>
</ul>


### Wolpert (3.18.0.2) - 2/20/2018

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-wolpert/2/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-wolpert/2/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12172'>#12172</a>] - Distributed XGBoost no longer fails silently when expanding a 4G dataset on a 1TB cluster.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12126'>#12126</a>] - Fixed an issue that caused GLM Multinomial to not work properly.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12150'>#12150</a>] - In XGBoost, when the first domain of a categorical is parseable as an Int, the remaining columns are not automatically assumed to also be parseable as an Int. As a result of this fix, the default value of categorical_encoding in XGBoost is now AUTO rather than label_encoder.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12166'>#12166</a>] - Fixed an issue that caused XGBoost models to fail to converge when an unknown decimal separator existed.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12197'>#12197</a>] - Fixed an issue in ParseTime that led to parse failing.
</li>
</ul>


<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12184'>#12184</a>] - In the User Guide, the default value for categorical_encoding in XGBoost is now AUTO rather than label_encoder.
</li>
</ul>

### Wolpert (3.18.0.1) - 2/12/2018

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-wolpert/1/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-wolpert/1/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11467'>#11467</a>] - Fixed an issue that caused XGBoost binary save/load to fail.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11475'>#11475</a>] - Fixed an issue that caused a Levensthein Distance Normalization Error. Levenstein distance is now implemented directly into H2O.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11985'>#11985</a>] - The Word2Vec Python API for pretrained models no longer requires a training frame. In addition, a new `from_external` option was added, which creates a new H2OWord2vecEstimator based on an external model.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12001'>#12001</a>] - Fixed an issue that caused the show function of metrics base to fail to check for a key custom_metric_name and excepts.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12002'>#12002</a>] - The fold column in Kmeans is no longer required to be in x.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12003'>#12003</a>] - The date is now parsed correctly when parsed from H2O-R.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12006'>#12006</a>] -  In Flow, the scoring history plot is now available for GLM models.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12008'>#12008</a>] - The Parquet parser no longer fails if one of the files to parse has no records.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12017'>#12017</a>] - Added error checking and logging on all the uses of `water.util.JSONUtils.parse().
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12027'>#12027</a>] - In AutoML, fixed an exception in Python binding that occurred when the leaderboard was empty. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12028'>#12028</a>] - In AutoML, fixed an exception in R binding that occurred when the leaderboard was empty.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12031'>#12031</a>] - Removed Pandas dependency for AutoML in Python.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12039'>#12039</a>] - In PySparkling, reading Parquet/Orc data with time type now works correctly in H2O.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12046'>#12046</a>] - Fixed a maximum recursion depth error when using `isin` in the H2O Python client.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12047'>#12047</a>] - When running getJobs in Flow, fixed a ClassNotFoundException that occurred when AutoML jobs existed.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12051'>#12051</a>] - Fixed an issue that caused a list of columns to be truncated in PySparkling. Light endpoint now returns all columns.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12058'>#12058</a>] - In AutoML, fixed a deadlock issue that occurred when two AutoML runs came in the same second, resulting in matching timestamps.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12063'>#12063</a>] - The offset_column and distribution parameters are no longer available in Random Forest. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12067'>#12067</a>] - Fixed an issue in XGBoost that caused MOJOs to fail to work without manually adding the Commons Logging dependency.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12075'>#12075</a>] - Fixed an issue that caused XGBoost to mangle the domain levels for datasets that have string response domains.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12085'>#12085</a>] - In Flow, the separator drop down now shows 3-digit decimal values instead of 2.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12087'>#12087</a>] - Users can now specify interactions when running GLM in Flow.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12100'>#12100</a>] - FrameMetadate code no longer uses hardcoded keys. Also fixed an issue that caused AutoML to fail when multiple AutoMLs are run simultaneously.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12101'>#12101</a>] - A frame can potentially have a null key. If there is a Frame with a null key (just a container for vecs), H2O no longer attempts to track a null key. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12128'>#12128</a>] - Users can now successfully build an XGBoost model as compile chain. XGBoost no longer fails to provide the compatible artifact for an Oracle Linux environment.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12137'>#12137</a>] - GLM no longer fails when a categorical column exists in the dataset along with an empty value on at least one row.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12158'>#12158</a>] - Fixed an issue that cause GBM grid to fail on some datasets when specifying `sample_rate` in the grid.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12159'>#12159</a>] - The x argument is no longer required when performing a grid search.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12168'>#12168</a>] - Fixed an issue that caused the Parquet parser to fail on Spark 2.0 (SW-707).
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12186'>#12186</a>] - Fixed an issue that caused XGBoost OpenMP to fail on Ubuntu 14.04. 
</li>
</ul>

<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11000'>#11000</a>] - Added support for INT96 timestamp to the Parquet parser.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11535'>#11535</a>] - Added support for XGBoost multinode training in H2O. Note that this is still a BETA feature.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11856'>#11856</a>] - Users can now specify a list of algorithms to exclude during an AutoML run. This is done using the new `exclude_algos` parameter. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12076'>#12076</a>] - In GLM, users can now specify a list of interactions terms to include when building a model instead of relying on the default action of including all interactions. 
</li>
</ul>

<h4>Task</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12102'>#12102</a>] - The Python PCA code examples in github and in the User Guide now use the h2o.estimators.pca.H2OPrincipalComponentAnalysisEstimator method instead of the h2o.transforms.decomposition.H2OPCA method.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12123'>#12123</a>] - Upgraded the XGBoost version. This now supports RHEL 6.
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11959'>#11959</a>] - Stacked Ensemble allows you to specify the metalearning algorithm to use when training the ensemble. When an algorithm is specified, Stacked Ensemble runs with the specified algorithm's default hyperparameter values.  The new ``metalearner_params`` option allows you to pass in a dictionary/list of hyperparameters to use for that algorithm instead of the defaults.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12096'>#12096</a>] - Users can now specify a seed parameter in Stacked Ensemble.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12181'>#12181</a>] - Documented clouding behavior of an H2O cluster. This is available at https://github.com/h2oai/h2o-3/blob/master/h2o-docs/devel/h2o_clouding.rst.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12021'>#12021</a>] - Updated the documentation to indicate that datetime parsing from R and Flow now is UTC by default.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12023'>#12023</a>] - R documentation on docs.h2o.ai is now available in HTML format. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12044'>#12044</a>] - Added a new Cloud Integration topic for using H2O with AWS.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12093'>#12093</a>] - In the XGBoost chapter, added that XGBoost in H2O supports multicore.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12114'>#12114</a>] - Added `interaction_pairs` to the list of GLM parameters. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12155'>#12155</a>] - Added `metalearner_algorithm` and `metalearner_params` to the Stacked Ensembles chapter.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12182'>#12182</a>] - The H2O-3 download site now includes a link to the HTML version of the R documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12183'>#12183</a>] - Updated the XGBoost documentation to indicate that multinode support is now available as a Beta feature.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12185'>#12185</a>] - Added the seed parameter to the Stacked Ensembles section of the User Guide.
</li>
</ul>


### Wheeler (3.16.0.4) - 1/15/2018

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-wheeler/4/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-wheeler/4/index.html</a>

<h4>Bug Fix</h4>

<li>[<a href='https://github.com/h2oai/h2o-3/issues/12078'>#12078</a>] - Fixed several client deadlock issues.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12084'>#12084</a>] - When verifying that a supported version of Java is available, H2O no longer checks for version 1.6. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12088'>#12088</a>] - The H2O-3 download site has an updated link for the Sparkling Water README.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12092'>#12092</a>] - In Aggregator, fixed the way that a created mapping frame is populated. 
</li>

<h4>New Feature</h4>

<li>[<a href='https://github.com/h2oai/h2o-3/issues/12081'>#12081</a>] - XGBoost can now be used in H2O on Hadoop with a single node.
</li>

<h4>Improvement</h4>

<li>[<a href='https://github.com/h2oai/h2o-3/issues/12082'>#12082</a>] - Deep Water is disabled in AutoML.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12083'>#12083</a>] - This release of H2O includes an upgraded XGBoost version.
</li>


### Wheeler (3.16.0.3) - 1/8/2018

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-wheeler/3/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-wheeler/3/index.html</a>

<h4>Technical task</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12056'>#12056</a>] - H2O-3 now allows definition of custom function directly in Python notebooks and enables iterative updates on defined functions. 
</li>
</ul>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11740'>#11740</a>] - When a frame name includes numbers followed by alphabetic characters (for example, "250ML"), Rapids no longer parses the frame name as two tokens. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11775'>#11775</a>] - Fixed an issue that caused Partial Dependence Plots to a use different order of categorical values after calling as.factor.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12020'>#12020</a>] - Added support for CDH 5.13.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12052'>#12052</a>] - Fixed an issue that caused a Python 2 timestamp to be interpreted as two tokens.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12068'>#12068</a>] - Aggregator supports categorial features. Fixed a discrepency in the Aggregator documentation. 
</li>
</ul>

<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11504'>#11504</a>] - In GBM, users can now specify quasibinomial distribution.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11842'>#11842</a>] - H2O-3 now supports the Netezza JDBC driver.
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12043'>#12043</a>] -  Users can now optionally export the mapping of rows in an aggregated frame to that of the original raw data.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11993'>#11993</a>] - When using S3/S3N, revised the documentation to recommend that S3 should be used for data ingestion, and S3N should be used for data export. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12022'>#12022</a>] - The H2O User Guide has been updated to indicate support for CDH 5.13.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12034'>#12034</a>] - Updated the Anaconda section with information specifically for Python 3.6 users.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12050'>#12050</a>] - The H2O User Guide has been updated to indicate support for the Netezza JDBC driver.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12062'>#12062</a>] - Added "quasibinomial" to the list of `distribution` options in GBM.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12064'>#12064</a>] - Added the new `save_mapping_frame` option to the Aggregator documentation.
</li>
</ul>


### Wheeler (3.16.0.2) - 11/30/2017

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-wheeler/2/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-wheeler/2/index.html</a>

<h4>Bug Fix</h4>

<li>[<a href='https://github.com/h2oai/h2o-3/issues/11988'>#11988</a>] - In AutoML, fixed an issue that caused the leaderboard_frame to be ignored when nfolds > 1. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11990'>#11990</a>] - Improved the warning that displays when mismatched jars exist. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11999'>#11999</a>] - The correct H2O version now displays in setup.py for sdist.
</li>

<h4>Improvement</h4>

<li>[<a href='https://github.com/h2oai/h2o-3/issues/11984'>#11984</a>] - Incorporated final improvements to the Sparkling Water booklet.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12000'>#12000</a>] - Automated Anaconda releases.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12004'>#12004</a>] - This version of H2O introduces light rest endpoints for obtaining frames in the python client.
</li>


### Wheeler (3.16.0.1) - 11/24/2017

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-wheeler/1/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-wheeler/1/index.html</a>


<h4>Technical Task</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11960'>#11960</a>] - A backend Java API is now available for custom evaluation metrics. 
</li>
</ul>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/14436'>#14436</a>] - Users can now save models to and download models from S3. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10476'>#10476</a>] - When running h2o.merge in the R client, the status line indicator will no longer return quickly. Users can no longer enter new commands until the merge process is completed.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11061'>#11061</a>] - In the R client strings, training_frame says no longer states that it is an optional parameter.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11553'>#11553</a>] - The H2OFrame.mean method now works in Python 3.6.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11576'>#11576</a>] - Early stopping now works with perfectly predictive data.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11606'>#11606</a>] - h2o.group_by now works correctly when specifying a median() value.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11657'>#11657</a>] - In XGBoost fixed an issue that caused prediction on a dataset without a response column to return an error.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11731'>#11731</a>] - When running AutoML in Flow, users can now specify a project name.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11735'>#11735</a>] - h2odriver in proxy mode now correctly forwards the authentication headers to the H2O node.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11778'>#11778</a>] - H2O can ingest Parquet 1.8 files created by Spark.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11784'>#11784</a>] - Loading models and exporting models to/from AWS S3 now works correctly.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11785'>#11785</a>] - Fixed an issue that caused  binary model imports and exports from/to S3 to fail. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11808'>#11808</a>] - Users can now load data from s3n resources after setting core-site.xml correctly.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11831'>#11831</a>] - Fixed an error that occurred when exporting data to s3.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11861'>#11861</a>] - Fixed an issue that caused H2O to "forget" that a column is of factor type if it contains only NA values.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11872'>#11872</a>] - The download instructions for Python now indicate that version 3.6 is supported.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11878'>#11878</a>] - In Flow, fixed an issue with retaining logs from the client node.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11879'>#11879</a>] - H2O can now handle the case where I'm the Client and the md5 should be ignored.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11881'>#11881</a>] - h2o.residual_deviance now works correctly.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11893'>#11893</a>] - h2o.predict no longer returns an error when the user does not specify an offset_column. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11908'>#11908</a>] - Fixed an issue with Spark string chunks. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11912'>#11912</a>] - Logs now display correctly on HADOOP, and downloaded logs no longer give an empty folder when the cluster is up.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11913'>#11913</a>] - Added an option for handling empty strings. If compare_empty if set to FALSE, empty strings will be handled as NaNs.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11915'>#11915</a>] - HTTP logs can now be obtained in Flow UI.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11742'>#11742</a>] - Fixed an issue with the progress bar that occurred when running PySparkling + DataBricks.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11940'>#11940</a>] - Fixed reporting of clients with the wrong md5.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11943'>#11943</a>] - In the R and Python clients, updated the strings for max_active_predictors to indicate that the default is now 5000. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11945'>#11945</a>] - h2o.merge now works correctly for one-to-many when all.x=TRUE. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11947'>#11947</a>] - Fixed an issue that caused GLM predict to fail when a weights column was not specified.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11954'>#11954</a>] - Reduced the number of URLs that get sent to google analytics.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11968'>#11968</a>] - When building a Stacked Ensemble model, the fold_column from AutoML is now piped through to the stacked ensemble.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11969'>#11969</a>] - Fixed an issue that cause GLM scoring to produce incorrect results for sparse data.
</li>
</ul>

<h4>Epic</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11563'>#11563</a>] - This version of H2O includes support for Python 3.6.
</li>
</ul>

<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10773'>#10773</a>] - MOJOs are now supported for Stacked Ensembles.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10640'>#10640</a>] - User can now specify the metalearner algorithm type that StackedEnsemble should use. This can be AUTO, GLM, GBM, DRF, or Deep Learning.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10864'>#10864</a>] - Added a metalearner_folds option in Stacked Ensembles, enabling cross validation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10975'>#10975</a>] - In GBM, endpoints are now exposed that allow for custom evaluation metrics. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11760'>#11760</a>] - When running AutoML through the Python or R clients, users can now specify the nfolds argument.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11770'>#11770</a>] - Add another Stacked Ensemble (top model for each algo) to AutoML
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11944'>#11944</a>] - The AutoML leaderboard now uses cross-validation metrics (new default). 
</li> 
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11792'>#11792</a>] - K-Means POJOs and MOJOs now expose distances to cluster centers.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11834'>#11834</a>] - Multiclass stacking is now supported in AutoML. Removed the check that caused AutoML to skip stacking for multiclass.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11918'>#11918</a>] - Users can now specify a number of folds when running AutoML in Flow.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11957'>#11957</a>] - Added a metalearner_fold_column option in Stacked Ensembles, allowing for custom folds during cross validation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11870'>#11870</a>] - The Aggregator Function is now exposed in the R client.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11871'>#11871</a>] - The Aggregator Function is now available in the Python client.
</li>
</ul>

<h4>Story</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11919'>#11919</a>] - Fixed a Jaro-Winkler Dependency.
</li>
</ul>

<h4>Task</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11682'>#11682</a>] - The current version of h2o-py is now published into PyPi.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11774'>#11774</a>] - Change behavior of auto-generation of validation and leaderboard frames in AutoML
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11809'>#11809</a>] - Updated the download site and the end user documentation to indicate that Python3.6 is now supported.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11813'>#11813</a>] - PyPi/Anaconda descriptors now indicate support for Python 3.6.
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11670'>#11670</a>] - Enabled the lambda search for the GLM metalearner in Stacked Ensembles. This is set to TRUE and early_stopping is set to FALSE. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11710'>#11710</a>] - Running `pip install` now installs the latest version of H2O-3.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11840'>#11840</a>] - In EasyPredictModelWrapper, preamble(), predict(), and fillRawData() are now protected rather than private.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11955'>#11955</a>] - MOJOs/POJOs will not be created for unsupported categorical_encoding values.
</li> 
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11982'>#11982</a>] - An AutoML run now outputs two StackedEnsemble model IDs. These are labeled StackedEnsemble_AllModels and StackedEnsemble_BestOfFamily.
</li> 
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11187'>#11187</a>] - In the Data Manipulation chapter, added a topic for pivoting tables.  
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11544'>#11544</a>] - Added a topic to the Data Manipulation chapter describing the h2o.fillna function.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11626'>#11626</a>] - Added MOJO and POJO Quick Start sections directly into the Productionizing H2O chapter. Previously, this chapter included links to quick start files.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11689'>#11689</a>] - In the GBM booklet when describing nbins_cat, clarified that factors rather than columns get grouped together. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11695'>#11695</a>] - The description for the GLM lambda_max option now states that this is the smallest lambda that drives all coefficients to zero. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11712'>#11712</a>] - Updated the installation instructions for PySparkling. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11741'>#11741</a>] - Clarified that in H2O-3, sampling is without replacement.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11756'>#11756</a>] - Updated documentation to state that multiclass classification is now supported in Stacked Ensembles.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11757'>#11757</a>] - Updated documentation to state that multiclass stacking is now supported in AutoML.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11773'>#11773</a>] - Added an Early Stopping section the Algorithms > Common chapter.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11823'>#11823</a>] - Added a note in Word2vec stating that binary format is not supported.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11824'>#11824</a>] - In the Parameters Appendix, updated the description for histogram_type=random.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11835'>#11835</a>] - In the Using Flow > Models > Run AutoML section, updated the AutoML screenshot to show the new Project Name field. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11848'>#11848</a>] - Added a Sorting Columns data munging topic describing how to sort a data frame by column or columns.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11876'>#11876</a>] - In KMeans, updated the list of model summary statistics and training metrics that are outputted. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11887'>#11887</a>] - Removed SortByResponse from the list of categorical_encoding options for Aggregator and K-Means.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11901'>#11901</a>] - Updated the Sparkling Water links on docs.h2o.ai to point to the latest release.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11907'>#11907</a>] - Added a section in the Algorithms chapter for Aggregator.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11929'>#11929</a>] - Updated the description for Save and Loading Models to indicate that H2O binary models are not compatible across H2O versions.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11930'>#11930</a>] - Added ignored_columns and 'x' parameters to AutoML section. Also added the 'x' parameter to the Parameters Appendix. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11935'>#11935</a>] - In DRF, add FAQs describing splitting criteria. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11958'>#11958</a>] - Added the new metalearner_folds and metalearner_fold_assignment parameters to the Defining a Stacked Ensemble Model section in the User Guide.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11962'>#11962</a>] - Updated the Sparking Water booklet. (Also #11962.)
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11965'>#11965</a>] - Added the new metalearner_algorithm parameter to Defining a Stacked Ensemble Model section in the User Guide.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11970'>#11970</a>] - The User Guide and the POJO/MOJO Javadoc have been updated to indicate that MOJOs are supported for Stacked Ensembles.
</li>
</ul>

### Weierstrass (3.14.0.7) - 10/20/2017

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-weierstrass/7/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-weierstrass/7/index.html</a>

<h4>Bug Fix</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11863'>#11863</a>] -         h2o.H2OFrame.any() and h2o.H2OFrame.all() not working properly if frame contains only True
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11864'>#11864</a>] -         Don&#39;t check H2O client hash-code ( Fix )
</li>
</ul>

<h4>Task</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10896'>#10896</a>] -         Generate Python API tests for Python Module Data in H2O and Data Manipulation
</li>
</ul>

### Weierstrass (3.14.0.6) - 10/9/2017

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-weierstrass/6/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-weierstrass/6/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/sparkling-water/issues/4170'>sparkling-water-#4170</a>] - Fixed an issue that prevented Sparkling Water from importing Parquet files.
</li>
</ul>

### Weierstrass (3.14.0.5) - 10/9/2017

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-weierstrass/4/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-weierstrass/5/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11748'>#11748</a>] - Fixed an issue that caused sorting to be done incorrectly.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11795'>#11795</a>] - Only relevant clients (the ones with the same cloud name) are now reported to H2O. </li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11832'>#11832</a>] - Improved error messaging in the case where H2O fails to parse a valid Parquet file.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11836'>#11836</a>] - Fixed an issue that allowed nodes from different clusters to kill different H2O clusters. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11855'>#11855</a>] - Fixed an issue that caused K-Means to improperly calculate scaled distance.
</li>
</ul>

<h4>Task</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11803'>#11803</a>] - Nightly and stable releases will now have published sha256 hashes.
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11290'>#11290</a>] - The h2o.sort() function now includes an `ascending` parameter that allows you to  specify whether a numeric column should be sorted in ascending or descending order.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11841'>#11841</a>] - H2O no longer terminates when an incompatible client tries to connect.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11827'>#11827</a>] - Updated the list of required packages for the H2O-3 R client on the H2O Download site and in the User Guide.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11843'>#11843</a>] - Added an FAQ to the <a href="http://docs.h2o.ai/h2o/latest-stable/h2o-docs/faq/java.html">User Guide FAQ</a> describing how Java 9 users can switch to a supported Java version. 
</li>
</ul>


### Weierstrass (3.14.0.3) - 9/18/2017

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-weierstrass/3/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-weierstrass/3/index.html</a>

<h4>Technical Task</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11751'>#11751</a>] - Introduced a Python client side AST optimization.
</li>
</ul>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10437'>#10437</a>] - In R, `h2o.arrange()` can now sort on a float column.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11602'>#11602</a>] - The `as_data_frame()` function no longer drops rows with NAs when `use_pandas` is set to TRUE.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11614'>#11614</a>] - In Deep Learning POJOs, fixed an issue in the sharing stage between threads.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11618'>#11618</a>] - Fixed an issue in R that caused `h2o.sub` to fail to retain the column names of the frame.
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11636'>#11636</a>] - Running ifelse() on a constant column no longer results in an error. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11725'>#11725</a>] - Using + on string columns now works correctly.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11727'>#11727</a>] - Fixed an issue that caused a POJO and a MOJO to return different column names with the `getNames()` method.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11728'>#11728</a>] - The R and Python clients now have consistent timeout numbers.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11746'>#11746</a>] - Fixed an issue that resulted in an AIOOB error when predicting with GLM. NA responses are now removed prior to GLM scoring.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11787'>#11787</a>] - The set_name method now works correctly in the Python client. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11799'>#11799</a>] - Replaced the deprecated Clock class in timing.gradle.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11815'>#11815</a>] - The MOJO Reader now closes open files after reading.
</li>
</ul>


<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11510'>#11510</a>] - MOJO support has been extended  to include the Deep Learning algorithm.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11724'>#11724</a>] - Added the ability to import an encrypted (AES128) file into H2O. This can be configured glovally by specifying the `-decrypt_tool` option and installing the tool in DKV. 
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11782'>#11782</a>] - The Decryption API is now exposed in the REST API and in the R client.
</li>
</ul>

<h4>Docs</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11690'>#11690</a>] - Updated the MOJO Quick Start Guide to show separator differences between Linux/OS X and Windows. Also updated the R example to match the Python example.
</li>
</ul>

### Weierstrass (3.14.0.2) - 8/21/2017

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-weierstrass/2/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-weierstrass/2/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11683'>#11683</a>] - Fixed a broken link to the Hive tutorials from the Productionizing section in the User Guide.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11701'>#11701</a>] - Sparkling Water can now pass a data frame with a vector for conversion into H2OFrame. In prior versions, the vector was not properly expanded and resulted in a failure.  
</li>
</ul>

<h4>Task</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11681'>#11681</a>] - Added more tests to ensure that, when max_runtime_secs is set, the returned model works correctly.
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11691'>#11691</a>] - This version of H2O includes an option to force toggle (on/off) a specific extension. This enables users to enable the XGBoost REST API on a system that does not support XGBoost.  
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11708'>#11708</a>] - A warning now displays when the minimal XGBoost version is used.
</li>
</ul>

### Weierstrass (3.14.0.1) - 8/10/2017

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-weierstrass/1/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-weierstrass/1/index.html</a>

<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9702'>#9702</a>] -  In the R client, making a copy of a factor column and then changing the factor levels no longer causes the levels of the original column to change.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11466'>#11466</a>] - Added a **Leaderboard Frame** option in Flow when configuring an AutoML run.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11468'>#11468</a>] - The `h2o.performance` function now works correctly on XGBoost models.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11507'>#11507</a>] - In the Python client, improved the help string for `h2o_import_file`. This string now indicates that setting `(parse=False)` will return a list instead of an H2OFrame.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11537'>#11537</a>] - Removed the Ecko dependency. This is not needed.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11562'>#11562</a>] - Fixed an issue that caused the parquet parser to store numeric/float values in a string column. This issue occurred when specifying an unsupported type conversion in Parse Setup (for example, numeric -> string). Users will now encounter an error when attempting this. Additionally, users can now change Enums->Strings in parse setup.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11565'>#11565</a>] - Deep Learning POJOs are now thread safe.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11567'>#11567</a>] - Fixed the default print method for H2OFrame in Python. Now when a user types the H2OFrame name, a new line is added, and the header is pushed to the next line.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11581'>#11581</a>] - Fixed an issue that caused the `max_runtime_secs` parameter to fail correctly when run through the Python client. As a result of this fix, the `max_runtime_secs` parameter was added to Word2vec.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11583'>#11583</a>] - Fixed an issue that caused XGBoost grid search to fail when using the Python client.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11603'>#11603</a>] - When running with weighted data and columns that are constant after applying weights, a GLM lambda search no longer results in an AIOOB error.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11609'>#11609</a>] - The XGBoost `max_bin` parameter has been renamed to `max_bins`, and its default value is now 256.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11610'>#11610</a>] - XGBoost Python documentation is now available.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11611'>#11611</a>] - In XGBoost, the `learning_rate` (alias: `eta` parameter now has a default value of 0.3.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11613'>#11613</a>] - In XGBoost, the `max_depth` parameter now has a default value of 6.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11614'>#11614</a>] - Multi-threading is now supported by POJO downloaded.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11630'>#11630</a>] - The XGBoost `min_rows` (alias: `min_child_weight`) parameter now has a default value of 1.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11631'>#11631</a>] - The XGBoost `max_abs_leafnode_pred` (alias: `max_delta_step`) parameter now has a default value of 0.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11632'>#11632</a>] - H2O XGBoost default options are now consistent with XGBoost default values. This fix involved the following changes:
<ul>
 <li>num_leaves has been renamed max_leaves, and its default value is 0.
 </li>
 <li>The default value for reg_lambda is 0.
 </li>
</ul>
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11635'>#11635</a>] - Removed the Guava dependency from the Deep Water API.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11655'>#11655</a>] - In XGBoost, the default value for sample_rate and the alias subsample are now both 1.0.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11656'>#11656</a>] - In XGBoost, the default value for colsample_bylevel (alias: colsample_bytree) has been changed to 1.0.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11662'>#11662</a>] - Hidden files are now ignored when reading from HDFS.
</li>
</ul>

<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11331'>#11331</a>] - Added a `verbose` option to Deep Learning, DRF, GBM, and XGBoost. When enabled, this option will display scoring histories as a model job is running.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11561'>#11561</a>] - Added an `extra_classpath` option, which allows users to specify a custom classpath when starting H2O from the R and Python client.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11564'>#11564</a>] - Users can now override the type of a Str/Cat column in a Parquet file when the parser attempts to auto detect the column type.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11617'>#11617</a>] - Users can now run a standalone H2O instance and read from a Kerberized cluster's HDFS.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11624'>#11624</a>] - Added support for CDH 5.10.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11629'>#11629</a>] - Added support for MapR 5.2.
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10840'>#10840</a>] - Fixed an issue that caused PCA to take 39 minutes to run on a wide dataset.
The wide dataset method for PCA is now only enabled if the dataset is very wide.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11478'>#11478</a>] -  XGBoost-specific WARN messages have been converted to TRACE.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11506'>#11506</a>] - When printing frames via `head()` or `tail()`, the `nrows` option now allows you to specify more than 10 rows. With this change, you can print the complete frame, if desired.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11512'>#11512</a>] - Improved the speed of        converting a sparse matrix to an H2OFrame in R.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11546'>#11546</a>] - Added the following parameters to the XGBoost R/Py clients:
<ul>
 <li>categorical_encoding</li>
 <li>sample_type</li>
 <li>normalize_type</li>
 <li>rate_drop</li>
 <li>one_drop</li>
 <li>skip_drop</li>
</ul>
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11557'>#11557</a>] - H2O can now handle sparse vectors as the input of  the external frame handler.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11571'>#11571</a>] - Added MOJO support for Spark SVM.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11580'>#11580</a>] - When running AutoML from within Flow, the default `stopping_tolerance` is now NULL instead of 0.001.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11627'>#11627</a>] - Removed dependency on Reflections.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11406'>#11406</a>] - Updated the list of Python requirements in the <a href="https://github.com/h2oai/h2o-3/blob/master/README.md#42-setup-on-all-platforms">README.md</a>, on the download site, and in the User Guide.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11437'>#11437</a>] - Updated the FAQ for Saving and Loading a Model in K-Means.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11449'>#11449</a>] - Added a <a href="http://docs.h2o.ai/h2o/latest-stable/h2o-docs/flow.html#run-automl">Run AutoML</a> subsection in the Flow section of the User Guide.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11482'>#11482</a>] - Continued improvements to XGBoost documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11511'>#11511</a>] - Added documentation for using H2O SW with Databricks.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11514'>#11514</a>] - In the <a href="http://docs.h2o.ai/h2o/latest-stable/h2o-docs/faq/general.html">http://docs.h2o.ai/h2o/latest-stable/h2o-docs/faq/general.html</a> topic, updated the example for scoring using an exported POJO.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11531'>#11531</a>] - In the <a href="http://docs.h2o.ai/h2o/latest-stable/h2o-docs/productionizing.html#about-pojos-and-mojos">About POJOs and MOJOs</a> topic, added text describing the h2o-genmodel jar file.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11539'>#11539</a>] - The User Guide now indicates that Hive files can be saved in ORC format and
then imported.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11568'>#11568</a>] - For topics that indicate support for Avro-formatted data, updated the User Guide to reflect that only Avro version 1.8.0 is supported.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11599'>#11599</a>] - A new H2O Python / Pandas Munging Parity document is now available at <a href="https://github.com/h2oai/h2o-3/tree/master/h2o-docs/src/cheatsheets">https://github.com/h2oai/h2o-3/tree/master/h2o-docs/src/cheatsheets</a>
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11612'>#11612</a>] - Added parameter defaults to the <a href="http://docs.h2o.ai/h2o/latest-stable/h2o-docs/data-science/xgboost.html">XGBoost</a> section in the User Guide.
</li>
</ul>

### Vapnik (3.12.0.1) 6/6/2017

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-vapnik/1/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-vapnik/1/index.html</a>

<h4>Epic</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11162'>#11162</a>] - AutoML is now available in H2O. AutoML can be used for automatically training and tuning a number of models within a user-specified time limit or model limit. It is designed to run with as few parameters as possible, and the top performing models can be viewed on a leaderboard. More information about AutoML is available <a href='http://docs.h2o.ai/h2o/latest-stable/h2o-docs/automl.html'>here</a>.
</li>
</ul>

<h4>New Feature</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11336'>#11336</a>] - With the addition of the AutoML feature, a new **Run AutoML** option is available in Flow under the **Models** dropdown menu.</li>
</ul>


### Vajda (3.10.5.4) - 7/17/2017

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-vajda/4/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-vajda/4/index.html</a>


<h4>Bug Fix</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11573'>#11573</a>] - Fixed an issue that caused tree algos to waste memory by storing categorical values in every tree.
</li>
</ul>

### Vajda (3.10.5.3) - 6/30/2017

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-vajda/3/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-vajda/3/index.html</a>


<h4>Bug Fix</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10917'>#10917</a>] - Fixed an issue that resulted in "Unexpected character after column id:" warnings when parsing an SVMLight file.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11330'>#11330</a>] - h2o.predict now displays a warning if the features (columns) in the test frame do not contain those features used by the model.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11455'>#11455</a>] - The XGBoost REST API is now only registered when backend lib exists.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11477'>#11477</a>] - H2O no longer displays an error if there is a "/" in the user-supplied model name. Instead, a message will display indicating that the "/" is replaced with "_".
</li>
</ul>

<h4>Improvement</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10835'>#10835</a>] - Added support for autoencoder POJOs in in the EasyPredictModelWrapper.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11158'>#11158</a>] - H2O now warns the user about the minimal required Colorama version in case of python client. Note that the current minimum version is 0.3.8.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11421'>#11421</a>] - Removed deprecation warnings from the H2O build.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11432'>#11432</a>] - Moved the initialization of XGBoost into the H2O core extension.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11399'>#11399</a>] - Added a link to paper describing balance classes in the <a href='http://docs.h2o.ai/h2o/latest-stable/h2o-docs/data-science/algo-params/balance_classes.html'>balance_classes</a> parameter topic.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11492'>#11492</a>] - Removed `laplace`, `huber`, and `quantile` from list of supported distributions in the XGBoost documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11494'>#11494</a>] - Add heuristics to the FAQ &gt; General Troubleshooting topic.
</li>
</ul>

### Vajda (3.10.5.2) - 6/19/2017

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-vajda/2/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-vajda/2/index.html</a>

<h4>Bug Fix</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10756'>#10756</a>] - In PCA, fixed an issue that resulted in errors when specifying `pca_method=glrm` on wide datasets. In addition, the GLRM algorithm can now be used with wide datasets.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11302'>#11302</a>] - Fixed issues with streamParse in ORC parser that caused a NullPointerException when parsing multifile from Hive.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11323'>#11323</a>] - Fixed an issue that occurred with H2O data frame indexing for large indices that resulted in off-by-one errors. Now, when indexing is set to a value greater than 1000, indexing between left and right sides is no longer inconsistent.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11341'>#11341</a>] - In DRF, fixed an issue that resulted in an AssertionError when run on certain datasets with weights.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9751'>#9751</a>] - Removed an incorrect Python example from the Sparkling Water booklet. Python users must start Spark using the H2O pysparkling egg on the Python path. Using `--package` when running the pysparkling app is not advised, as the pysparkling distribution already contains the required jar file.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11476'>#11476</a>] - In GLM fixed an issue that caused a Runtime exception when specifying the quasibinomial family with `nfold = 2`.
</li>
</ul>

<h4>New Feature</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10529'>#10529</a>] - Added top an bottom N functions, which allow users to grab the top or bottom N percent of a numerical column. The returned frame contains the original row indices of the top/bottom N percent values extracted into the second column.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10985'>#10985</a>] - When building Stacked Ensembles in R, the base_models parameter can accept models rather than just model IDs. Updated the documentation in the User Guide for the base_models parameter to indicate this.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11407'>#11407</a>] - Added the following new GBM and DRF parameters to the User Guide: `calibrate_frame` and `calibrate_model`.
</li>
</ul>

<h4>Improvement</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11415'>#11415</a>] -  Improved PredictCsv.java as follows:
  <ul>
  <li>Enabled PredictCsv.java to accept arbitrary separator characters in the input dataset file if the user includes the optional flag `--separator` in the input arguments. If a user enters a special Java character as the separator, then H2O will add "\".
  </li>
  <li>Enabled PredictCsv.java to perform setConvertInvalidNumbersToNa(setInvNumNA)) if the optional flag `--setConvertInvalidNum` is included in the input arguments.
  </li>
  </ul>
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11461'>#11461</a>] - Fixed the R package so that a "browseURL" NOTE no longer appears.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11465'>#11465</a>] - In the R package documentation, improved the description of the GLM `alpha` parameter.
</li>
</ul>

<h4>Docs</h4>

<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11408'>#11408</a>] - In the "Using Flow - H2O’s Web UI" section of the User Guide, updated the Viewing Models topic to include that users can download the h2o-genmodel.jar file when viewing models in Flow.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11433'>#11433</a>] - The `group_by` function accepts a number of aggregate options, which were documented in the User Guide and in the Python package documentation. These aggregate options are now described in the R package documentation.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11458'>#11458</a>] - Added an initial XGBoost topic to the User Guide. Note that this is still a work in progress.
</li>
</ul>

### Vajda (3.10.5.1) - 6/9/2017

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-vajda/1/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-vajda/1/index.html</a>

<h4>Technical Task</h4>

<ul>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/14554'>#14554</a>] - Fixed a GLM persist test.
   </li>
   	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11014'>#11014</a>] - Disabled R tests for OS X.
   </li>
</ul>

<h4>Bug Fix</h4>

<ul>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/14428'>#14428</a>] - PCA no longer reports incorrect values when multiple eigenvectors exist.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/14542'>#14542</a>] - Users can now specify the weights_column as a numeric index in R.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/14549'>#14549</a>] - Fixed an issue that caused GLM models returned by h2o.glm() and h2o.getModel(..) to be different.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/14586'>#14586</a>] - Fixed an issue that caused PCA with GLRM to display incorrect results on data.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/15193'>#15193</a>] - Fixed an issue that caused  `df.show(any_int)` to always display 10 rows.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/15321'>#15321</a>] - Starting an H2O cloud from R no longer results in "Error in as.numeric(x[&quot;max_mem&quot;]) :    (list) object cannot be coerced to type &#39;double&#39;"
	</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/9595'>#9595</a>] - `h2o::ifelse` now handles NA values the same way that `base::ifelse` does.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/9653'>#9653</a>] - Fixed an issue in PCA that resulted in incorrect standard deviation and components results for non standardized data.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/9696'>#9696</a>] - When performing a grid search with a `fold_assignment` specified and with `cross_validation` disabled, Python unit tests now display a Java error message. This is because a fold assignment is meaningless without cross validation.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/9750'>#9750</a>] - The Python `h2o.get_grid()` function is now in the base h2o object, allowing you to use it the same way as `h2o.get_model()`, `h2o.get_frame()` etc.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10116'>#10116</a>] - The `.mean()` function can now be applied to a row in `H2OFrame.apply()`.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10262'>#10262</a>] - Fixed an issue that caused a negative value to display in the H2O cluster version. </li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10306'>#10306</a>] - GLM now checks to see if a response is encoded as a factor and warns the user if it is not. </li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10381'>#10381</a>] - Fixed an issue that resulted in an `h2o.init()` fail message even though the server had actually been started. As a result, H2O did not shutdown automatically upon exit.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10413'>#10413</a>] - Fixed an issue that caused PCA to hang when run on a wide dataset using the Randomized `pca_method`. Note that it is still not recommended to use Randomized with wide datasets.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10432'>#10432</a>] - `h2o.setLevels` now works correctly when wrapped into invisible.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10555'>#10555</a>] - Added a dependency for the roxygen2 package.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10611'>#10611</a>] - `h2o.coef` in R is now functional for multinomial models.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10628'>#10628</a>] - When converting a column to `type = string` with `.ascharacter()` in Python, the `structure` method now correctly recognizes the change.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10657'>#10657</a>] - Fixed an issue that caused GBM Grid Search to hang.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10675'>#10675</a>] - Subset h2o frame now allows 0 row subset - just as data.frame.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10712'>#10712</a>] - Fixed an issue that caused the R `apply` method to fail to work with `h2o.var()`.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10755'>#10755</a>] - PCA no longer reports errors when using PCA on wide datasets with `pca_method = Randomized`. Note that it is still not recommended to use Randomized with wide datasets.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10796'>#10796</a>] - Jenkins builds no longer all share the same R package directory, and new H2O R libraries are installed during testing. </li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10801'>#10801</a>] -  When trimming is done, H2O now checks if it passes the beginning of the string. This check prevents the code from going further down the memory with negative indexes.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10866'>#10866</a>] - Stacked Ensembles no longer fails when the `fold_assignment` for base learners is not `Modulo`. </li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10881'>#10881</a>] - Fixed an issue that caused H2O to generate invalid code in POJO for PCA/SVM.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10969'>#10969</a>] - Instead of using random charset for getting bytes from strings, the source code now centralizes "byte extraction" in StringUtils. This prevents different build machines from using different default encoders.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10979'>#10979</a>] - When performing a Random Hyperparameter Search, if the model parameter seed is set to the default value but a search_criteria seed is not, then the model parameter seed will now be set to search_criteria seed+0, 1, 2, ..., model_number. Seeding the built models makes random hyperparameter searches more repeatable.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10989'>#10989</a>] - Fixed a bad link that was included in the "A K/V Store for In-Memory Analytics, Part 2" blog.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11027'>#11027</a>] - Comments are now permitted in Content-Type header for application/json mime type. As a result, specifying content-type charset no longer results in the request body being ignored. </li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11032'>#11032</a>] - Improved the Python `group_by` option count column name to match the R client.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11035'>#11035</a>] - Fixed broken links in the "Hacking Algorithms into H2O" blog post.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11045'>#11045</a>] - The Python API now provides a method to extract parameters from `cluster_status`.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11060'>#11060</a>] - Fixed incorrect parsing of input parameters. Previously, system property parsing logic added the value of any system property other than "ga_opt_out" to the arguments list if a property was prefixed with "ai.h2o.". This caused an attempt to parse the value of a system property as if it were itself a system property and at times resulted in an "Unknown Argument" error. </li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11063'>#11063</a>] - Fixed intermittent pyunit_javapredict_dynamic_data_paramsDR. </li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11066'>#11066</a>] - Fixed orc parser test by setting timezone to local time.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11074'>#11074</a>] - H2O can now correctly handle preflight OPTIONS calls - specifically in the event of a (1) CORS request and (2) the request has a content type other than text/plain, application/x-www-form-urlencoded, or multipart/form-data.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11091'>#11091</a>] - In the REST API, POST of application/json requests no longer fails if requests expect required fields.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11105'>#11105</a>] - The R client `impute` function now checks for categorical values and returns an error if none exist.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11120'>#11120</a>] - Fixed a filepath issue that occurred on Windows 7 systems when specifying a network drive.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11123'>#11123</a>] - Added a response column to Stacked Ensembles so that it can be exposed in the Flow UI.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11124'>#11124</a>] - Updated the list of required packages on the H2O download page for the Python client.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11139'>#11139</a>] - Updated the header in the Confusion Matrix to make the list of actual vs predicted values more clear.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11189'>#11189</a>] - Explicit 1-hot encoding in FrameUtils no longer generates an invalid order of column names. MissingLevel is now the last column.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11193'>#11193</a>] - Fixed an issue that caused ModelBuilder to leak xval frames if hyperparameter errors existed. </li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11200'>#11200</a>] - Fixed an issue that caused PCA model output to fail to display the Importance of Components.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11203'>#11203</a>] - When using the H2O Python client, the varimp() function can now be used in PCA to retrieve the Importance of Components details.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11204'>#11204</a>] - Fixed an issue that caused an ArrayIndexOutOfBoundsException in GLM.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11205'>#11205</a>] - When a main model is cloned to create the CV models, clearValidationMessages() is now called. Messages are no longer all thrown into a single bucket, which previously caused confusion with the `error_count()`.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11206'>#11206</a>] - ModelBuilder.message(...) now correctly bumps the error count when the message is an error.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11208'>#11208</a>] - Fixed an issue with unseen categorical levels handling in GLM scoring. Prediction with "skip" missing value handling in GLM with more than one variable no longer fails.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11210'>#11210</a>] - ModelMetricsRegression._mean_residual_deviance is now exposed. For all algorithms except GLM, this is the mean residual deviance. For GLM, this is the total residual deviance.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11216'>#11216</a>] - Fixed an issue that caused the`~` operator to fail when used in the Python client. Now, all logical operators set their results as Boolean.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11218'>#11218</a>] - Fixed an issue that caused an assertion error in GLM.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11220'>#11220</a>] - In GLM, fixed an issue that caused GLM to fail when `quasibinomial` was specified with a link other than the default. Specifying an incorrect link for the quasibinomial family will now result in an error message.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11239'>#11239</a>] - Improved the doc strings for `sample_rate_per_class` in R and Python.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11240'>#11240</a>] - Fixed a bug in the cosine distance formula.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11241'>#11241</a>] - Fixed an issue with CBSChunk set with long argument.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11252'>#11252</a>] - C0DChunk with con == NaN now works with strings.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11267'>#11267</a>] - When retrieving a Variable Importance plot using the H2O Python client, the default number of features shown is now 10 (or all if < 10 exist). Also reduced the top and bottom margins of the Y axis. </li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11269'>#11269</a>] - When retrieving a Variable Importance plot using the H2O R client, the default number of features shown is now 10 (or all if < 10 exist).</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11302'>#11302</a>] - Fixed an ORC stream parse.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11314'>#11314</a>] - Appended constant string to frame.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11379'>#11379</a>] - Fixed an issue with the View Log option in Flow.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11383'>#11383</a>] - The h2o.deepwater.available function is now working in the R API.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11426'>#11426</a>] - Fixed a bug with Log.info that resulted in bypassing log initialization.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11427'>#11427</a>] - LogsHandler now checks whether logging on specific level is enabled before accessing the particular log.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11430'>#11430</a>] - Fixed a logging issue that caused PID values to be set to an incorrect value. H2O now initializes PID before we initializing SELF_ADDRESS. This change was necessary because initialization of SELF_ADDRESS triggers buffered logged messages to be logged, and PID is part of the log header.</li>
</ul>

<h4>Epic</h4>

<ul>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10279'>#10279</a>] - Added supported for iSAX 2.0. This algorithm is a time series indexing strategy that reduces the dimensionality of a time series along the time axis. For example, if a time series had 1000 unique values with data across 500 rows, you can reduce this dataset to a time series that uses 100 unique values across 10 buckets along the time span. The following demos are available for more information:
	<ul>
		<li>Python - <a href='https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/isax2.ipynb'>https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/isax2.ipynb</a></li>
		<li>R - <a href='https://github.com/h2oai/h2o-3/blob/master/h2o-r/demos/isax.R'>https://github.com/h2oai/h2o-3/blob/master/h2o-r/demos/isax.R</a></li>
	</ul>
	</li>
</ul>

<h4>New Feature</h4>

<ul>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/13060'>#13060</a>] - Generate R bindings now available for REST API. </li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/13119'>#13119</a>] - Flow: Implemented test infrastructure for Jenkins/CI.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/13510'>#13510</a>] - The R client now reports to the user when memory limits have been exceeded.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/13038'>#13038</a>] - Added support to impute missing elements for RandomForest.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/15255'>#15255</a>] - Added a probability calibration plot function.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/9479'>#9479</a>] - A new h2o.pivot() function is available to allow pivoting of tables.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10570'>#10570</a>] - MOJO support has been extended to K-Means models.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10737'>#10737</a>] - Added two new options in GBM and DRF: `calibrate_model` and `calibrate_frame`. These flags allow you to retrieve calibrated probabilities for binary classification problems. </li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10746'>#10746</a>] - In Stacked Ensembles, added support for passing in models instead of model IDs when using the R client.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10863'>#10863</a>] - Added support for saving and loading binary Stacked Ensemble models.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10993'>#10993</a>] - Added support for idxmax, idxmin in Python H2OFrame to get an index of max/min values.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10994'>#10994</a>] - Added support for which.max, which.min support for R H2OFrame to get an index of max/min values.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11023'>#11023</a>] - A new h2o.sort() function is available in the H2O Python client. This returns a new Frame that is sorted by column(s) in ascending order. The column(s) to sort by can be either a single column name, a list of column names, or a list of column indices.</li>
</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11036'>#11036</a>] - Word2vec can now be used with the H2O Python client.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11040'>#11040</a>] - Missing values are filled sequentially for time series data. </li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11057'>#11057</a>] - Enabled cors option flag behind the sys.ai.h2o. prefix for debugging.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11155'>#11155</a>] - Added support for converting a Word2vec model to a Frame. </li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11169'>#11169</a>] - Created a Capability rest end point that gives the client an overview of registered extensions.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11219'>#11219</a>] - When viewing a model in Flow, a new **Download Gen Model** button is available, allowing you to save the h2o-genmodel.jar file locally. </li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11311'>#11311</a>] - Added an `h2o.flow()` function to base H2O. This allows users to open up a Flow window from within R and Python.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11357'>#11357</a>] - The `parse_type` parameter is now case insensitive.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11363'>#11363</a>] - Added automatic reduction of categorical levels for Aggregator. This can be done by setting `categorical_encoding=EnumLimited`.</li>
	<li>[NA] - In GBM and DRF, added two new categorical_encoding schemas: SortByResponse and LabelEncoding. More information about these options is available <a href="http://docs.h2o.ai/h2o/latest-stable/h2o-docs/data-science/algo-params/categorical_encoding.html">here</a>.
	</li>
</ul>

<h4>Story</h4>

<ul>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10821'>#10821</a>] - Added support for Leave One Covariate Out (LOCO). This calculates row-wise variable importances by re-scoring a trained supervised model and measuring the impact of setting each variable to missing or its most central value (mean or median & mode for categoricals).</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10940'>#10940</a>] - Removed support for Java 6.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11163'>#11163</a>] - Integrated XGBoost with H2O core as a separate extension module.</li>
</ul>


<h4>Task </h4>

<ul>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10420'>#10420</a>] - Users can now run predictions in R using a MOJO or POJO without running h2o running.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10977'>#10977</a>] - Created a test to verify that random grid search honors the `max_runtime_secs` parameter.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11084'>#11084</a>] - Removed javaMess.txt from scripts</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11127'>#11127</a>] - A new `node()` function is available for retrieving node information from an H2O Cluster.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11242'>#11242</a>] - Improved the R/Py doc strings for the `sample_rate_per_class` parameter.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11298'>#11298</a>] - Users can now optionally build h2o.jar with a visualization data server using the following: `./gradlew -PwithVisDataServer=true -PvisDataServerVersion=3.14.0 :h2o-assemblies:main:projects`</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11339'>#11339</a>] - Removed support for the following Hadoop platforms: CDH 5.2, CDH 5.3, and HDP 2.1.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11351'>#11351</a>] - Added the ability to go from String to Enum in PojoUtils.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11364'>#11364</a> - Continued modularization of H2O by removing reflections utils and replace them by SPI.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11366'>#11366</a>] - Removed the deprecated `h2o.importURL` function from the R API.</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11375'>#11375</a>] - Stacked Ensembles now removes any unnecessary frames, vecs, and models that were produced when compiled.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11378'>#11378</a>] - Updated R and Python doc strings to indicate that users can save and load Stacked Ensemble binary models. In the User Guide, updated the FAQ that previously indicated users could not save and load stacked ensemble models.</li>
</ul>

<h4> Improvement </h4>

<ul>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10011'>#10011</a>] - Improved error handling when users receive the following error:
`Error: lexical error: invalid char in json text.
	</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10411'>#10411</a>] - In PCA, when the user specifies a value for k that is <=0, then all principal components will automatically be calculated.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10804'>#10804</a>] - Exposed metalearner and base model keys in R/Py StackedEnsemble object.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10962'>#10962</a>] - The `h2o.download_pojo()` function now accepts a `jar_name` parameter, allowing users to create custom names for the downloaded file. </li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10992'>#10992</a>] - Added port and ip details to the error logs for h2o cloud.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11030'>#11030</a>] - When using Hadoop with SSL Internode Security, the `-internal_security` flag is now deprecated in favor of the `-internal_security_conf` flag.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11058'>#11058</a>] - Scala version of udf now serializes properly in multinode.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11070'>#11070</a>] - Fixed an NPM warn message.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11073'>#11073</a>] - Updated the documentation for using H2O with Anaconda and included an end-to-end example.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11079'>#11079</a>] - Arguments in h2o.naiveBayes in R are now the same as Python/Java.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11096'>#11096</a>] - StackedEnsembles is now stable vs. experimental.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11145'>#11145</a>] - Introduced latest_stable_R and latest_stable_py links, making it easy to point users to the current stable version of H2O for Python and R.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11156'>#11156</a>] - In the R client, the default for `nthreads` is now -1. The documentation examples have been updated to reflect this change.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11196'>#11196</a>] - ModelMetrics can sort models by a different Frame.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11221'>#11221</a>] - The application type is now reported in YARN manager, and H2O now overrides the default MapReduce type to H2O type.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11305'>#11305</a>] - Added a title option to PrintMOJO utility</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11316'>#11316</a>] - Flow now uses ip:port for identifying the node as part of LogHandler.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11350'>#11350</a>] - Reduced the frequency of Hadoop heartbeat logging.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11369'>#11369</a>] - In GLM, quasibinomial models produce binomial metrics when scoring.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11376'>#11376</a>] - Implemented methods to get registered H2O capabilities in Python client.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11377'>#11377</a>] - Implemented methods to get registered H2O capabilities in R client.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11382'>#11382</a>] - Upgraded Flow to version 0.7.0</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11395'>#11395</a>] - Removed the `selection_strategy` argument from Stacked Ensembles.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11417'>#11417</a>] - In Stacked Ensembles, added support for passing in models instead of model IDs when using the Python client.</li>
		<li>[<a href='https://github.com/h2oai/h2o-3/issues/11420'>#11420</a>] - Provided a file that contains a list of licenses for each H2O dependency. This can be acquired using com.github.hierynomus.license.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11424'>#11424</a>] - H2O now explicitly checks if the port and baseport is within allowed port range.</li>
</ul>

<h4> Docs </h4>
<ul>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/9795'>#9795</a>] - Added documentation describing how to call Rapids expressions from Flow.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/15440'>#15440</a>] - Added parameter descriptions for Naive Bayes parameter.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10838'>#10838</a>] - Added examples for Naive Bayes parameter.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10965'>#10965</a>] - Added `label_encoder` and `sort_by_response` to the list of available `categorical_encoding` options.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10984'>#10984</a>] - Added support for KMeans in MOJO documentation.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/10968'>#10968</a>] - Added a topic to the Data Manipulation section describing the `group_by` function.
	</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11029'>#11029</a>] - In the Productionizing H2O section of the User Guide, added an example showing how to read a MOJO as a resource from a jar file.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11071'>#11071</a>] - Improved the R and Python documentation for coef() and coef_norm().
	</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11072'>#11072</a>] - In the GLM section of the User Guide, added a topic describing how to extract coefficient table information. This new topic includes Python and R examples.
	</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11073'>#11073</a>] - Added information about Anaconda support to the User Guide. Also included an IPython Notebook example.
	</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11085'>#11085</a>] - Added Word2vec to list of supported algorithms on <a href='http://docs.h2o.ai/h2o/latest-stable/index.html#algorithms'>docs.h2o.ai</a>.
	</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11090'>#11090</a>] - Uncluttered the H2O User Guide. Combined serveral topics on the left navigation/TOC. Some changes include the following:
	<ul>
		<li>Moved AWS, Azure, DSX, and Nimbix to a new Cloud Integration section.</li>
		<li>Added a new **Getting Data into H2O** topic and moved the Supported File Formats and Data Sources topics into this.</li>
		<li>Moved POJO/MOJO topic into the **Productionizing H2O** section.</li>
		</ul>
	</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11095'>#11095</a>] - In the Security topic of the User Guide, added a section about using H2O with PAM authentication.
	</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11100'>#11100</a>] - Documentation for `h2o.download_all_logs()` now informs the user that the supplied file name must include the .zip extension.
	</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11107'>#11107</a> - Added an FAQ describing how to use third-party plotting libraries to plot metrics in the H2O Python client. This faq is available in the <a href='http://docs.h2o.ai/h2o/latest-stable/h2o-docs/faq/python.html'>FAQ > Python</a> topic.
	</li>
   <li>[<a href='https://github.com/h2oai/h2o-3/issues/11119'>#11119</a>] - Added an "Authentication Options" section to **Starting H2O > From the Command Line**. This section describes the options that can be set for all available supported authentication types. This section also includes flags for setting the newly supported Pluggable Authentication Module (PAM) authentication as well as Form Authentication and Session timeouts for H2O Flow.
   </li>
   	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11121'>#11121</a>] - Updated documentation to indicate that Word2vec is now supported for Python.</li>
   	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11142'>#11142</a>] - Added support for HDP 2.6 in the Hadoop Users section.
	</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11147'>#11147</a>] - Added two FAQs within the GLM section describing why H2O's glm differs from R's glm and the steps to take to get the two to match. These FAQs are available in the <a href='http://docs.h2o.ai/h2o/latest-stable/h2o-docs/data-science/glm.html#faq'>GLM > FAQ</a> section.
	</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11157'>#11157</a>] - Updated R examples in the User Guide to reflect that the default value for `nthreads` is now -1.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11170'>#11170</a>] - Updated the POJO Quick Start markdown file and Javadoc. </li>
   <li>[<a href='https://github.com/h2oai/h2o-3/issues/11179'>#11179</a>] - Added the `-principal` keyword to the list of Hadoop launch parameters.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11183'>#11183</a>] - In the Deep Learning topic, deleted the Algorithm section. The information included in that section has been moved into the Deep Learning FAQ.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11186'>#11186</a>] - Documented support for using H2O with Microsoft Azure Linux Data Science VM. Note that this is currently still a BETA feature.
	</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11198'>#11198</a>] - Added an FAQ describing YARN resource usage. This FAQ is available in the <a href='http://docs.h2o.ai/h2o/latest-stable/h2o-docs/faq/hadoop.html'>FAQ > Hadoop</a> topic.
	</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11226'>#11226</a>] - Added parameter descriptions for PCA parameters.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11227'>#11227</a>] - Added examples for PCA parameters.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11237'>#11237</a>] - A new h2o.sort() function is available in the H2O Python client. This returns a new Frame that is sorted by column(s) in ascending order. The column(s) to sort by can be either a single column name, a list of column names, or a list of column indices. Information about this function is available in the Python and R documentation.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11238'>#11238</a>] - Updated the "Using H2O with Microsoft Azure" topics. </li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11251'>#11251</a>] - Updated the "What is H2O" section in each booklet.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11274'>#11274</a>] - A Deep Water booklet is now available. A link to this booklet is on <a href='doc.h2o.ai'>docs.h2o.ai</a>.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11282'>#11282</a>] - Updated GLM documentation to indicate that GLM supports both multinomial and binomial handling of categorical values.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11283'>#11283</a>] - Added an FAQ describing the steps to take if a user encounters a "Server error - server 127.0.0.1 is unreachable at this moment" message. This FAQ is available in the <a href='http://docs.h2o.ai/h2o/latest-stable/h2o-docs/faq/r.html'>FAQ > R</a> topic.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11287'>#11287</a>] - Fixed documentation that described estimating in K-means. </li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11289'>#11289</a>] - Updated the documentation that described how to download a model in Flow.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11329'>#11329</a>] - The Data Sources topic, which describes that data can come from local file system, S3, HDFS, and JDBC, now also includes that data can be imported by specifying the URL of a file.</li>
	<li>[<a href='https://github.com/h2oai/h2o-3/issues/11352'>#11352</a>] - H2O now supports GPUs. Updated the FAQ that indicated we do not, and added a pointer to Deep Water.</li>
</ul>

### Ueno (3.10.4.8) - 5/21/2017

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-ueno/8/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-ueno/8/index.html</a>


<h4>        Bug Fix
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11013'>#11013</a>] -         Python: Frame summary does not return Python object
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11204'>#11204</a>] -         AIOOB with GLM
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11220'>#11220</a>] -         glm : quasi binomial with link other than default causes an h2o crash
</li>
</ul>

<h4>        Improvement
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11222'>#11222</a>] -         Create new /3/SteamMetrics REST API endpoint
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11321'>#11321</a>] -         Steam hadoop user impersonation
</li>
</ul>


### Ueno (3.10.4.7) - 5/8/2017

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-ueno/7/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-ueno/7/index.html</a>

<h4>        Bug Fix
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11278'>#11278</a>] -         h2o on yarn:  H2O does not respect the cloud name in case of flatfile mode
</li>
</ul>

### Ueno (3.10.4.6) - 4/26/2017

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-ueno/6/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-ueno/6/index.html</a>


<h4>        Bug Fix
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11154'>#11154</a>] -         Problem with h2o.uploadFile on Windows
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11230'>#11230</a>] -         glm: get AIOOB exception on attached data
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11232'>#11232</a>] -         External cluster always reports &quot;&quot;Timeout for confirmation exceeded!&quot;
</li>
</ul>

### Ueno (3.10.4.5) - 4/19/2017

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-ueno/5/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-ueno/5/index.html</a>

<h4>        Bug Fix
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11182'>#11182</a>] -         Problem with h2o.merge in python
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11195'>#11195</a>] -         Failing SVM parse
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11197'>#11197</a>] -         Rollups computation errors sometimes get wrapped in a unhelpful exception and the original cause is hidden.
</li>
</ul>

### Ueno (3.10.4.4) - 4/15/2017

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-ueno/4/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-ueno/4/index.html</a>

<h4>        Technical task
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11133'>#11133</a>] -         Add documentation on how to create a config file
</li>
</ul>

<h4>        Bug Fix
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9741'>#9741</a>] -         PCA Rotations not displayed in Python API
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10971'>#10971</a>] -         Sparse matrix cannot be converted to H2O
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11118'>#11118</a>] -         Flow/Schema problem, predicting on frame without response returns empty model metrics
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11135'>#11135</a>] -         Proportion of variance in GLRM for single component has a value &gt; 1
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11140'>#11140</a>] -         HDP 2.6 add to the build
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11141'>#11141</a>] -         Set timeout for read/write confirmation in ExternalFrameWriter/ExternalFrameReader
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11150'>#11150</a>] -         GLM default solver gets AIIOB when run on dataset with 1 categorical variable and no intercept
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11174'>#11174</a>] -         Correct exit status reporting ( when running on YARN )
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11176'>#11176</a>] -         Documentation: Update GLM FAQ and missing_values_handling parameter regarding unseen categorical values
</li>
</ul>

<h4>        New Feature
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11064'>#11064</a>] -         H2O Flow UI Authentication
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11115'>#11115</a>] -         Implement session timeout for Flow logins
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11178'>#11178</a>] -         Document a new parameters for h2odriver.
</li>
</ul>

<h4>        Task
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11069'>#11069</a>] -         Wrap R examples in code so that they don&#39;t run on Mac OS
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11104'>#11104</a>] -         Export polygon function to fix CRAN note in h2o R package
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11137'>#11137</a>] -         Add a parameter that ignores the config file reader when h2o.init() is called
</li>
</ul>

<h4>        Improvement
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11128'>#11128</a>] -         Extend Watchdog client extension so cluster is also stopped when the client doesn&#39;t connect in specified timeout
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11177'>#11177</a>] -         Set hadoop user from h2odriver
</li>
</ul>

### Ueno (3.10.4.3) - 3/31/2017

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-ueno/3/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-ueno/3/index.html</a>

<h4>        Bug Fix
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10196'>#10196</a>] -         ARFF parser parses attached file incorrectly
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10986'>#10986</a>] -         Proxy warning message displays proxy with username and password.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11054'>#11054</a>] -         h2o.import_sql_table works in R but on python gives error
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11056'>#11056</a>] -         java.lang.IllegalArgumentException with PCA
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11076'>#11076</a>] -         Impute does not handle catgoricals when values is specified
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11108'>#11108</a>] -         Increase number of bins in partial plots
</li>
</ul>

<h4>        New Feature
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11051'>#11051</a>] -         h2o.transform can produce incorrect aggregated sentence embeddings
</li>
</ul>

<h4>        Improvement
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10754'>#10754</a>] -         Errors with PCA on wide data for pca_method = Power
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10991'>#10991</a>] -         Introduce mode in which failure of H2O client ensures whole H2O clouds goes down
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11067'>#11067</a>] -         Add support for IBM IOP 4.2
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11075'>#11075</a>] -         Placeholder for: [SW-334]
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11080'>#11080</a>] -         Remove minor version from hadoop distribution in buildinfo.json file
</li>
</ul>

### Ueno (3.10.4.2) - 3/18/2017

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-ueno/2/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-ueno/2/index.html</a>

<h4>        Bug Fix
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11009'>#11009</a>] -         Deep Learning: mini_batch_size &gt;&gt;&gt; 1 causes OOM issues
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11024'>#11024</a>] -         head(df) and tail(df) results in R are inconsistent for datetime columns
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11033'>#11033</a>] -         GLM with family = multinomial, intercept=false, and weights or SkipMissing produces error
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11044'>#11044</a>] -         glm hot fix: fix model.score0 for multinomial
</li>
</ul>

<h4>        New Feature
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11022'>#11022</a>] -         Add option to specify a port range for the Hadoop driver callback
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11028'>#11028</a>] -         Support reading MOJO from a classpath resource
</li>
</ul>

<h4>        Improvement
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10947'>#10947</a>] -         Arff Parser doesn&#39;t recognize spaces in @attribute
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10988'>#10988</a>] -         How to generate Precision Recall AUC (PRAUC) from the scala code
</li>
</ul>

<h4>        Docs
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10870'>#10870</a>] -         Documentation: Add documentation for word2vec
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11007'>#11007</a>] -         Documentation: Add topic for using with IBM Data Science Experience
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/11038'>#11038</a>] -         Document &quot;driverportrange&quot; option of H2O&#39;s Hadoop driver
</li>
</ul>

### Ueno (3.10.4.1) - 3/3/2017

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-ueno/1/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-ueno/1/index.html</a>

<h4>        Technical task
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10837'>#10837</a>] -         Documentation: Naive Bayes links to parameters section
</li>
</ul>

<h4>        Bug Fix
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10714'>#10714</a>] -         Error in predict, performance functions caused by fold_column
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10717'>#10717</a>] -         Kmeans Centroid info not Rendered through Python API
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10724'>#10724</a>] -         PCA &quot;Importance of Components&quot; returns &quot;data frame with 0 columns and 0 rows&quot;
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10762'>#10762</a>] -         Stratified sampling does not split minority class
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10781'>#10781</a>] -         R Kmean&#39;s user_point doesn&#39;t get used
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10799'>#10799</a>] -         Setting -context_path doesn&#39;t change REST API path
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10826'>#10826</a>] -         K-means Training Metrics do not match Prediction Metrics with same data
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10832'>#10832</a>] -         h2o-py/tests/testdir_hdfs/pyunit_INTERNAL_HDFS_timestamp_date_orc.py failing
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10909'>#10909</a>] -         gradle update broke the build
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10910'>#10910</a>] -         H2O config (~/.h2oconfig) should allow user to specify username and password
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10923'>#10923</a>] -         Flow/R/Python - H2O cloudInfo should show if cluster is secured or not
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10930'>#10930</a>] -         FLOW fails to display custom models including Word2Vec
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10931'>#10931</a>] -         Import json module as different alias in Python API
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10932'>#10932</a>] -         Stacked Ensemble docstring example is broken
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10933'>#10933</a>] -         The autogen R bindings have an incorrect definition for the y argument
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10938'>#10938</a>] -         AIOOB while training an H2OKMeansEstimator
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10955'>#10955</a>] -         Fix bug in randomgridsearch and Fix intermittent pyunit_gbm_random_grid_large.py
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10956'>#10956</a>] -         Typos in Stacked Ensemble Python H2O User Guide example code
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10963'>#10963</a>] -         StackedEnsemble: stacking fails if combined with ignore_columns
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10973'>#10973</a>] -         AIOOB in GLM
</li>
</ul>

<h4>        New Feature
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10748'>#10748</a>] -         Documentation: Add Data Munging topic for file name globbing
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10902'>#10902</a>] -         Integration to add new top-level Plot menu to Flow
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10929'>#10929</a>] -         Add stddev to PDP computation
</li>
</ul>

<h4>        Task
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10588'>#10588</a>] -         Update h2o-py README
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10694'>#10694</a>] -         Generate Python API tests for H2O Cluster commands
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10810'>#10810</a>] -         Add documentation for python GroupBy class
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10811'>#10811</a>] -         Document python&#39;s Assembly and ConfusionMatrix classes, add python API tests as well
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10831'>#10831</a>] -         Clean up R docs
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10879'>#10879</a>] -         Documentation: Summarize the method for estimating k in kmeans and add to docs
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10899'>#10899</a>] -         Update links to Stacking on docs.h2o.ai
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10912'>#10912</a>] -         H2O config (~/.h2oconfig) should allow user to specify username and password
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10957'>#10957</a>] -         Check if strict_version_check is TRUE when checking for config file
</li>
</ul>

<h4>        Improvement
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10679'>#10679</a>] -         Documentation: Add info about sparse data support
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10682'>#10682</a>] -         h2o doc deeplearning:  clarify what the (heuristics)defaults for auto are in categorical_encoding
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10815'>#10815</a>] -         Saving/serializing currently existing, detailed model information
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10854'>#10854</a>] -         Py/R: Remove unused &#39;cluster_id&#39; parameter
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10876'>#10876</a>] -         Update GBM FAQ
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10887'>#10887</a>] -         Documentation: Add info about imputing data in Flow and in Data Manipulation
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10891'>#10891</a>] -         Documentation: Add instructions for running demos
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10898'>#10898</a>] -         AIOOB Exception with fold_column set with kmeans
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10946'>#10946</a>] -         Modify h2o#connect function to accept config with connect_params field
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10950'>#10950</a>] -         Change of h2o.connect(config) interface to support Steam
</li>
</ul>

### Tverberg (3.10.3.5) - 2/16/2017

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-tverberg/5/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-tverberg/5/index.html</a>

<h4>        Bug Fix
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10744'>#10744</a>] -         GLM with interaction parameter and cross-validation cause Exception
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10812'>#10812</a>] -         pca: hangs on attached data
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10857'>#10857</a>] -         StepOutOfRangeException when building GBM model
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10869'>#10869</a>] -         py unique() returns frame of integers (since epoch) instead of frame of unique dates
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10872'>#10872</a>] -         py date comparisons don&#39;t work for rows &gt; 1
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10873'>#10873</a>] -         AstUnique drops column types
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10906'>#10906</a>] -         In R, the confusion matrix at the end doesn’t say: vertical: actual, across: predicted
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10907'>#10907</a>] -         AIOOB  in GLM with hex.DataInfo.getCategoricalId(DataInfo.java:952) is the error with 2 fold cross validation
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10927'>#10927</a>] -         Parse fails when trying to parse large number of Parquet files
</li>
<li>[<a href='https://github.com/h2oai/private-h2o-3/issues/93'>private-#93</a>] -         POJO doesn&#39;t include Forest classes
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10935'>#10935</a>] -         moment producing wrong dates
</li>
</ul>

### Tverberg (3.10.3.4) - 2/3/2017

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-tverberg/4/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-tverberg/4/index.html</a>

<h4>        Bug Fix
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10858'>#10858</a>] -         Importing data in python returns error - TypeError: expected string or bytes-like object
</li>
</ul>

### Tverberg (3.10.3.3) - 2/2/2017

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-tverberg/3/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-tverberg/3/index.html</a>

<h4>        Bug Fix
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10732'>#10732</a>] -         Standard Errors in GLM: calculating and showing specifically when called
</li>
</ul>

<h4>        Improvement
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10882'>#10882</a>] -         Decrease size of h2o.jar
</li>
</ul>

### Tverberg (3.10.3.2) - 1/31/2017

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-tverberg/2/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-tverberg/2/index.html</a>

<h4>        Bug Fix
</h4>
<ul>
<li> Hotfix: Remove StackedEnsemble from Flow UI. Training is only supported from Python and R interfaces. Viewing is supported in the Flow UI.
</li>
</ul>

### Tverberg (3.10.3.1) - 1/30/2017

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-tverberg/1/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-tverberg/1/index.html</a>

<h4>        Bug Fix
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/15369'>#15369</a>] -         Using asfactor() in Python client cannot allocate to a variable
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10034'>#10034</a>] -         R API&#39;s h2o.interaction() does not use destination_frame argument
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10597'>#10597</a>] -         Errors with PCA on wide data for pca_method = GramSVD which is the default
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10639'>#10639</a>] -         StackedEnsemble should work for regression
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10761'>#10761</a>] -         h2o gbm : for an unseen categorical level, discrepancy in predictions when score using h2o vs pojo/mojo
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10779'>#10779</a>] -         Negative indexing for H2OFrame is buggy in R API
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10790'>#10790</a>] -         Relational operators don&#39;t work properly with time columns.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10859'>#10859</a>] -         java.lang.AssertionError when using h2o.makeGLMModel
</li>
</ul>

<h4>        Story
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10352'>#10352</a>] -         StackedEnsemble: put ensemble creation into the back end
</li>
</ul>

<h4>        New Feature
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/13019'>#13019</a>] -         Implement word2vec in h2o
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10540'>#10540</a>] -         Ability to Select Columns for PDP computation in Flow
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10777'>#10777</a>] -         Add PCA Estimator documentation to Python API Docs
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10798'>#10798</a>] -         Documentation: Add information about Azure support to H2O User Guide (Beta)
</li>
</ul>

<h4>        Task
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10251'>#10251</a>] -         h2o.create_frame(): if randomize=True, `value` param cannot be used
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10353'>#10353</a>] -         REST: implement simple ensemble generation API
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10740'>#10740</a>] -         Modify R REST API to always return binary data
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10741'>#10741</a>] -         Safe GET calls for POJO/MOJO/genmodel
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10760'>#10760</a>] -         Import files by pattern
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10780'>#10780</a>] -         StackedEnsemble: Add to online documentation
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10834'>#10834</a>] -         Add Stacked Ensemble code examples to R docs
</li>
</ul>

<h4>        Improvement
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10173'>#10173</a>] -         Documentation: As a K-Means user, I want to be able to better understand the parameters
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10638'>#10638</a>] -         StackedEnsemble: add tests in R and Python to ensure that a StackedEnsemble performs at least as well as the base_models
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10753'>#10753</a>] -         Clean up the generated Python docs
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10791'>#10791</a>] -         Filter H2OFrame on pandas dates and time (python)
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10808'>#10808</a>] -         Provide way to specify context_path via Python/R h2o.init methods
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10827'>#10827</a>] -         Modify gen_R.py for Stacked Ensemble
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10865'>#10865</a>] -         Add Stacked Ensemble code examples to Python docstrings
</li>
</ul>

### Tutte (3.10.2.2) - 1/12/2017

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-tutte/2/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-tutte/2/index.html</a>

<h4>        Bug Fix
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10772'>#10772</a>] -         Enable HDFS-like filesystems
</li>
</ul>

<h4>        Task
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10713'>#10713</a>] -         import functions required for r-release check
</li>
</ul>

### Tutte (3.10.2.1) - 12/22/2016

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-tutte/1/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-tutte/1/index.html</a>

<h4>        Bug Fix
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10206'>#10206</a>] -         Summary() doesn&#39;t update stats values when asfactor() is applied
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10409'>#10409</a>] -         rectangular assign to a categorical column does not work (should be possible to assign either an existing level, or a new one)
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10523'>#10523</a>] -         Numerical Column Names in H2O and R
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10593'>#10593</a>] -         pred_noise_bandwidth parameter is not reproducible with seed
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10622'>#10622</a>] -         Fix mktime() referencing from 0 base to 1 base for month and day
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10627'>#10627</a>] -         Binary loss functions return error in GLRM
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10644'>#10644</a>] -         python hist() plotted bars overlap
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10647'>#10647</a>] -         Python set_levels doesn&#39;t change other methods
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10650'>#10650</a>] -         h2o doc: glm grid search hyper parameters missing/incorrect listing. Presently glrm&#39;s is marked as glm&#39;s
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10662'>#10662</a>] -         Partial Plot incorrectly calculates for constant categorical column
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10676'>#10676</a>] -         h2o.proj_archetypes returns error if constant column is dropped in GLRM model
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10686'>#10686</a>] -         GLRM loss by col produces error if constant columns are dropped
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10693'>#10693</a>] -         isna() overwrites column names
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10709'>#10709</a>] -         NullPointerException with Quantile GBM, cross validation, &amp; sample_rate &lt; 1
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10716'>#10716</a>] -         R h2o.download_mojo broken - writes a 1 byte file
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10728'>#10728</a>] -         Seed definition incorrect in R API for RF, GBM, GLM, NB
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10731'>#10731</a>] -         h2o.glm: get AIOOB exception with xval and lambda search
</li>
</ul>

<h4>        New Feature
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10393'>#10393</a>] -         Supporting GLM binomial model to allow two arbitrary integer values
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10287'>#10287</a>] -         Implement ISAX calculations per ISAX word
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10288'>#10288</a>] -         Optimizations and final fixes for ISAX
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10568'>#10568</a>] -         Implement GLM MOJO
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10412'>#10412</a>] -         Variance metrics are missing from GLRM that are available in PCA
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10453'>#10453</a>] -         py h2o.as_list() should not return headers
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10615'>#10615</a>] -         Modify sum() calculation to work on rows or columns
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10636'>#10636</a>] -         make sure that the generated R bindings work with StackedEnsemble
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10730'>#10730</a>] -         Add HDP 2.5 Support
</li>
</ul>

<h4>        Task
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9937'>#9937</a>] -         Remove grid.sort_by method in Python API
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10598'>#10598</a>] -         Documentation: Add GLM to list of algorithms that support MOJOs
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10689'>#10689</a>] -         Documentation: Add quasibinomomial family in GLM
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10580'>#10580</a>] -         Add SLURM cluster documentation
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10595'>#10595</a>] -         Add memory check for GLRM before proceeding
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10663'>#10663</a>] -         Check to make sure hinge loss works for GLRM
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10700'>#10700</a>] -         Add parameters from _upload_python_object to H2OFrame constructor
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10701'>#10701</a>] -         Refer to .h2o.jar.env when detaching R package
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10702'>#10702</a>] -         Call on proper port when exiting R/detaching package
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10703'>#10703</a>] -         Modify search for config file in R api
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10715'>#10715</a>] -         properly handle url in R docs from autogen
</li>
</ul>

<h4>        Improvement
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10172'>#10172</a>] -         Documentation: As a GLM user, I want to be able to better understand the parameters
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10655'>#10655</a>] -         Fix bad/inconsistent/empty categorical (bitset) splits for DRF/GBM
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10691'>#10691</a>] -         Auto-generate R bindings
</li>
</ul>

### Turnbull (3.10.1.2) - 12/14/2016

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-turnbull/2/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-turnbull/2/index.html</a>

<h4>        Bug Fix
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9735'>#9735</a>] -         Starting h2o server from R ignores IP and port parameters
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10395'>#10395</a>] -         Treat 1-element numeric list as acceptable when numeric input required
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10421'>#10421</a>] -         h2o&#39;s cor() breaks R&#39;s native cor()
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10499'>#10499</a>] -         h2o.get_grid isn&#39;t working
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10512'>#10512</a>] -         `cor` function should properly pass arguments
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10534'>#10534</a>] -         Avoid confusing error message when column name is not found.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10536'>#10536</a>] -         overwrite_with_best_model fails when using checkpoint
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10538'>#10538</a>] -         plot.h2oModel in R no longer supports metrics with uppercase names (e.g. AUC)
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10546'>#10546</a>] -         Fix citibike R demo
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10600'>#10600</a>] -         Create an Attribute for Number of Interal Trees in Python
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10604'>#10604</a>] -         Error with early stopping and score_tree_interval on GBM
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10634'>#10634</a>] -         Python&#39;s coef() and coef_norm() should use column name not index
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10654'>#10654</a>] -         Perfbar does not work for hierarchical path passed via -h2o_context
</li>
</ul>

<h4>        New Feature
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10385'>#10385</a>] -         Show Partial Dependence Plots in Flow
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10525'>#10525</a>] -         Allow setting nthreads &gt; 255.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10603'>#10603</a>] -         Add RMSE, MAE, RMSLE, and lift_top_group as stopping metrics
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10618'>#10618</a>] -         Update h2o.mean in R to match Python API
</li>
</ul>

<h4>        Task
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10486'>#10486</a>] -         Document Partial Dependence Plot in Flow
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10526'>#10526</a>] -         Add R endpoint for cumsum, cumprod, cummin, and cummax
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10553'>#10553</a>] -         Modify correlation matrix calculation to match R
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10561'>#10561</a>] -         Remove max_confusion_matrix_size from booklets &amp; py doc
</li>
</ul>

<h4>        Improvement
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/private-h2o-3/issues/128'>private-#128</a>] -         aggregator should calculate domain for enum columns in aggregated output frames &amp; member frames based on current output or member frame
</li>
<li>[<a href='https://github.com/h2oai/private-h2o-3/issues/115'>private-#115</a>] -         Naive Bayes (and maybe GLM): Drop limit on classes that can be predicted (currently 1000)
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10530'>#10530</a>] -         Speed up GBM and DRF
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10653'>#10653</a>] -         Support `-context_path` to change servlet path for REST API
</li>
</ul>

<h4>        IT Help
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10194'>#10194</a>] -         Adding a custom loss-function
</li>
</ul>

### Turing (3.10.0.10) - 11/7/2016

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-turing/10/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-turing/10/index.html</a>

<h4>        Bug Fix
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10395'>#10395</a>] -         Treat 1-element numeric list as acceptable when numeric input required
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10579'>#10579</a>] -         Cannot determine file type
</li>
</ul>

### Turing (3.10.0.9) - 10/25/2016

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-turing/9/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-turing/9/index.html</a>


<h4>        Bug Fix
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10458'>#10458</a>] -         h2o.year() method does not return year
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10468'>#10468</a>] -         Regression Training Metrics: Deviance and MAE were swapped
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10477'>#10477</a>] -         h2o.max returns NaN even when na.rf condition is set to TRUE
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10500'>#10500</a>] -         Fix display of array-valued entries in TwoDimTables such as grid search results
</li>
</ul>

<h4>        Improvement
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10492'>#10492</a>] -         Optimize algorithm for automatic estimation of K for K-Means
</li>
<li>[<a href='https://github.com/h2oai/private-h2o-3/issues/127'>private-#127</a>] -         include flow, /3/ API accessible Aggregator model in h2o-3
</li>
</ul>

### Turing (3.10.0.8) - 10/10/2016

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-turing/8/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-turing/8/index.html</a>

<h4>        Technical task
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10275'>#10275</a>] -         R binding for new MOJO
</li>
</ul>

<h4>        Bug Fix
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10294'>#10294</a>] -         S3 API method PersistS3#uriToKey breaks expected contract
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10347'>#10347</a>] -         GLM multinomial with defaults fails on attached dataset
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10351'>#10351</a>] -         .structure() encounters list index out of bounds when nan is encountered in column
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10367'>#10367</a>] -         max_active_predi tors option in glm does not work anymore
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10372'>#10372</a>] -         Printed PCA model metrics in R is missing
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10388'>#10388</a>] -         R - Unnecessary JDK requirement on Windows
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10416'>#10416</a>] -         uuid columns with mostly missing values causes parse to fail.
</li>
<li>[<a href='https://github.com/h2oai/private-h2o-3/issues/171'>private-#171</a>] -         Fold Column not available in h2o.grid
</li>
</ul>

<h4>        New Feature
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/14907'>#14907</a>] -         Compute partial dependence data
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10332'>#10332</a>] -         Create Method to Return Columns of Specific Type
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10402'>#10402</a>] -         Find optimal number of clusters in K-Means
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10403'>#10403</a>] -         Add optional categorical encoding schemes for GBM/DRF
</li>
</ul>

<h4>        Task
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10242'>#10242</a>] -         Tasks for completing MOJO support
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10356'>#10356</a>] -         Ensure functions have `h2o.*` alias in R API
</li>
</ul>

<h4>        Improvement
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10376'>#10376</a>] -         Sync up functionality of download_mojo and download_pojo in R &amp; Py
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10410'>#10410</a>] -         Improve the stopping criterion for K-Means Lloyds iterations
</li>
<li>[<a href='https://github.com/h2oai/private-h2o-3/issues/174'>private-#174</a>] -         Encryption of H2O communication channels
</li>
<li>[<a href='https://github.com/h2oai/private-h2o-3/issues/137'>private-#137</a>] -         add option to Aggregator model to show ignored columns in output frame
</li>
</ul>

### Turing (3.10.0.7) - 9/19/2016

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-turing/7/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-turing/7/index.html</a>

<h4>        Bug Fix
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10215'>#10215</a>] -         NPE during categorical encoding with cross-validation (Windows 8 runit only??)
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10221'>#10221</a>] -         H2OFrame arithmetic/statistical functions return inconsistent types
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10230'>#10230</a>] -         Multi file parse fails with NPE
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10285'>#10285</a>] -         h2o.hist() does not respect breaks
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10311'>#10311</a>] -         importFiles, with s3n, gives NullPointerException
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10319'>#10319</a>] -         Python Structure() Breaks When Applied to Entire Dataframe
</li>
</ul>

<h4>        New Feature
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9645'>#9645</a>] -         Diff operation on column in H2O Frame
</li>
<li>[<a href='https://github.com/h2oai/private-h2o-3/issues/154'>private-#154</a>] -         calculate residuals  in h2o-3 and in flow and create a new frame with a new column that contains the residuals
</li>
</ul>

<h4>        Task
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9720'>#9720</a>] -         Clean up Python booklet code in repo
</li>
</ul>

<h4>        Improvement
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10211'>#10211</a>] -         In R, allow x to be missing (meaning take all columns except y) for all supervised algo&#39;s
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10244'>#10244</a>] -         median() should return a list of medians from an entire frame
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10249'>#10249</a>] -         Conduct rbind and cbind on multiple frames
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10297'>#10297</a>] -         Add argument to H2OFrame.print in R to specify number of rows
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10328'>#10328</a>] -         Suppress chunk summary in describe()
</li>
</ul>

### Turing (3.10.0.6) - 8/25/2016

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-turing/6/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-turing/6/index.html</a>

<h4>        Bug Fix
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/private-h2o-3/issues/164'>private-#164</a>] -         Hashmap in H2OIllegalArgumentException fails to deserialize &amp; throws FATAL
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9809'>#9809</a>] -         NPE in MetadataHandler
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10009'>#10009</a>] -         hist() fails for constant numeric columns
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10095'>#10095</a>] -         Client mode: flatfile requires list of all nodes, but a single entry node should be sufficient
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10126'>#10126</a>] -         Make CreateFrame reproducible for categorical columns.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10127'>#10127</a>] -         Fix intermittency of categorical encoding via eigenvector.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10130'>#10130</a>] -         isBitIdentical is returning true for two Frames with different content
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10140'>#10140</a>] -         AssertionError for DL train/valid with categorical encoding
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10154'>#10154</a>] -         Wrong MAE for observation weights other than 1.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10161'>#10161</a>] -         H2ODriver for CDH5.7.0 does not accept memory settings
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10191'>#10191</a>] -         H2OFrame.drop() leaves the frame in inconsistent state
</li>
</ul>

<h4>        New Feature
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9932'>#9932</a>] -         Implement skewness calculation for H2O Frames
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9933'>#9933</a>] -         Implement kurtosis calculation for H2O Frames
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10051'>#10051</a>] -         Add ability to do a deep copy in Python API
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10086'>#10086</a>] -         Add docs for h2o.make_metrics() for R and Python
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10136'>#10136</a>] -         Add RMSLE to model metrics
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10179'>#10179</a>] -         Return unique values of a categorical column as a Pythonic list
</li>
</ul>

<h4>        Task
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10152'>#10152</a>] -         Refactor and simplify implementation of Pearson Correlation
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10155'>#10155</a>] -         Add MAE to CV Summary
</li>
</ul>

<h4>        Improvement
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9640'>#9640</a>] -         Create h2o.* functions for H2O primitives
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10021'>#10021</a>] -         Add methods to get actual and default parameters of a model
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10055'>#10055</a>] -         Add ability to drop a list of columns or a subset of rows from an H2OFrame
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10061'>#10061</a>] -         Ensure all is*() functions return a list
</li>
</ul>

### Turing (3.10.0.3) - 7/29/2016

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-turing/3/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-turing/3/index.html</a>

<h4>        Bug Fix
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9739'>#9739</a>] -         Error when setting a string column to a single value in R/Py
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9894'>#9894</a>] -         R h2o.merge() ignores by.x and by.y
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10058'>#10058</a>] -         Download Logs broken URL from Flow
</li>
</ul>

<h4>        New Feature
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9887'>#9887</a>] -         H2O Version Check
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9947'>#9947</a>] -         Add an h2o.concat function equivalent to pandas.concat
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9973'>#9973</a>] -         Add Huber loss function for GBM and DL (for regression)
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9994'>#9994</a>] -         Add RMSE to model metrics
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10027'>#10027</a>] -         Add Mean Absolute Error to Model Metrics
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10031'>#10031</a>] -         Add mean absolute error to scoring history and model plotting
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10039'>#10039</a>] -         Add categorical encoding schemes for DL and Aggregator
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10078'>#10078</a>] -         Compute supervised ModelMetrics from predicted and actual values in Java/R
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10085'>#10085</a>] -         Compute supervised ModelMetrics from predicted and actual values in Python
</li>
</ul>

<h4>        Improvement
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/14849'>#14849</a>] -         Implement gradient checking for DL
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9567'>#9567</a>] -         Add better warning message to functions of H2OModelMetrics objects
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9946'>#9946</a>] -         Add demo datasets to Python package
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10036'>#10036</a>] -         Replace &quot;MSE&quot; with &quot;RMSE&quot; in scoring history table
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10045'>#10045</a>] -         Make all TwoDimTable Headers Pythonic in R and Python API
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10052'>#10052</a>] -         Achieve consistency between DL and GBM/RF scoring history in regression case
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10054'>#10054</a>] -         Disable R^2 stopping criterion in tree model builders
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10072'>#10072</a>] -         Remove R^2 from all model output except GLM
</li>
</ul>

### Turin (3.8.3.4) - 7/15/2016

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-turin/4/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-turin/4/index.html</a>

<h4>        Bug Fix
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9963'>#9963</a>] -         File parse from S3 extremely slow
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10068'>#10068</a>] -         Fix Deep Learning POJO for hidden dropout other than 0.5
</li>
</ul>

### Turin (3.8.3.2) - 7/1/2016

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-turin/2/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-turin/2/index.html</a>

<h4>        Bug Fix
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/13884'>#13884</a>] -         DRF: sample_rate=1 not permitted unless validation is performed
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/15026'>#15026</a>] -         create a set of tests which create large POJOs for each algo and compiles them
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/15228'>#15228</a>] -         Merge (method=&quot;radix&quot;) bug1
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/15231'>#15231</a>] -         Merge (method=&quot;radix&quot;) bug2
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9506'>#9506</a>] -         Fold Column not available in h2o.grid
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9893'>#9893</a>] -         h2o.merge(,method=&quot;radix&quot;) failing 15/40 runs
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9954'>#9954</a>] -         Parse: java.lang.IllegalArgumentException: 0 &gt; -2147483648
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9956'>#9956</a>] -         Cached errors are not printed if H2O exits
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9995'>#9995</a>] -          java.lang.ClassCastException for Quantile GBM
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10000'>#10000</a>] -         model_summary number of trees is too high for multinomial DRF/GBM models
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10002'>#10002</a>] -         NPE when accessing invalid null Frame cache in a Frame&#39;s vecs()
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10004'>#10004</a>] -         TwoDimTable version of a Frame prints missing value (NA) as 0
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10012'>#10012</a>] -         Fix tree split finding logic for some cases where min_rows wasn&#39;t satisfied and the entire column was no longer considered even if there were allowed split points
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10016'>#10016</a>] -         saveModel and loadModel don&#39;t work with windows c:/ paths
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10018'>#10018</a>] -         getStackTrace fails on NumberFormatException
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10019'>#10019</a>] -         TwoDimTable for Frame Summaries doesn&#39;t always show the full precision
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10020'>#10020</a>] -         DRF OOB scoring isn&#39;t using observation weights
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10022'>#10022</a>] -         AIOOBE when calling &#39;getModel&#39; in Flow while a GLM model is training
</li>
</ul>


<h4>        Task
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9619'>#9619</a>] -         Properly document the addition of missing_values_handling arg to GLM
</li>
</ul>

<h4>        Improvement
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/14588'>#14588</a>] -         Matt&#39;s new merge (aka join) integrated into H2O
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9756'>#9756</a>] -         Improved handling of missing values in tree models (training and testing)
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9983'>#9983</a>] -         IPv6 documentation
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9989'>#9989</a>] -         Stop GBM models once the effective learning rate drops below 1e-6.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/10017'>#10017</a>] -         Log input parameters during boot of H2O
</li>
</ul>

### Turchin (3.8.2.9) - 6/10/2016

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-turchin/9/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-turchin/9/index.html</a>

<h4>        Bug Fix
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9849'>#9849</a>] -         Python apply() doesn&#39;t recognize % (modulo) within lambda function
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9869'>#9869</a>] -         Documentation: Add RoundRobin histogram_type to GBM/DRF
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9886'>#9886</a>] -         Add &quot;seed&quot; option to GLM in documentation
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9902'>#9902</a>] -         Documentation: Update supported Hadoop versions
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9910'>#9910</a>] -         Models hang when max_runtime_secs is too small
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9911'>#9911</a>] -         Default min/max_mem_size to gigabytes in h2o.init
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9925'>#9925</a>] -         Add &quot;ignore_const_cols&quot; argument to glm and gbm for Python API
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9927'>#9927</a>] -         AIOOBE in GBM if no nodes are split during tree building
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9929'>#9929</a>] -         Negative R^2 (now NaN) can prevent early stopping
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9936'>#9936</a>] -         Two grid sorting methods in Py API - only one works sometimes
</li>
</ul>

<h4>        New Feature
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9680'>#9680</a>] -         Add seed argument to GLM
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9846'>#9846</a>] -         Add cor() function to Rapids
</li>
</ul>

<h4>        Task
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9930'>#9930</a>] -         Verify checkpoint argument in h2o.gbm (for R)
</li>
</ul>

<h4>        Improvement
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/14989'>#14989</a>] -         Sync up argument names in `h2o.init` between R and Python
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9924'>#9924</a>] -         Change `getjar` to `get_jar` in h2o.download_pojo in R
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9926'>#9926</a>] -         Change min_split_improvement default value from 0 to 1e-5 for GBM/DRF
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9938'>#9938</a>] -         Allow specification of &quot;AUC&quot; or &quot;auc&quot; or &quot;Auc&quot; for stopping_metrics, sorting of grids, etc.
</li>
</ul>

### Turchin (3.8.2.8) - 6/2/2016

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-turchin/8/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-turchin/8/index.html</a>

<h4>        Bug Fix
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9914'>#9914</a>] -         Make Random grid search consistent between clients for same parameters
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9916'>#9916</a>] -         Allow learn_rate_annealing to be passed to H2OGBMEstimator constructor in Python API
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9918'>#9918</a>] -         Fix typo in GBM/DRF Python API for col_sample_rate_change_per_level - was misnamed and couldn&#39;t be set
</li>
</ul>

<h4>        New Feature
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9908'>#9908</a>] -         Add a new metric: mean misclassification error for classification models
</li>
</ul>

<h4>        Improvement
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9901'>#9901</a>] -         No longer print negative R^2 values - show NaN instead
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9913'>#9913</a>] -         Add xval=True/False as an option to model_performance() in Python API
</li>
</ul>

### Turchin (3.8.2.6) - 5/24/2016

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-turchin/6/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-turchin/6/index.html</a>

<h4>        Bug Fix
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/14860'>#14860</a>] -         Number of active predictors is off by 1 when Intercept is included
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9871'>#9871</a>] -         GLM with cross-validation AIOOBE (+ Grid-Search + Multinomial, may be related)
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9872'>#9872</a>] -         Improved accuracy for histogram_type=&quot;QuantilesGlobal&quot; for DRF/GBM
</li>
</ul>

<h4>        New Feature
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/14669'>#14669</a>] -         GLM needs &#39;seed&#39; argument for new (random) implementation of n-folds
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9680'>#9680</a>] -         Add seed argument to GLM
</li>
</ul>

<h4>        Improvement
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9857'>#9857</a>] -         Remove _Dev from file name _DataScienceH2O-Dev
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9874'>#9874</a>] -         Clean up overly long and duplicate error message in KeyV3
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9882'>#9882</a>] -         Allow the user to pass column types of an existing H2OFrame during Parse/Upload in R and Python
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9883'>#9883</a>] -         Tweak Parser Heuristic
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9884'>#9884</a>] -         GLM improvements and fixes
</li>
</ul>

### Turchin (3.8.2.5) - 5/19/2016

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-turchin/5/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-turchin/5/index.html</a>

<h4>        Technical task
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9839'>#9839</a>] -         Documentation update for relevel
</li>
</ul>

<h4>        Bug Fix
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/15189'>#15189</a>] -         DRF: cannot compile pojo
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/15210'>#15210</a>] -         GBM pojo compile failures
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9808'>#9808</a>] -         Bug in h2o-py H2OScaler.inverse_transform()
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9810'>#9810</a>] -         Add NAOmit() to Rapids
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9827'>#9827</a>] -         AIOOBE in Vec.factor (due to Parse bug?)
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9833'>#9833</a>] -         In grid search, max_runtime_secs without max_models hangs
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9862'>#9862</a>] -         GBM&#39;s fold_assignment = &quot;Stratified&quot; breaks with missing values in response column
</li>
</ul>

<h4>        New Feature
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9667'>#9667</a>] -         Implement h2o.relevel, equivalent of base R&#39;s relevel function
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9788'>#9788</a>] -         Add Kerberos authentication to Flow
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9823'>#9823</a>] -         Summaries Fail in rdemo.citi.bike.small.R
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9825'>#9825</a>] -         DimReduction for EasyModelAPI
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9844'>#9844</a>] -         Make histograms truly adaptive (quantiles-based) for DRF/GBM
</li>
</ul>

<h4>        Task
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9832'>#9832</a>] -         Add a list of gridable parameters to the docs
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9834'>#9834</a>] -         Add relevel() to Python API
</li>
</ul>

<h4>        Improvement
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9835'>#9835</a>] -         Improve the progress bar based on max_runtime_secs &amp; max_models &amp; actual work
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9838'>#9838</a>] -         Improve GBM/DRF reproducibility for fixed parameters and hardware
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9841'>#9841</a>] -         Check sanity of random grid search parameters (max_models and max_runtime_secs)
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9842'>#9842</a>] -         Add Job&#39;s remaining time to Flow
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9848'>#9848</a>] -         Add enum option &#39;histogram_type&#39; to DRF/GBM (and remove random_split_points)
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9852'>#9852</a>] -         JUnit: Separate POJO namespace during junit testing
</li>
</ul>

### Turchin (3.8.2.3) - 4/25/2016

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-turchin/3/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-turchin/3/index.html</a>

<h4>        Bug Fix
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9783'>#9783</a>] -         Incorrect sparse chunk getDoubles() extraction
</li>
</ul>

<h4>        New Feature
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9759'>#9759</a>] -         Create h2o.get_grid
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9767'>#9767</a>] -         Implement distributed Aggregator for visualization
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9768'>#9768</a>] -         Add col_sample_rate_change_per_level for GBM/DRF
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9769'>#9769</a>] -         Add learn_rate_annealing for GBM
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9770'>#9770</a>] -         Add random cut points for histograms in DRF/GBM (ExtraTreesClassifier)
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9782'>#9782</a>] -         Add limit on max. leaf node contribution for GBM
</li>
</ul>

<h4>        Task
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9780'>#9780</a>] -         Add tests for early stopping logic (stopping_rounds &gt; 0)
</li>
</ul>

<h4>        Improvement
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9807'>#9807</a>] -         Make NA split decisions internally more consistent
</li>
</ul>

### Turchin (3.8.2.2) - 4/8/2016

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-turchin/2/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-turchin/2/index.html</a>

<h4>        Bug Fix
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9755'>#9755</a>] -         Implement max_runtime_secs to limit total runtime of building GLM models with and without cross-validation enabled
</li>
</ul>

<h4>        New Feature
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9749'>#9749</a>] -         Add stratified sampling per-tree for DRF/GBM
</li>
</ul>

### Turchin (3.8.2.1) - 4/7/2016

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-turchin/1/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-turchin/1/index.html</a>

<h4>        Bug Fix
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9701'>#9701</a>] -         AIOOBE for quantile regression with stochastic GBM
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9704'>#9704</a>] -         Naive Bayes AIOOBE
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9706'>#9706</a>] -         AIOOBE for GBM if test set has different number of classes than training set
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9709'>#9709</a>] -         Number of CPUs incorrect in Flow when using a hypervisor
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9730'>#9730</a>] -         Grid search runtime isn&#39;t enforced for CV models
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9754'>#9754</a>] -         AIOOBE in GLM for dense rows in sparse data
</li>
</ul>

<h4>        New Feature
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9483'>#9483</a>] -         Compute and display statistics of cross-validation model metrics
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9708'>#9708</a>] -         Add keep_cross_validation_fold_assignment and more CV accessors
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9710'>#9710</a>] -         Set initial weights and biases for DL models
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9725'>#9725</a>] -         Control min. relative squared error reduction for a node to split (DRF/GBM)
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9740'>#9740</a>] -         On-the-fly interactions for GLM
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9749'>#9749</a>] -         Add stratified sampling per-tree for DRF/GBM
</li>
</ul>

<h4>        Task
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/15002'>#15002</a>] -         Create test cases to show that POJO prediction behavior can be different than in-h2o-model prediction behavior
</li>
</ul>

<h4>        Improvement
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/12992'>#12992</a>] -         Populate start/end/duration time in milliseconds for all models
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9633'>#9633</a>] -         Consistent handling of missing categories in GBM/DRF (and between H2O and POJO)
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9674'>#9674</a>] -         Alert the user if columns can&#39;t be histogrammed due to numerical extremities
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9693'>#9693</a>] -         GLM should generate error if user enter an alpha value greater than 1.
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9698'>#9698</a>] -         Create full holdout prediction frame for cross-validation predictions
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9703'>#9703</a>] -         Support Validation Frame and Cross-Validation for Naive Bayes
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9744'>#9744</a>] -         Add class_sampling_factors argument to DRF/GBM for R and Python APIs
</li>
</ul>

### Turan (3.8.1.4) - 3/16/16

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-turan/4/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-turan/4/index.html</a>

<h4>        Bug Fix
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/13525'>#13525</a>] -         KMeans: Size of clusters in Model Output is different from the labels generated on the training set
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/14940'>#14940</a>] -         GLM fails on negative alpha
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9656'>#9656</a>] -         countmatches bug
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9665'>#9665</a>] -         bug in processTables in communication.R
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9679'>#9679</a>] -          Allow strings to be set to NA
</li>
</ul>

<h4>        New Feature
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9657'>#9657</a>] -         Implement Shannon entropy for a string
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9658'>#9658</a>] -         Implement proportion of substrings that are valid English words
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9671'>#9671</a>] -         Add utility function, h2o.ensemble_performance for ensemble and base learner metrics
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9678'>#9678</a>] -         Add date/time and string columns to createFrame.
</li>
</ul>

<h4>        Task
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/13073'>#13073</a>] -         Certify sparkling water on CDH5.2
</li>
</ul>

<h4>        Improvement
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/13289'>#13289</a>] -         Make python equivalent of as.h2o() work for numpy array and pandas arrays
</li>
</ul>


### Turan (3.8.1.3) - 3/6/16

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-turan/3/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-turan/3/index.html</a>

<h4>        Bug Fix
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9583'>#9583</a>] -         Collinear columns cause NPE for P-values computation
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9659'>#9659</a>] -         Update default values in h2o.glm.wrapper from -1 and NaN to NULL
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9660'>#9660</a>] -         AIOOBE in NewChunk
</li>
</ul>

<h4>        New Feature
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/13012'>#13012</a>] -         Hive UDF form for Scoring Engine POJO for H2O Models
</li>
</ul>

### Turan (3.8.1.2) - 3/4/16

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-turan/2/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-turan/2/index.html</a>

<h4>        Bug Fix
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9651'>#9651</a>] -         /3/scalaint fails with a 404
</li>
</ul>

<h4>        New Feature
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9649'>#9649</a>] -         Allow DL models to be pretrained on unlabeled data with an autoencoder
</li>
</ul>

<h4>        Improvement
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9646'>#9646</a>] -         H2O Flow does not contain CodeMirror library
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9648'>#9648</a>] -         Model export fails: parent directory does not exist
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9650'>#9650</a>] -         Flow doesn&#39;t show DL AE error (MSE) plot
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9655'>#9655</a>] -         Do not compute expensive quantiles during h2o.summary call
</li>
</ul>

### Turan (3.8.1.1) - 3/3/16

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-turan/1/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-turan/1/index.html</a>

<h4>        Technical task
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9643'>#9643</a>] -         implement random (stochastic) hyperparameter search
</li>
</ul>

<h4>        Bug Fix
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9578'>#9578</a>] -         Parse: Incorrect assertion error caused by very large few column data
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9588'>#9588</a>] -         h2o::|,&amp; operator handles NA&#39;s differently than base::|,&amp;
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9594'>#9594</a>] -         h2o::as.logical behavior is different than base::as.logical
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9620'>#9620</a>] -         Importing CSV file is not working with &quot;java -jar h2o.jar -nthreads -1&quot;
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9623'>#9623</a>] -         Allow DL reproducible mode to work with user-given train_samples_per_iteration &gt;= 0
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9628'>#9628</a>] -         Grid Search NPE during Flow display after grid was cancelled
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9631'>#9631</a>] -         NPE in initialMSE computation for GBM
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9634'>#9634</a>] -         DL checkpoint restart doesn&#39;t honor a change in stopping_rounds
</li>
</ul>

<h4>        New Feature
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/14842'>#14842</a>] -         Add option to train with mini-batch updates for DL
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9636'>#9636</a>] -         Return leaf node assignments for DRF + GBM
</li>
</ul>

<h4>        Improvement
</h4>
<ul>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9612'>#9612</a>] -         Change default functionality of as_data_frame method in Py H2O
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9635'>#9635</a>] -         Add method setNames for setting column names on H2O Frame
</li>
<li>[<a href='https://github.com/h2oai/h2o-3/issues/9641'>#9641</a>] -         NPE in Log.write during cluster shutdown
</li>
</ul>

### Tukey (3.8.0.6) - 2/23/16

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-tukey/6/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-tukey/6/index.html</a>

#### Enhancements

The following changes are improvements to existing features (which includes changed default values):

##### System

- [#15269](https://github.com/h2oai/h2o-3/issues/15269): Handling Sparsity with Missing Values
- [#9621](https://github.com/h2oai/h2o-3/issues/9621): Fix for erroneous conversion of NaNs to zeros during rebalancing
- [#9622](https://github.com/h2oai/h2o-3/issues/9622): Remove bigdata test file (not available)

#### Bug Fixes

The following changes resolve incorrect software behavior:

##### Algorithms
- [#9616](https://github.com/h2oai/h2o-3/issues/9616): CV models during grid search get overwritten

##### R

- [#9587](https://github.com/h2oai/h2o-3/issues/9587): Di/trigamma handle NA
- [#9617](https://github.com/h2oai/h2o-3/issues/9617): Progress bar for grid search with N-fold CV is wrong when max_models is given

### Tukey (3.8.0.1) - 2/10/16

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-tukey/1/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-tukey/1/index.html</a>

#### New Features

These changes represent features that have been added since the previous release:


##### API

- [#14762](https://github.com/h2oai/h2o-3/issues/14762): Ability to conduct a randomized grid search with optional limit of max. number of models or max. runtime
- [#14781](https://github.com/h2oai/h2o-3/issues/14781): Add score_tree_interval to GBM to score every n'th tree
- [#15217](https://github.com/h2oai/h2o-3/issues/15217): Make it easy for clients to sort by model metric of choice
- [#9491](https://github.com/h2oai/h2o-3/issues/9491): Add ability to set a maximum runtime limit on all models
- [#9571](https://github.com/h2oai/h2o-3/issues/9571): Return a grid search summary as a table with desired sort order and metric


##### Algorithms

- [private-#665](https://github.com/h2oai/private-h2o-3/issues/665): Added ability to calculate GLM p-values for non-regularized models
- [#13841](https://github.com/h2oai/h2o-3/issues/13841): Implemented gain/lift computation to allow using predicted data to evaluate the model performance
- [#13143](https://github.com/h2oai/h2o-3/issues/13143): Compute the lift metric for binomial classification models
- [#15123](https://github.com/h2oai/h2o-3/issues/15123): Add absolute loss (Laplace distribution) to GBM and Deep Learning
- [#15308](https://github.com/h2oai/h2o-3/issues/15308): Add observations weights to quantile computation
- [#15374](https://github.com/h2oai/h2o-3/issues/15374): For GBM/DRF, add ability to pick columns to sample from once per tree, instead of at every level
- [#9535](https://github.com/h2oai/h2o-3/issues/9535): Quantile regression for GBM and Deep Learning
- [#9565](https://github.com/h2oai/h2o-3/issues/9565): Add recall and specificity to default ROC metrics

##### Python

- [private-#311](https://github.com/h2oai/private-h2o-3/issues/311): Added support for Python 3.5 and better (in addition to existing support for 2.7 and better)


#### Enhancements

The following changes are improvements to existing features (which includes changed default values):


##### Algorithms

- [#15142](https://github.com/h2oai/h2o-3/issues/15142): Adjust string substitution and global string substitution to do in place updates on a string column.


##### Python

- [#14844](https://github.com/h2oai/h2o-3/issues/14844): Fix layout issues of Python docs.
- [#15241](https://github.com/h2oai/h2o-3/issues/15241): as.numeric for a string column only converts strings to ints rather than reals
- [#15165](https://github.com/h2oai/h2o-3/issues/15165): Table printout in Python doesn't warn the user about truncation
- [#15365](https://github.com/h2oai/h2o-3/issues/15365): Version mismatch message directs user to get a matching download
- [private-#238](https://github.com/h2oai/private-h2o-3/issues/238): Implement secure Python h2o.init
- [#15409](https://github.com/h2oai/h2o-3/issues/15409): Check and print a warning if a proxy environment variable is found


##### R

- [#15241](https://github.com/h2oai/h2o-3/issues/15241): as.numeric for a string column only converts strings to ints rather than reals
- [#15165](https://github.com/h2oai/h2o-3/issues/15165): Table printout in R doesn't warn the user about truncation
- [#15335](https://github.com/h2oai/h2o-3/issues/15335): Improve R's reporting on quantiles
- [#15365](https://github.com/h2oai/h2o-3/issues/15365): Version mismatch message directs user to get a matching download

##### Flow

- [#15313](https://github.com/h2oai/h2o-3/issues/15313): Improve model convergence plots in Flow
- [#9537](https://github.com/h2oai/h2o-3/issues/9537): Flow shows empty logloss box for regression models
- [#9558](https://github.com/h2oai/h2o-3/issues/9558): Flow's histogram doesn't cover the full support

##### System

- [private-#644](https://github.com/h2oai/private-h2o-3/issues/644): exportFile should be a real job and have a progress bar
- [#15364](https://github.com/h2oai/h2o-3/issues/15364): Improve parse chunk size heuristic for better use of cores on small data sets
- [#9547](https://github.com/h2oai/h2o-3/issues/9547): Print all columns to stdout for Hadoop jobs for easier debugging

#### Bug Fixes

The following changes resolve incorrect software behavior:

##### API

- [#9572](https://github.com/h2oai/h2o-3/issues/9572): Ability to extend grid searches with more models

##### Algorithms

- [#14826](https://github.com/h2oai/h2o-3/issues/14826): GLRM with Simplex Fails with Infinite Objective
- [#15047](https://github.com/h2oai/h2o-3/issues/15047): Set GLM to give error when lower bound > upper bound in beta contraints
- [#15101](https://github.com/h2oai/h2o-3/issues/15101): Set GLM to default to a value of rho = 0, if rho is not provided when beta constraints are used
- [#15121](https://github.com/h2oai/h2o-3/issues/15121): Add check for epochs value when using checkpointing in deep learning
- [#15150](https://github.com/h2oai/h2o-3/issues/15150): Set warnings about slowness from wide column counts comes before building a model, not after
- [#15185](https://github.com/h2oai/h2o-3/issues/15185): Fix docstring reporting in iPython
- [#15273](https://github.com/h2oai/h2o-3/issues/15273): Fix display of scoring speed for autoencoder
- [#15332](https://github.com/h2oai/h2o-3/issues/15332): GLM gives different std. dev. and means than expected
- [#9536](https://github.com/h2oai/h2o-3/issues/9536): Bad (perceived) quality of DL models during cross-validation due to internal weights handling
- [#9566](https://github.com/h2oai/h2o-3/issues/9566): GLM with weights gives different answer h2o vs R

##### Python

- [#15225](https://github.com/h2oai/h2o-3/issues/15225): sd not working inside group_by
- [#15309](https://github.com/h2oai/h2o-3/issues/15309): Parser reads file of empty strings as 0 rows
- [#15310](https://github.com/h2oai/h2o-3/issues/15310): Empty strings in Python objects parsed as missing

##### R

- [#15225](https://github.com/h2oai/h2o-3/issues/15225): sd not working inside group_by
- [#15140](https://github.com/h2oai/h2o-3/issues/15140): Fix bug in summary when zero-count categoricals were present.
- [#14713](https://github.com/h2oai/h2o-3/issues/14713): Fix h2o.apply to correctly handle functions (so long as functions contain only H2O supported primitives)

##### System

- [#14831](https://github.com/h2oai/h2o-3/issues/14831): Ability to ignore 0-byte files during parse
- [#15307](https://github.com/h2oai/h2o-3/issues/15307): /Jobs fails if you build a Model and then overwrite it in the DKV with any other type
- [#9544](https://github.com/h2oai/h2o-3/issues/9544): Improve progress bar for grid/hyper-param searches


---


### Tibshirani (3.6.0.9) - 12/7/15

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-tibshirani/9/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-tibshirani/9/index.html</a>

#### New Features

These changes represent features that have been added since the previous release:


##### API

- [#15100](https://github.com/h2oai/h2o-3/issues/15100): H2O now allows selection of the `non_negative` flag in GLM for R and Python


##### Algorithms

- [PUBDEB-1540](https://github.com/h2oai/h2o-3/issues/14512): Added Generalized Low-Rank Model (GLRM) algorithm
- [#13068](https://github.com/h2oai/h2o-3/issues/13068): Added gains/lift computation
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/f4d2f85f3e61cfa03a1c80ea3a973e9d2f3ba20b): Added `remove_colinear_columns` parameter to GLM


##### R

- [#15019](https://github.com/h2oai/h2o-3/issues/15019): R now retrieves column types for a H2O Frame more efficiently

##### Python

- [#15200](https://github.com/h2oai/h2o-3/issues/15200): Added Python equivalent for `h2o.num_iterations`
- [#15142](https://github.com/h2oai/h2o-3/issues/15142): Added `sub` and `gsub` to Python client
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/dbef536dc48fa56eb21d65339f1f1857724012f1): Added weighted quantiles to Python API
- [#14280](https://github.com/h2oai/h2o-3/issues/14280): Added `sapply` operator to Python
- [#14933](https://github.com/h2oai/h2o-3/issues/14933): H2O now plots decision boundaries for classifiers in Python

#### Enhancements

The following changes are improvements to existing features (which includes changed default values):


##### Algorithms

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/315201eb41a8439a1a77fb632b0e32a8181a2785): Change in behavior in GLM beta constraints - when ignoring constant/bad columns, remove them from `beta_constraints` as well
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/8035f7388560801e4af69c92c81a436a96f02957): Added `ignore_const_cols` to all algos
- [#15217](https://github.com/h2oai/h2o-3/issues/15217): Improved ability to sort by model metric of choice in client

##### Python

- [#15315](https://github.com/h2oai/h2o-3/issues/15315): H2O now checks for `H2O_DISABLE_STRICT_VERSION_CHECK` env variable in Python [GitHub commit](https://github.com/h2oai/h2o-3/commit/238a1c80e091c681da8b4b52eca308281244a7b9)
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/953b892560242146af0ffc124f32e7a9ddb89b04): H2O now allows l/r values to be null or an empty string
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/7e368a4df9c39b30ca4f4eac77e2c739ef38bbf6): H2O now accomodates `LOAD_FAST` and `LOAD_GLOBAL` in `bytecode_to_ast`

###### R

- [#14352](https://github.com/h2oai/h2o-3/issues/14352): In R, `h2o.getTimezone()` previously returned a list of one, now it just returns the string


##### System

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/e1c9272bc21d4fcf3ab55f4fe646656a061ce542): Added more tweaks to help various low-memory configurations



#### Bug Fixes

The following changes resolve incorrect software behavior:


##### API

- [#14991](https://github.com/h2oai/h2o-3/issues/14991): `h2o.grid` failed when REST API version was not default
- [#15307](https://github.com/h2oai/h2o-3/issues/15307): `/Jobs` failed if you built a Model and then overwrote it in the DKV with any other type [GitHub commit](https://github.com/h2oai/h2o-3/commit/453db68b4d31130bdaa2b6a31cf80a88447baabf)
- [#15299](https://github.com/h2oai/h2o-3/issues/15299): `/3/Jobs` failed with exception after running `/3/SplitFrame`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/888cc021694fe0b34aadba70bdd0d79811cfb30d): #15332 - Fixed error where sd and mean were adjusted to weights even if no observation weights were passed

##### Algorithms

- [#15303](https://github.com/h2oai/h2o-3/issues/15303):  GLRM validation frames must have the same number of rows as the training frame
- [#15000](https://github.com/h2oai/h2o-3/issues/15000): Fixed assertion failure in Deep Learning
- [#15221](https://github.com/h2oai/h2o-3/issues/15221): Could not compile POJO using K-means
- [#15223](https://github.com/h2oai/h2o-3/issues/15223): Could not compile POJO using PCA
- [#15226](https://github.com/h2oai/h2o-3/issues/15226): Could not compile POJO using Naive Bayes
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/928002f60a4523ad1b065635f55ccd2bb2b9091c): Fixed weighted mean and standard deviation computation in GLM
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/147fd0990b484747439ba0921f72763211d10072): Fixed stopping criteria for lambda search and multinomial in GLM


##### Python

- [#15170](https://github.com/h2oai/h2o-3/issues/15170): H2OFrame indexing was no longer Pythonic on Bleeding Edge 10/23
- [#15185](https://github.com/h2oai/h2o-3/issues/15185): Trying to get help in python client displayed the frame
- [#15278](https://github.com/h2oai/h2o-3/issues/15278): Fixed ASTEQ `str_op` bug [GitHub commit](https://github.com/h2oai/h2o-3/commit/4fc88caa8d86679c4ed584d6a2c32ec931f03cdd)


##### R

- [#14713](https://github.com/h2oai/h2o-3/issues/14713): `h2o.apply` did not correctly handle functions
- [#15241](https://github.com/h2oai/h2o-3/issues/15241): R: as.numeric for a string column only converted strings to ints rather than reals
- [#15225](https://github.com/h2oai/h2o-3/issues/15225): R: `sd` was not working inside `group_by`
- [#15304](https://github.com/h2oai/h2o-3/issues/15304): R: Ignore Constant Columns was not an argument in Algos in R like it is in Flow
- [#15053](https://github.com/h2oai/h2o-3/issues/15053): When a dataset was sliced, the int mapping of enums was returned
- [#15314](https://github.com/h2oai/h2o-3/issues/15314): Improved handling when H2O has already been shutdown in R [GitHub commit](https://github.com/h2oai/h2o-3/commit/00d1505681135960722bc74def2a951ba01682ba)
- [#15140](https://github.com/h2oai/h2o-3/issues/15140): Fixed categorical levels mapping bug

##### System

- [#15309](https://github.com/h2oai/h2o-3/issues/15309): Parser read file of empty strings as 0 rows  [GitHub commit](https://github.com/h2oai/h2o-3/commit/eee101ba16e11bcbc10e35ddd1f96bfb749156d3)
- [#15310](https://github.com/h2oai/h2o-3/issues/15310): Empty strings in python objects were parsed as missing  [GitHub commit](https://github.com/h2oai/h2o-3/commit/fb4817a6847192a9dc3f359b0f3ec2d63268aaef)
- [#15282](https://github.com/h2oai/h2o-3/issues/15282): Save Model (Deeplearning): the filename for the model metrics file is too long for windows to handle
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/76aaf1ce863ea3cdfc5531dc0b551a26ff3ddf48): Fixed streaming load bug for large files
- [#15150](https://github.com/h2oai/h2o-3/issues/15150): Column width slowness warning now prints before model build, not after

---

### Tibshirani (3.6.0.7) - 11/23/15

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-tibshirani/7/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-tibshirani/7/index.html</a>


#### Enhancements

The following changes are improvements to existing features (which includes changed default values):


##### Algorithms

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/47f4b754e8f4178b86d5c4f929a46223b3f7b042): Added Iterations and Epochs to DL job status updates, added Iterations to scoring history
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/aa25d5f71b281): Cleaned up iteration counter to work for checkpointing
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/1038cdf9372b799d): Cleaned up counter iteration logic


#### Bug Fixes

The following changes resolve incorrect software behavior:


##### Algorithms

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/bd784804b5ba09fd21eb0ee67d13925b668496a3): Fixed scoring speed display for autoencoder, was showing 0 because wrong runtime was used (ms since 1970 instead of actual runtime)

---

### Tibshirani (3.6.0.2) - 11/5/15

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-tibshirani/2/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-tibshirani/2/index.html</a>

#### New Features

##### Algorithms

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/93cd9246762cab74008ae188e9dcad552978875a): Added support for grid search
- [#15180](https://github.com/h2oai/h2o-3/issues/15180): Implemented GLRM grid search in R and Python
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/ada571ee5bfc7d8e140ab3fd3d416a8246909201): #15196: Enabled early convergence-based stopping by default for Deep Learning
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/514aa26de192821f7f5c780caed561f2689761ab): Added L1+LBFGS solver for multinomial GLM

##### Python

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/33fdf12410bbe0fc294a848a6b37719fa05e6f9a): #15196: Added Python API for convergence-based stopping


##### R

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/1f9dbffbea6f648717889064a9b612ee0fbbd3ab): Added `.Last` to `Delete InitID`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/723d552f7f1a9977c6fc837704b73cf1b85f0524): #15196: Enabled convergence-based early stopping for R API of Deep Learning

#### Enhancements

##### Algorithms

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/6e0c3575c2d5f07fcc9dacf51f76ec83279d1783): Enable grid search for Deep Learning parameters `overwrite_with_best_model`, `momentum_ramp`, `elastic_averaging`, `elastic_averaging_moving_rate`, & `elastic_averaging_regularization`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/e69a8a5da0a738b1fa46fac5614171269e042f5a): #15196: Stopping tolerance and stopping metric are no longer hidden if `stopping_rounds` is 0
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/514adb7bd456bc2d0ed08eb62db450de3524b935): Added checks to verify the mean, median, nrow, var, and sd are calculated correctly in `groupby`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/c2095f7ae5fafcb7e1bcc88c314005886725aba8): `mean` and `sd` now return lists



##### Python

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/3f0d0cb230aa047295524043dae46cc9cb19c7c5): [#15165] H2O now gives users [row x col] of Frame in `__str__`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/cde0510be3adc418b3595989761f3f5cdf513886): `sd`/`var` is now sampled for `group_by`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/30743335bf914af11d787bf9ec1961ac51ebde14): Parameter checking is now split between float and strings/unicode
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/ba630573dbf4cb571302710698664f4fda51f0b6): H2O now only wipes `src._ex` if `src_in_self`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/ebc53e6860028be1bcf220c9898863da0f915943): Refactored default arg handling in `astfun`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/e9950c6ea7abaac46c9670ba02016aef529a14ec): Added new parameters to estimators
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/fbfac8c6ed86cafe4b5f0b78f83e9c9c4e4221f4): Added session start/end; Python now ends the session on exit
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/c73b988ebf30168ddb6c76b6ed51a3c011b6504a): `src` and `self` types are now checked for `None`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/94a90ea8b7b859f2909a0eadf37b5949e722f2ea): H2O now passes caches through all prefix ops
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/edd0c7feffb32249a1ba0e596685483e81bfd34a): H2O now pushes cached types, names, and ncols forward if possible

##### R

- [#14915](https://github.com/h2oai/h2o-3/issues/14915): Removed the R backward compatibility shim
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/c7652e8c93384b1be38fb88da18e65f954b6373c): Added [rows x cols] to `print.Frame` in R
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/a82297bd1f6c0e08722ac6671797fa0e41db77a8): `sd` can now alias `sdev` in `group_by`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/5c9bd93ec15f65658809def2634b13bd788ee7a1): Changed `.eval.driver` to `.fetch.data` in `h2o.getFrame`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/e9608bfc696e1a64d9becf865571fe11da21ac93): Removed debug printing of `==Finalizer on` in R
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/d6bc387a3d0d5f830f053a8384062eccb8b96d74): Added metalearning function


##### System

- [private-#280](https://github.com/h2oai/private-h2o-3/issues/280): Added EasyPOJO comments and improvements
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/ba75b35fbc7498b3d0d3c0b1d362a44dba6f31bb): [#15115] Enabled `Vec#toCategoricalVec` to convert string columns to categorical columns
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/613e121e9ab13f32df4beffce1c2139607b5f64d): `apply` now works in


#### Bug Fixes


##### Algorithms

- [#15223](https://github.com/h2oai/h2o-3/issues/15223): PCA: Could not compile POJO
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/545c00f9913b0fc586490ff391759f106228c121): [#15223] Incorrect PCA code was generated


##### Python

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/fc7e364c6c4cab889cbb5c8cab223ab092a301c5): #15203: Python was not updating exception on job update
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/b5f81871265d5c3cab4788313a719460dff32dbc): Added missing arguments to DRF/GBM/DL in scikit-learn-like API
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/fbe8275e1aec2ca930373fc18d65b11e706b88b5): Fixed `impute` in Python
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/ce343692ce2382e789959e717bf7a3996b5d9fda): Restored `ASTRename`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/f55dc318d74352333c7f2ec4f54fffad3bb49529): Fixed reference to `_quoted` in H2O module

##### R

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/cc34b88ff322ec829f3d52dca0d03b2c658dd6d6): [#15207, #15207] Hidden grid parameter was passed incorrectly from R
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/1ac19955ca1b0ce72e794542d58b4625affae525): H2O now uses deep copy when using `assign` from one global to another
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/a0300b0b519d06707598a31cf8aaf168835f869f): Fixed `getFrame` and directory `unlink`


##### System

- [#14783](https://github.com/h2oai/h2o-3/issues/14783): `h2o.init()` failed to launch on the Docker image
- [#14992](https://github.com/h2oai/h2o-3/issues/14992):  Deep Learning generated an assertion error
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/c3544016927d93695bed20969d8588f159b5797d): Fixed rm handling of non-frames
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/2285a7c89effd9c9be8d45b5899c7463c335006f): Fixed `log_level`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/f0f3559bc00e9f1ef264138db75b3beade32d066): Fixed eq2 slot assign
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/150a29fce82f1e0c1450b0c653342fbdfabf326f): Fixed a bug found during benchmarking for small data
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/3097a0754b6f49a7358a5ea3a8150606f36d5172): #15201: User-given weights were accidentally passed to N-fold CV models
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/91d141f5dd574293ecce6336365ec56fb8007a2d): Fixed NPE in Grid Schema
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/41bf4a3c164734d1dd6d6ca010b1a3eac313cfdc): #15196: Convergence checks are now numerically stable

---

### Slotnick (3.4.0.1)

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-slotnick/1/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-slotnick/1/index.html</a>

#### New Features

##### API

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/8a2aefb72ab9c64c7064a65c242239a69cbf87a3): Added `NumList` and `StrList`
- [#13666](https://github.com/h2oai/h2o-3/issues/13666): Added REST API and R / Python for grid search

##### Algorithms

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/c18cfab41bd32fd4c2f34fdda5cf73076c1320f6): Added option in PCA to use randomized subspace iteration method for calculation
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/acccdf7e0a698b2960aac8260c8284c6523d1fd5): Deep Learning: Added `target_ratio_comm_to_comp` to R and Python client APIs
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/a17dcc0637a04fc7e63c020bd0a3f2bba7b6f674): #14225: Added stochastic GBM parameters (`sample_rate` and `col_sample_rate`) to R/Py APIs
- [#14421](https://github.com/h2oai/h2o-3/issues/14421): GLRM has been tested and removed from "experimental" status

##### Hadoop

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/ba2755313d22f3812742786269ababc72257a179): Added support for H2O with HDP2.3

##### Python

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/cc02cc6f19360a79232a781328c6afae80a4861a): Added `_to_string` method
- [#15079](https://github.com/h2oai/h2o-3/issues/15079): Added Python grid client [GitHub commit](https://github.com/h2oai/h2o-3/commit/16589b7d3362dce6a2caaed6e23287c605896a8a)
- [#15036](https://github.com/h2oai/h2o-3/issues/15036): Scoring history in Python is now visualized ([GitHub commit](https://github.com/h2oai/h2o-3/commit/77b27109c84c4739f9f1b7a3078f8992beefc813))
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/3cda6e1a810dcbee5182dde5821a65f3b8800a69): [#14972](https://github.com/h2oai/h2o-3/issues/14972): Python implementation and test for `split_frame()`


##### R

>This software release introduces changes to the R API that may cause previously written R scripts to be inoperable. For more information, refer to the following [link](https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/product/upgrade/RChanges.md).

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/a989234a0ec9d6ded30441a2c6d2672ef5731379): Added `h2o.getTypes()` to the R wrapper
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/c630e40f5ba577e912aaf44d3c7f7fb10f1693dd): Added ability to set `col.types` with a named list
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/875418caebf8f12aca1675f124c2d5135670642a): Added `h2o.getId()` to get the back-end distributed key/value store ID from a Frame
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/9420547451c5ef6dcb04b8803d0a400c720445a4): Added column types to H2O frame in R, which allows R to set the correct column types when `as.data.frame()` is used on an H2O frame
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/9e08405307b6499dbf09d264ab2ee8798b496a5d): Added `@export` for exported R functions

##### System

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/de0b19c71a18f09eeace304773adebb51772e311): Added string length util for Enum columns
- [[GitHub commit](https://github.com/h2oai/h2o-3/commit/7b8e39e8a6624d2512620d9e230ff91dd9c7e240): Added pass-through version of `toCategoricalVec()`, `toNumericVec()`, and `toStringVec()` to `Vec.java` for code simplicity and backwards compatibility
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/20ccac7947232fbb68e318e013c0ac2a96870284): Added string column handling to `StrSplit()`

##### Web UI

- [#14941](https://github.com/h2oai/h2o-3/issues/14941): Added grid search to Flow web UI



#### Enhancements


##### Algorithms

- [#13459](https://github.com/h2oai/h2o-3/issues/13459): Show Frames for DL weights/biases in Flow
- [#14806](https://github.com/h2oai/h2o-3/issues/14806): DRF/GBM: `nbins_top_level` is now configurable
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/f9b1fea92c46105d0a2a54874eb7898993e6f718): Deep Learning: Scoring time is now shown in the logs
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/ad041d3b5ff96ed33ea22692035f02c21b461a68): Sped up GBM split finding by dynamically switching between single and multi-threaded based on workload
- [#14225](https://github.com/h2oai/h2o-3/issues/14225): Implemented Stochastic GBM
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/5ada6c5f654e75c2275e1fc5027a306c44793ea3): Parallelized split finding for GBM/DRF (useful for large numbers of columns and nbins).
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/66230ffcf7276c83aa8db52cb9656efba06ec45a): Added improvements to speed up DRF (up to 35% faster) and stochastic GBM (up to 5x faster)
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/f48b52cf0a8f74a57c60a9cafd979ff28cd4a4c0): Added some straight-forward optimizations for GBM histogram building
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/5ccc4699f3c71dccb64f7c11fac5a91ddff514ba): GLRM is now deterministic between one vs. many chunks
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/ae79aec8a84d0b7bdabb60f15e8138218e5e227e): Input parameters are now immutable
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/c6ad99d06a337f7535720852213e4d55fa116e8a): #15054: Cleaned up N-fold CV model parameter sanity checking and error message propagation; now checks all N-fold model parameters upfront and lets the main model carry the message to the user
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/756ed15a8a8e34e4383cba1a6580c24806603c49): [#15050](https://github.com/h2oai/h2o-3/issues/15050): N-fold CV models are no longer deleted when the main model is deleted
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/c7339d0597f690aef491b343797368c27645bb64): #15044: The title in `plot.H2OBinomialMetrics` is now editable
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/61d125c4b1a457fc95c5daf2a2423b3934a1d6eb): Parse Python lambda (bytecode -> ast -> rapids)
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/2534dc6d4bb2c534cd0122e317317ad0459e4d3e): #14806: Cleaned up/refactored GBM/DRF
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/ee3d12d04e2f23569e456436f880fb2e28223e62): Updated MeanSquare to Quadratic for DL
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/fe94591e6ef84e1f2b051d18beece2b10006de7a): #15052: Speed up Enum mapping between train/test from O(N^2) to O(N*log(N))
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/60e8de2600d28fd8d7475ea9ae8b114913510ef9): Added GLRM scoring history with step size and average change in objective function value
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/f7369945a480a3c29677ce7baa97c56166c4e0f2): SVD now outputs the V matrix as a frame with a frame key, rather than a double array in the API
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/fc4aaaceb9e5c5b42f0c269b316b4d3f6f827a18): Modified k-means++ initialization in GLRM to set X to inverse of cluster distance with sum normalized to one, for each observation in training data
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/aad59cf147c887c4f709406393e8a242a49bc531): Increased GBM worker thread priority to avoid deadlock with high parallel GBM job counts
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/e2be556f7ca9a10fae88747259f83f839e80d99b): Added input parameter `svd_method` to GLRM

##### Python

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/2822e05775a157b6a64d31ab3cc5ae3bbccc4322): `centers_std` is now returned as a list of columns
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/48590f6e6affc3e12246cd78bdf82b9806d79f52): `str(Frame)` no longer returns an ID; updated ExprNode `_to_string` to accomodate
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/e385e78271fb634c4e43e1d3c694ee6dfe955bff): Changed default setting for `_isAllAscii` to false
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/215348e1e743e1aa0fbddb7be937f58144d6b0e9): Fixed var to return scalar/frame based on `nrow`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/aadf7e1558bf673fc44bd345b9e1a592dc7242d6): Python now checks `ncol`, not `nrow`
- [#14040](https://github.com/h2oai/h2o-3/issues/14040): Python's `h2o.import_frame()` now matches R's `importFile()` parameters where applicable
- [#14924](https://github.com/h2oai/h2o-3/issues/14924): Python now uses the streaming endpoint `/3/DownloadDataset.bin`
- [#15132](https://github.com/h2oai/h2o-3/issues/15132): Added normalization and standardization coefficients to the model output in Python
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/7f556a8117cdcbd556421cb11076d8db9fa79a1f): Renamed `logging` to `h2o_logging` to avoid conflict with original logging package
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/a902a8e16982d0e015436272272d6c2a2b551ea9): H2O now recognizes additional parameters (such as column names) for Python objects
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/6877f2c69f42abc779df4bb98fc8b5d000a0bd88): `head` and `tail` no longer download the entire dataset
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/1fd6ae0a988ca421dedf6338a895b53f9220d030): Truncated DF in `head` and `tail` before calling `/DownloadDataset`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/54279fbf168abc8dc45b080d47eebc6ea56e616d): `head()` and `tail()` now default to pretty printing in Python
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/56f1c364897c25b082f800fce9549160661fed03): Moved setup functionality from parse to parse setup; `col_types` and `na_strings` can now be dictionaries
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/adf612fd1f5c764a05231d6b8023c83ba9ffe0f5): Updated `H2OColSelect` to supply extra argument
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/9e5595ea79b9d4f3eb81dffc457c97180e6f078a): #15086: Relative tolerance is now used for floating point comparison
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/d930dd90c2d77a903cb79e6f107f6cbe6823b94f): Added more cloud health output to `run.py`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/16dce03a13af46fae2fa912e79cd5fb073ca8477): When Pandas frames are returned, they are now wrapped to display nicely in iPython



##### R

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/38fc561b542d0d17caf18eeee142034c935393a9): Added null check
- [#15096](https://github.com/h2oai/h2o-3/issues/15096): When appending a vec to an existing data frame, H2O now creates a new data frame while still keeping the original frame in memory
- [#14923](https://github.com/h2oai/h2o-3/issues/14923): R now uses the streaming endpoint `/3/DownloadDataset.bin`
- [#14972](https://github.com/h2oai/h2o-3/issues/14972): `h2o.splitFrame()` in R/Python now uses the `runif` technique instead of the horizontal slice technique
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/fc8c337bad6178783286a262d0a18a246811e6fc): Changed `T`/`F` to `TRUE`/`FALSE`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/0a2f64f526b35456614188806e38ed2c54ed8b5c): `xml2` package is now required for `rversions` package
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/d3956c45a6de6e845ed9791f295195778902116e): Package dependencies are taken into account when installing R packages
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/4c18d351207f2441e80a74e55df205edcaacbfcd): Metrics are now always computer if a dataset is provided (R `h2o.performance` call)
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/ff1f925b27a951608d5bbd66ee9487772e529b38): Column names are now fetched from H2O
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/42c2bb48b3534ebb43a992b37ed3c683050e4aab): #15066: Time columns in H2O are now imported as Date columns in R
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/9420547451c5ef6dcb04b8803d0a400c720445a4): `h2o.ls()` now returns `data.frame`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/69bd25b93763f7326d93447986a597cd283b4217): `h2o.ls()` now returns the whole frame
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/a9326cd564b522dcb20aed91663f4390c8c218ef): Removed unnamed additional parameters (ellipses) in R algos
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/c271c2e193c9e7581ee999db48fa9798997a66ee): Added `as.character`to Rapids implementation
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/e6df880496a67449693a25ab739682319ca2e6ab): Updated `plot.H2OModel` in R
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/808a64152e04ad9cc5a351001844a0a1fdfc907f): Updated scoring history plot in R for `training_frame` only
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/58c30eb4c94c06a78cd6a04a52cb84ebd97c1533): Instead of `:` and `assign`, `attr` is now used
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/f3275a89227884e23cfabc535073dc08b8e7634d): Raw strings are now used as accessors
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/1b319d837e0f18474419efe981e559a51606febb): `name.Frame` and `dimnames.Frame` are now visible


##### System

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/cb9fe3fd132ce7851339b99420d1c25a0129160c): Added vertical prefetch of all chunks' worth of data for dense rows
- [#14399](https://github.com/h2oai/h2o-3/issues/14399): Scoring is now a non-blocking job with a progress bar
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/c84c663c33963195f960f12819afa7624370764e): EasyPojo API is now serializable
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/70d91bed44172257d6d804572c985ec1ec67201e): Changed parse setup guess when encountering large NA counts to not favor numeric over dates or UUIDs
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/e0ed51ce8a7cc52ef522a15915f7444635ee2b5d): Refactored vector type conversion methods into a class called VecUtils
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/836c32ebd5a00b0c255ac3bdb418af2b5d4da81a): Cleaned up ASTStrList to handle frames with more than one vector during column conversion; checks types before converting; added several new column type conversions
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/678fa6af2b20105e17cacbe9c38bec4266b89246): If the job is cancelled, scoring is now canceled
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/ad2548754ad909edb0f4b01c61c09708d8ba3ee6): Refactored `doAll_numericResult() -> doAll(nout, type, frame)` where all output vecs are of the given type
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/5206760f20fa2394d16e30e4c025c6a3f4a62c44): Improved hash function
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/55874fa3e7c9ffbbdfb1cbb26d132304bf81deed): The output of `_train.get()` is now passed to a Frame
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/805f7a6cb58ac81720f65c0e895fab4433bb2972): Refactored binary/col ops for aesthetics and maintainability
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/7f1912e2e590e0011ac19ccd4910ce0216f0f8a2): Added correct types for new Vecs; `CategoricalWrappedVec` now exports a utility for enum conversions instead of a constructor
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/f4fdf57c5a15ee36fc8ce8a0c3102a6a9500bd9f): Mean/sigma values are now printed to the logs after parsing
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/4885b259b80e2001104de670c7fce1bcee149a17): #15086: Added some optimizations for some chunks (mostly integers) in RollupStats
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/bcec3daaf504c392555a9d8b5f171cfa396be981): #15086: Added instantiations of Rollups for dense numeric chunks
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/5c30f2375b40f5443ed71ed75e56e654c8cdddf4): #15086: Implemented single-pass variance/stddev calculation for rollups
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/70d547db554b48a9f1a4915b556746fbe2ae0854): #15086: Added `hasNA()` for chunks
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/655d36ddfb194606d251149021bddbd93ffa35d6): Reordered args in sub/gsub (`astid` > `astparameter`, `add string` -> `numeric`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/f73199ab30c04c40aafa855b90cc1de2cce892d4): Ensured all chunks get closed
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/9ae1d15a567cd92dfcef39adeddef8b10a7f3ced): `NewChunk.addString()` now accepts a Java string or BufferedString, eliminating needless conversion to a BufferedString before inserting into the NewChunk buffer. Improves efficiency of several ASTStrOps as well as converting Categorical columns to String columns.
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/68f7e5122364b938b560e68e0aac573c3ed198bd): Renamed enums to categoricals system-wide
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/cd456388d1815cf08292459d44013d5e20436b49): Renamed `ValueString` -> `BufferedString`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/5efb27dc1f50d5cad14fe86166060452bf16582f): Removed redundant frame creation; added Java comments to each string utility; changed RAPIDS name of `gsub` -> `replaceall` and `sub` -> `replacefirst`; added nchar utility to the R client; updated comments in Python and R client
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/06cf75e3786a14fb7cf846c9e34744b1cfead194): All NA chunks are now handled in string ops
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/2e87a2634fc48daf6996f9befb3c3d95e9d467cf): Added ability for string utils to handle NA chunks
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/7eba6217d3fe6f04a605b273a866d4e084a46208): Added the ability to handle duplicate rows to merge
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/9e0c9fb38d885c54d811091448d134d09e08aaf3): `countMatches` utilities now only work on string columns
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/e5cc404eec3e4a9e6ee9edf9e985cf487d4f91bd): Changed names of `SubStr` and `GSubStr` to `ReplaceFirst` and `ReplaceAll`; both methods now only accept string columns as input
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/b71043473c652d0f457bdd00f4d1192090cbf210): Changed `toUpper` and `toLower` to only work on string columns; includes an optimzied version of each method as well as a UTF-safe version
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/ab44f73178cfb711da8f5b65ee1b5c67e27213d4): CStrChunks now track whether they are pure ASCII to allow StringUtilities to use optimized versions of the utilities that operate directly on the string buffer
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/bd7ba53f1a360a3b35b5b1e7b93306927a907ff1): Moved frame function to ArrayUtils
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/1afa2701f352f03140a64d4a001e21fef2ead7a8): Removed categorical versions of `trim()` and `length()`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/c92390122601d3abb2ff42b2fab85a9d49595025): Changed the merge defaults to match the implementation
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/5291941a8d132b60f3a22e04c0d21b6c4bd7d7d9): Merge no longer uses a `by` argument
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/b318faf497c8989d900fa7f26a0c75a7ffe270cc): Added `trim` and `length` functionality for string columns
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/26421b79453623c961cc4f73ff48fd1cca95ceaa): private-#293: Improved POJO handling
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/146b656c3e94fd025cee0988abd8d0948e7ae94d): Config files are now transferred using a hexstring to avoid issues with Hadoop XML parsing
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/6c8ddf330ae739117ab4302eb83f682f342e2a5e): private-#291: Added `isNA` check
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/b340b63b221a765e9d2fb4b82283da92d997e3d7): Means, mults, modes, and size now do bulk rollups
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/823209767da4c162ad99047195d702769e7d37a8): Increased priority of model builder Driver classes to prevent deadlock when bulk-launching parallel unrelated model builds
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/207dee8a52d3fa7936eba9440ec8aed182c34e55): Renamed Currents to Rapids
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/b5e21d2f40a544303ef4acbe3f64f20d47d9d864): CRAN-based R clients are now set to opt-out by default
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/76d7a3307f7636660ca226ec65ce90fd7153257d): Assembly states are now saved in the DKV

##### Web UI

- [#14925](https://github.com/h2oai/h2o-3/issues/14925): Flow now uses the streaming endpoit `/3/DownloadDataset.bin`


#### Bug Fixes


##### Algorithms

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/798c098f42ad412e2331936238642dc7578450c8): Fixed bug with `CategoricalWrappedVec`
- [#14629](https://github.com/h2oai/h2o-3/issues/14629): Corrected math for GBM Tweedie with offsets/weights
- [#14630](https://github.com/h2oai/h2o-3/issues/14630): Corrected math for GBM Poisson with offsets/weights
- [#15050](https://github.com/h2oai/h2o-3/issues/15050): Deleting Deep Learning n-fold models resulted in a `java.lang.AssertionError`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/e14e50d85922913ff5c6f0cb5a7c0806787d7be8): Fixed GLM with nfolds
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/463ac6aee7d656a61b45b29d517054e39300c126): Updated GLM InitTsk to run at +1 priority level to avoid deadlock when launching hundreds of GLMs in parallel
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/129c9fd062d1ba2b3a20c9d6ebe54d1a6d94b730): Column names (feature names) are now named correctly for the exported weight matrix connecting the input to the first hidden layer
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/fa14eb4b07c320fb627476928a795693b0f4f6b9): Changed `isEnum` to `isCategorical`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/d8c287caa43aa005648c4f9b3c88aca7a09710d7): Cleaned up DRF and GBM; fixed checkpoint restart logic for trees and changed which parameters are configurable
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/b3cbbf316e9b34e4da838fae902d1754d0bcd96b): Fixed incorrect logistic and hinge loss functions and apply to binary numeric columns in {0,1} only
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/dd67ef00b135c6d50f5fba28c3fde477f031a1eb): Fixed a bug where Poisson loss function was calculated incorrectly for values of 0
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/c244867c3e4eb7813070c804882a88ab47c90f43): Fixed DL POJO for large input columns

##### Python

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/99f59e1161da82496aa3008592e1ff5d8826097e): `nrow` was not filling cache correctly
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/e65bb079988ad7a19ee2fc0c17f93071f2ec4795): Fixed typo in Python object upload (`header` -> `col_header`)
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/f62055393b3273a67e9a5c02281d30e0dfca3392): Append now does so in place
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/6ceab711563ec6b307656b0e2fad1a8bb7696fcf): Seed was not being set
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/e0073fc3c03e779974896d259ff655e0b5cab8c9): Fixed `group_by`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/09779bb751c032c007f31b2408bfe7196a30c46f): Corrected `.fromPython`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/4b078544fa49dc44ff59a920108f8d882d52bb3a): Corrected Python dict col names
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/85cf09508d1737ab9b3ad8381216e339b769b283): Fixed null/npe in H2O's fit for sklearn (Windows only)
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/ceb69702040279ff95e00dd4d5d179a30ddfcd67): `get_params` now keeps "algo" out of params
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/74969ec78970de85c8780380d6ae90d911bedcaa): Improved compatibility with sklearn by using "train" as a model build verb and reserving "fit" for sklearn; if "fit" method is attempted, a warning displays
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/05d2179e751b530561f2c36c3c65c98a28d73d21): Fixed accessor in Python model predict

##### R

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/7f5424abef8d8acb4c7160134643ca4122e7ff00): Fixed `is.numeric`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/56eef73e2f019b9d6cd49239d377d4899d1eb02c): Fixed `h2o.anyFactor` and `h2o.impute`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/05e130297684b584a62d2a8a0b16fab04a1af4f0): Fixed levels
- [#14768](https://github.com/h2oai/h2o-3/issues/14768): `h2o.splitFrame` was not splitting randomly in R
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/903779fd8526f730091f70f52c67750554ed88fc): Fixed range in R
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/f3a2a3f171910ef9121a8cfae86fc6932eb6a978): #14972: Fixed variable name for case where `destination_frame` is provided.
- [#15109](https://github.com/h2oai/h2o-3/issues/15109): `h2o.table` ran slower than `h2o.groupby` by magnitudes
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/9cf2639f71e26d48aa2fdc1725ee61889a619239): Fixed location of datafile for for R example code
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/bfe6ae201eee39b0ad32867a6322eaf6e23d4cb6): Fixed `length(column.names)==number_columns` check
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/573b25e3aaa2e7ea79d0713a93c4687d76fb0991): Parse types  can be specified by column index or column name, but not both
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/0cb8b8dd69de20619318f1dd1bf9ff0c7a7f5481): Added connection (close HTTP header) to improve jetty connection pool behavior
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/3b9cc9b42c781d07e0cf9b455008690502b9a495): Added a sensible min on N
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/60ff4973c68eff876b63c286e04b0b2f3c166825): Added Windows binaries to R package repo
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/ac3a755e8c4161bb6e696815a5f04774f3969e1e): Fixed `h2o.weights` to show frame as output
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/20827d5161b675628c543200304530c26860bfea): Fixed type conversion for time columns when ingested by `as.data.frame()`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/4c1e9e0098ca94b09e1f2fcb1cc96610c27daa30): Fixed `h2o.merge` R interface
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/cb8e2b5667021e452bdb69fe630c53270ad2062b): `head` and `tail` now always return `data.frame`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/e703181d0b545dc0be152bcac1d9e32f68f03f1a): Fixed a bug in GLRM init in R
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/613bd6ab537f09e24cb287addd0989178ee14134): Fixed bug in `h2o.summary` (constant categorical columns)
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/42f4635f6c5b17937b1eb58a46c505507979b89c): Fixed bug in `plot.H2OModel`
- [#14938](https://github.com/h2oai/h2o-3/issues/14938): When imputing columns from R, many temp files were created, which did not occur in Flow


##### System

- [#15159](https://github.com/h2oai/h2o-3/issues/15159): During parsing, SVMLight-formatted files failed with an NPE [GitHub commit](https://github.com/h2oai/h2o-3/commit/d7c8d431a1bc08a64dc6e6233717dc7423ade58d)
- [#15124](https://github.com/h2oai/h2o-3/issues/15124): During parsing, alphanumeric data in a column was converted to missing values and the column was assigned a type of `int`
- [#14948](https://github.com/h2oai/h2o-3/issues/14948): Spaces are now permitted in the Flow directory name
- [#14018](https://github.com/h2oai/h2o-3/issues/14018): Space in the user name was preventing H2O from starting
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/44994ee4998543ccf13c38e44017adee307db4da): Fixed `VecUtils.copyOver()` to accept a column type for the resulting copy
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/a1f06c4ed21cbec4ac1c5f250f7cf5470758484a): Fixed `Vec.preWriting` so that it does not use an anonymous inner task which causes the entire Vec header to be passed
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/bcec96c0fc088c5be2a923654a9581055f2ad969): Fixed parse to mark categorical references in ParseWriter as transient (enums must be node-shared during the entire multiple parse task)
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/c5d5f166fce56fddd808fa6b1267b9c13d83063f): #15093: Fixed DL checkpoint restart with given validation set after R (currents) behavior changed; now the validation set key no longer necessarily matches the file name
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/0f690db79d2df914d6ad2de2ca2feac6dc2ba48c): Fixed makeCon memory leak when `redistribute=T`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/b5dcd34febf1c289e1a86276b06122e6ddcbd3ec): #15086: Fixed sigma calculation for sparse chunks
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/6c9fc7dac6c150b27989c5f4044ebd1df7c6e83e): Restored pre-existing string manipulation utilities for categorical columns
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/a8b7cf2d80458116c8c2d5e8491285f197706859): Fixed syncRPackages task so it doesn't run during the normal build process
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/ea3a86251e3ad99a1d1a04b60ead0f21925f7674): Fixed intermittent failures caused by different default timezone settings on different machines; sets needed timezone before starting test
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/fa3cad682e33aed6b4b8c7d7982bd13f600eb08f): Fixed error message for `countmatches`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/02b09902d096a082296d76f097cbccfe3ac72dd5): #14414: Fixed size computation in merge
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/b89399d88d1b10a67b9411897ed6dfbc68cb76bf): Fixed `h2o.tabulate()` to work in multi-node mode
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/8b1c75a85eadbc32aa9c2b4d4545252e468f79fc): Fixed integer overflow in printout of CM to TwoDimTable

---

### Slater (3.2.0.7) - 10/09/15

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-slater/7/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-slater/7/index.html</a>


#### Bug Fixes

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/bc6f15ab71f5d41553bbe566bcc0585ef2a2bdf1): Fix Java 6 compatibility

  The Java 7 API call
  `_rawChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);`
  has been replaced by the Java 6 API call
  `_rawChannel.socket().setTcpNoDelay(true);`

  The Java 7 API call
  `sock.getRemoteAddress())`
  has been replaced by
  `sock.socket().getRemoteSocketAddress()`

---

### Slater (3.2.0.5) - 09/24/15

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-slater/5/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-slater/5/index.html</a>

#### Enhancements

##### Algorithms

* [#15052](https://github.com/h2oai/h2o-3/issues/15052): Enum test/train mapping is faster [(GitHub commit)](https://github.com/h2oai/h2o-3/commit/fe94591e6ef84e1f2b051d18beece2b10006de7a)

- [#14980](https://github.com/h2oai/h2o-3/issues/14980): Improved POJO support to DRF

---

### Slater (3.2.0.3) - 09/21/15

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-slater/3/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-slater/3/index.html</a>

#### New Features

##### R

- [#15018](https://github.com/h2oai/h2o-3/issues/15018): H2O now returns per-feature reconstruction error for `h2o.anomaly()` [(GitHub commit)](https://github.com/h2oai/h2o-3/commit/e818860af87cad796699e27f8dfb4ff6fc9354e8)

#### Enhancements


##### Algorithms

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/1def2121ac811eebd9ea0e4ed9fa9f4d296a10ad): Added back support for sparse activations in DL; currently changes results as numerical values are de-scaled only, no standardized

##### Python

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/f37ef949bd428d362b20f424f9df02761c33a419): Adjusted `import_file` in Python to accept the same parameters as `import_file` in R


##### R

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/b5e21d2f40a544303ef4acbe3f64f20d47d9d864): H2O now sets CRAN-based R clients to permanent opt-out.
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/0f2c3d67e4ab40d3fc2a5874acc1efce0bbe6bc4): Modified output of h2o.tabulate in R
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/e6cfe9d6408539645996486f10b579f9240e5b90): Added default plotting for models in R
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/a9ac5058c4f07e106d6b6b16002839cab8f9b2ef): Pre-pended graphics pkg to `plot.H2OModel` methods


#### Bug Fixes

##### Algorithms

- [#15030](https://github.com/h2oai/h2o-3/issues/15030): All algos: when offset is the same as the response, all train errors should be zero [(GitHub commit)](https://github.com/h2oai/h2o-3/commit/7515360a4c4181f639a18f70436f59969d4a0a46)
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/c244867c3e4eb7813070c804882a88ab47c90f43): Fixed DL POJO for large input columns

##### R

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/43d22d18d284bf26b8553a31c02daf1ea3bb92d6): Fixed bugs in model
  plotting in R
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/ed4afe55e64ae681cf37e71179cbfa4a9c0f88c9): Fixed bugs in R plot.H2OModel for DL
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/42f4635f6c5b17937b1eb58a46c505507979b89c): Fixed bug in plot.H2OModel

##### System


- [#14809](https://github.com/h2oai/h2o-3/issues/14809): Parse not setting NA strings properly [(GitHub commit)](https://github.com/h2oai/h2o-3/commit/6196b23ef68c364559fe304dbe342780fe8afbeb)
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/6d269ee2b59c71df178cae120c232f1551854700): H2O now escapes XML entities
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/afe4ff2f0dea41595a44eefa40fa256b353547f8): Fixed Java 6 build -replaced AutoCloseable with Closeable
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/7548c71de44e1e2adf4e165e0ec41105d0ac607b): Restored code that was needed for detecting NA strings

---

### Slater (3.2.0.1) - 09/12/15
Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-slater/1/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-slater/1/index.html</a>


#### New Features

##### Algorithms

- [GitHub](https://github.com/h2oai/h2o-3/commit/b33156815dc96167e2bf6f466e694e40ad813fcf): #14849: Added loss function calculation for DL.
- [GitHub](https://github.com/h2oai/h2o-3/commit/da8f65d3d8364b883937640e49a25785b9498d39): Set more parameters for GLM to be gridable.
- [GitHub](https://github.com/h2oai/h2o-3/commit/5cfaacaf8f2cfc82024e371554f94326d4f4bce4): [KMeans] Enable grid search with max_iterations parameter.
- [GitHub](https://github.com/h2oai/h2o-3/commit/c02b124b1daf3a0db504201fdc555e5f28a5a3e3): Add kfold column builders
- [GitHub](https://github.com/h2oai/h2o-3/commit/4ae83b726bc9e9128b8b0df81842d1c3c5df7b3c): Add stratified kfold method


##### Python

- [#13676](https://github.com/h2oai/h2o-3/issues/13676): Add nfolds to R/Python
- [GitHub](https://github.com/h2oai/h2o-3/commit/5b273a297d7b5b4b7e6131ed6c31cbb3d3d22638): Improved group-by functionality
- [GitHub](https://github.com/h2oai/h2o-3/commit/236c5af71093549108fa942847820a721da4880a): Added python example for downloading glm pojo.
- [GitHub](https://github.com/h2oai/h2o-3/commit/74c00f24777bd07bde05c2751204cccd7892ebcb): Added countmatches to Python along with a test.
- [GitHub](https://github.com/h2oai/h2o-3/commit/e94892ffab027282e4d96ffea89972f041367a77): Added support for getting false positive rates and true positive rates for all thresholds from binomial models; makes it easier to calculate custom metrics from ROC data (like weighted ROC)



##### R

- [#14751](https://github.com/h2oai/h2o-3/issues/14751): Added a factor function that will allow the user to set the levels for a enum column [GitHub](https://github.com/h2oai/h2o-3/commit/7999075a7cdcdc880d76a3be7e39edeb63d32fc8)
- [#14840](https://github.com/h2oai/h2o-3/issues/14840): Fixed bug in h2o.group_by for enumerator columns
- [GitHub](https://github.com/h2oai/h2o-3/commit/af75976a238d22dd37048eb8d7c100c994bdac08): Refactor SVD method name and add `svd_method` option to R package to set preferred calculation method
- [#15011](https://github.com/h2oai/h2o-3/issues/15011): Accept columns of type integer64 from R through as.h2o()

##### Sparkling Water

- [#13292](https://github.com/h2oai/h2o-3/issues/13292): Support Windows OS in Sparkling Water

##### System

- [private-#537](https://github.com/h2oai/private-h2o-3/issues/537): Switch from NanoHTTPD to Jetty
- [GitHub](https://github.com/h2oai/h2o-3/commit/5987666d56d2a272b7b521cd9eb9cde3de6de0b0): Allow for "most" and "mode" in groupby
- [GitHub](https://github.com/h2oai/h2o-3/commit/930be126da18e6d4ed9078493dc788e22ea7e4c5): Added NA check to checking for matches in categorical columns
- [#14441](https://github.com/h2oai/h2o-3/issues/14441): Dropped UDP mode in favor of TCP
- [#14402](https://github.com/h2oai/h2o-3/issues/14402): /3/DownloadDataset.bin is now a registered handler in JettyHTTPD.java. Allows streaming of large downloads from H2O.[GitHub](https://github.com/h2oai/h2o-3/commit/a65a116875ca17eaf5b3535135f152781b51a40f)
- [#14824](https://github.com/h2oai/h2o-3/issues/14824): Implemented per-row 1D, 2D and 3D DCT transformations for signal/image/volume processing
- [#14650](https://github.com/h2oai/h2o-3/issues/14650): LDAP Integration
- [private-#329](https://github.com/h2oai/private-h2o-3/issues/329): LDAP Integration
- [private-#456](https://github.com/h2oai/private-h2o-3/issues/456): Added https support
- [GitHub](https://github.com/h2oai/h2o-3/commit/ced34107f71b5fe3c5ff830c827563be3d0c0286): Added mapr5.0 version to builds
- [GitHub](https://github.com/h2oai/h2o-3/commit/9b92f571cd6c4710b5454a425fce090b99128b35): Add Vec.Reader which replaces lost caching

##### Web UI

- [GitHub](https://github.com/h2oai/h2o-3/commit/15eece855e8cfd1598aafadc42ffab9fb170e916): Disallow N-fold CV for GLM when lambda-search is on.
- [GitHub](https://github.com/h2oai/h2o-3/commit/d3b7f01a10ff68c644f0a823051e23ebc4fc39f0): Added typeahead for http and https.
- [#14780](https://github.com/h2oai/h2o-3/issues/14780): Added Save Model and Load Model


#### Enhancements

##### Algorithms

- [GitHub](https://github.com/h2oai/h2o-3/commit/7d96961462ea1d65e85c719b0310ea453206acb4): Don't allocate input dropout helper if `input_dropout_ratio = 0`.
- [#14884](https://github.com/h2oai/h2o-3/issues/14884): Datasets : Unbalanced sparse for binomial and multinomial
- [GitHub](https://github.com/h2oai/h2o-3/commit/463207ec042af9d05c9885daba78701440ceacb1): Major code cleanup for DL: Remove dead code, deprecate `sparse`/`col_major`.
- [#14906](https://github.com/h2oai/h2o-3/issues/14906): Use prior class probabilities to break ties when making labels [GitHub](https://github.com/h2oai/h2o-3/commit/f8b188e4775d0f3671c34b3c42fe9c417d960cfd)
- [GitHub](https://github.com/h2oai/h2o-3/commit/a57d0ff742c8d84189a42e340433bc79cc33e7d4): Update DL perf Rmd file to get the overall CM error.
- [GitHub](https://github.com/h2oai/h2o-3/commit/245a1dd8fc467eecd4102f906170cbc9eba38de0): Enable training data shuffling if `train_samples_per_iteration==0` and `reproducible==true`
- [GitHub](https://github.com/h2oai/h2o-3/commit/2369b555a68d02a8cff6d052870c8cb47bb52ec2): Checkpointing for DL now follows the same convention as for DRF/GBM.
- [GitHub](https://github.com/h2oai/h2o-3/commit/6cb0fb3f2e7742597dd8fb2abce2cb22929f6782): No longer do sampling with replacement during training with `shuffle_training_data`
- [GitHub](https://github.com/h2oai/h2o-3/commit/d91daa179088dfd40df3bcd978c8f31a90419eaf): Add printout of sparsity ratio for double chunks.
- [GitHub](https://github.com/h2oai/h2o-3/commit/5170b79e98f88bc7da40be53ab54ea41d4f624da): Check memory footprint for Gram matrix in PCA and SVD initialization
- [GitHub](https://github.com/h2oai/h2o-3/commit/b4c4a4e8246749cc567b45603330f47917b009a2): Print more fill ratio debugging.
- [GitHub](https://github.com/h2oai/h2o-3/commit/a5f60727c887798ea4a305f28a3b529254f9764a): Fix the RNG for createFrame to be more random (since we are setting the seed for each row).
- [#14962](https://github.com/h2oai/h2o-3/issues/14962): Improve reporting of unstable DL models [GitHub](https://github.com/h2oai/h2o-3/commit/d6c1c4a833d82f89281024a3337fb847b0df407c)
- [#14970](https://github.com/h2oai/h2o-3/issues/14970): Improve auto-tuning for DL on large clusters / large datasets [GitHub](https://github.com/h2oai/h2o-3/commit/861763c1527372e9f65ee15a21af801db8ce3844)
- [GitHub](https://github.com/h2oai/h2o-3/commit/c9dcc80dd888e1d1c33e231aee52aec177ec93ac): Add input parameter to h2o.glrm indicating whether to ignore constant columns
- [GitHub](https://github.com/h2oai/h2o-3/commit/2dae22965b9f9a876bcb6198e3bf6340ad1f781b): Missing enums are imputed using the majority class of the column. For other types of missing categorical, just round the mean to the nearest integer.
- [GitHub](https://github.com/h2oai/h2o-3/commit/c0e21a977fcc436e984da42158f7b3184e3b63f2): Skip rows in training frame with missing value(s) if requested
- [GitHub](https://github.com/h2oai/h2o-3/commit/f96b53d2d688201133c15d87b4a4ef06c71bfbfb): Speed up direct SVD by working with transpose directly
- [GitHub](https://github.com/h2oai/h2o-3/commit/5703a88bf630c1ab61a54ec7523ee2587d8309a2): Fix a bug in initialization of SVD and change l2 norm to sum of squared error in convergence test.
- [GitHub](https://github.com/h2oai/h2o-3/commit/88ed523b674588607a8af7c27357bef4aa042b49): Use absolute value for mean weight and bias checks.
- [GitHub](https://github.com/h2oai/h2o-3/commit/6b1073b310d3058b44ac2e10546115338fa5ac23): No longer leak constant chunks during AE scoring/reconstruction.
- [GitHub](https://github.com/h2oai/h2o-3/commit/c5f4c10a7df0cf81f659f09b0814bd76f1c181dc): No longer differentiate between DL model instabilitites (weights vs biases).
- [GitHub](https://github.com/h2oai/h2o-3/commit/791f9878007a6a5bd3ecf2794f7b538c8f9cd66a): Make method static, where possible.
- [GitHub](https://github.com/h2oai/h2o-3/commit/14dedf6dee5a33622f4d9a2be6d5b64627b8273c): Make GLRM seeding independent of number of chunks.


##### API

- [GitHub](https://github.com/h2oai/h2o-3/commit/9e2e14c1ce782e0016cb632e9130ce291826b97e): Added REST end-points for glrm,svd,pca,naive bayes algorithms.
- [GitHub](https://github.com/h2oai/h2o-3/commit/bd38de613dad5528979d801007f080afc221b15e): Added unicode to frame getter possibilities
- [GitHub](https://github.com/h2oai/h2o-3/commit/0dab2b0232e0b21cd658ec765e57c8c93836d1ec): Added proper lookup of offset/weights/fold_column
- [GitHub](https://github.com/h2oai/h2o-3/commit/65a43018a6de8df24eef27065916ac33c3c0074f): Data should be eagered before download_csv.
- [GitHub](https://github.com/h2oai/h2o-3/commit/99b0fa76e6efe9e268989985967fa545195e2b53): Simplified model builder
- [GitHub](https://github.com/h2oai/h2o-3/commit/508ad0e28f40f537e906a372a2760ca6730ebe94): Added None as default for "on" field
- [GitHub](https://github.com/h2oai/h2o-3/commit/f227b8e730314ab3bd30269d44d24ef6c79383cb): Removed all of the unnecessary calls to h2o.init and removed the unnecessary environment variable for version checking during testing
- [#15004](https://github.com/h2oai/h2o-3/issues/15004): rename the coordinate decent solvers in the REST API / Flow to <mumble> (experimental)


##### Grid Search

- [GitHub](https://github.com/h2oai/h2o-3/commit/83991bb91c7a523e32f8e106d91b7bb7343655f8): Added check that x is not null before verifying data in unsupervised grid search algorithm
- [GitHub](https://github.com/h2oai/h2o-3/commit/88553f6423e1a9713aa4325f2ca540491f5ea27b): Made naivebayes parameters gridable.
- [#14897](https://github.com/h2oai/h2o-3/issues/14897): Called drf as randomForest in algorithm option [GitHub](https://github.com/h2oai/h2o-3/commit/0334fa3cd76653dfe6233013247ee7fe7d68abfd)
- [GitHub](https://github.com/h2oai/h2o-3/commit/ef75ceaaab345959f9af07921cc2fcc4272f181a): Validation of grid parameters against algo `/parameters` rest endpoint.
- [#14944](https://github.com/h2oai/h2o-3/issues/14944): Train N-fold CV models in parallel [GitHub](https://github.com/h2oai/h2o-3/commit/108e097babe1c97cb83912f04fd68f444b5c6fc1)
- [#14942](https://github.com/h2oai/h2o-3/issues/14942): grid: would be good to add to h2o.grid R help example, how to access the individual grid models


##### Python

- [GitHub](https://github.com/h2oai/h2o-3/commit/e448879646b01052b565662d605dead4c690290d): Refactored into h2o.system_file so it's parallel to R client.
- [GitHub](https://github.com/h2oai/h2o-3/commit/d7e7172e0ea4282e4c842e083fb797b2567d5e3b): Added `h2o_deprecated` decorator
- [GitHub](https://github.com/h2oai/h2o-3/commit/8dd038f89ad3e5996b6ef0d2eaec68e00f881583): Use `import_file` in `import_frame`
- [GitHub](https://github.com/h2oai/h2o-3/commit/625b22d4f1731f7cc69d2b7fabb75868722dce78): Handle a list of columns in python group-by api
- [GitHub](https://github.com/h2oai/h2o-3/commit/e0b700f524ec589502708d62f5f16189db317a47): Use pandas if available for twodimtables and h2oframes
- [GitHub](https://github.com/h2oai/h2o-3/commit/684bfde2da68b6e2d23929909a3fe099d1f23e9c): Transform the parameters list into a dict with keys being the parameter label
- [GitHub](https://github.com/h2oai/h2o-3/commit/22fd873952c4ae30853a7bc459c170ae7f3a1aa4): Added pop option which does inplace update on a frame (Frame.remove)
- [GitHub](https://github.com/h2oai/h2o-3/commit/9c57681c67d2fbd9a52a7779bcc4f336c7c45d42): ncol,dim,shape, and friends are now all properties
- [#13204](https://github.com/h2oai/h2o-3/issues/13204): Write python version of h2o.init() which knows how to start h2o
- [#14865](https://github.com/h2oai/h2o-3/issues/14865): Method to get parameters of model in Python API
- [GitHub](https://github.com/h2oai/h2o-3/commit/4048429c7f9af76a993f16f883e0b81dc827427a): Allow for single alpha specified not be in a list
- [GitHub](https://github.com/h2oai/h2o-3/commit/f19f4cf730f57d872f8685ee3eecc604be1f74a2): Updated endpoint for python client `download_csv`
- [GitHub](https://github.com/h2oai/h2o-3/commit/311762edf2cb2396bc708b6dc4fe5236a7a92566): Allow for enum in scale/mean/sd (ignore or give NA)
- [GitHub](https://github.com/h2oai/h2o-3/commit/c2b15c340196f08f7b8dfbdfe4936cb1d24e0ee4): Allow for `n_jobs=-1` and `n_jobs > 1` for Parallel jobs
- [GitHub](https://github.com/h2oai/h2o-3/commit/650525f327d142ad8a3048ef742874637ab92d58): Added `frame_id` property to frame
- [GitHub](https://github.com/h2oai/h2o-3/commit/268e3791c211a00d03af7d01998443b1fb8b6080): Removed remaining splats on dicts
- [GitHub](https://github.com/h2oai/h2o-3/commit/8ead75d3e83a34d479617a7dbd18748ada599d0d): Removed need to splat pass thru args
- [GitHub](https://github.com/h2oai/h2o-3/commit/802aa472f41030884e7f15b1ad13e5a9e555851c): Added `get_jar` flag to `download_pojo`

##### R

- [#14825](https://github.com/h2oai/h2o-3/issues/14825): Rewrote h2o.ensemble to utilize nfolds/fold_column in h2o base learners
- [GitHub](https://github.com/h2oai/h2o-3/commit/44581bb46e43ef20100249cf7271590aaf3953a2): Added `max_active_predictors`.
- [GitHub](https://github.com/h2oai/h2o-3/commit/58143a4b6fc49d1974b64dc16afdabc8e5b1d621): Updated REST call from R for model export
- [#14812](https://github.com/h2oai/h2o-3/issues/14812): Removed addToNavbar from RequestServer [GitHub](https://github.com/h2oai/h2o-3/commit/9362909fbce887c282783da6e9efa9e3a9a9b96c)
- [GitHub](https://github.com/h2oai/h2o-3/commit/9b4f8818eba32abfe4b393d2340df793204abe0d): Add "Open H2O Flow" message.
- [GitHub](https://github.com/h2oai/h2o-3/commit/3b3bc6fc67237306495433b030833a7f6d3e603f): Replaced additive float op by multiplication
- [GitHub](https://github.com/h2oai/h2o-3/commit/70a2e5d859f6c6f9075bdfb93e77aa12c23b1074): Reimplement checksum for Model.Parameters
- [GitHub](https://github.com/h2oai/h2o-3/commit/df6cc628edf2577d05fd0deae68a0d63b04d11c4): Remove debug prints.
- [#14816](https://github.com/h2oai/h2o-3/issues/14816): Removed the need for String[] path_params in RequestServer.register() [GitHub](https://github.com/h2oai/h2o-3/commit/5dfca019b1c69c2814911bdfe485fc888525ec99)
- [#14815](https://github.com/h2oai/h2o-3/issues/14815): Removed the writeHTML_impl methods from all the schemas
- [#14813](https://github.com/h2oai/h2o-3/issues/14813): Made _doc_method optional in the in Route constructors [GitHub](https://github.com/h2oai/h2o-3/commit/a0bd6d7bf065bc78ac34864c1e095ed53dacd5a1)
- [#14817](https://github.com/h2oai/h2o-3/issues/14817): Changed RequestServer so that only one handler instance is created for each Route
- [GitHub](https://github.com/h2oai/h2o-3/commit/1b8e6f2b5f7ddb0e9ae7b976c14f03ae4de8c627): Swapped out rjson for jsonlite for better handling of odd characters from dataset.
- [GitHub](https://github.com/h2oai/h2o-3/commit/b12175ee2cde22172b61ee49e492f9acff2421bd): Prettify R's grid output.
- [#14799](https://github.com/h2oai/h2o-3/issues/14799): R now respects the TwoDimTable's column types
- [GitHub](https://github.com/h2oai/h2o-3/commit/2e9161f1682df37736451b74465f5bb422d64cc5): Fixes show method for grid object when `hyper_params` is empty.
- [GitHub](https://github.com/h2oai/h2o-3/commit/8c77c2ef785292adec6616f5243a38a6e3105ebc): h2o.levels returns R vector for single column
- [GitHub](https://github.com/h2oai/h2o-3/commit/08f1e95f17c5dfbff8bcdea6ef3a460751fa8ba2): Uses PredictCsv from genmodel now.
- [GitHub](https://github.com/h2oai/h2o-3/commit/5cbf42e300df0ef45f0bd4558fa5a339ea97cdaf): Exposed stacktraces in R's summary() call.
- [GitHub](https://github.com/h2oai/h2o-3/commit/b4dac1e2fb230f7740540ffa9f9379e03a626935): print type of failed value in $<-
- [GitHub](https://github.com/h2oai/h2o-3/commit/050956e1562ee51ad7de4cd93dcc0e6195b83445): allow value to be integer in $<-
- [GitHub](https://github.com/h2oai/h2o-3/commit/785111016a2c7a7fcdaa90846d725caadb0f9192): Check for `is_client` being NULL since older H2O clusters may not have `is_client`.


##### Sparkling Water

- [GitHub](https://github.com/h2oai/h2o-3/commit/5035be242cb6f3a594902cb730301fcd7d2cfec6): Copy content of h2o-dist into target directory.

##### System

- [GitHub](https://github.com/h2oai/h2o-3/commit/cf80c73439b56340688481a473a18c61ccbe0818): Rename label fields in prediction object.
- [GitHub](https://github.com/h2oai/h2o-3/commit/32685d0413bab4a5c142e2fb79205e6d5062ad69): Uses the original Vec's domain in alignment
- [GitHub](https://github.com/h2oai/h2o-3/commit/3cd21a2ee87ec088f5e2bbd99a56cdf0247dc790): Added columnName and unknownLevel to PredictUnknownCategoricalLevelException.
- [#14520](https://github.com/h2oai/h2o-3/issues/14520): Added compression of 64-bit Reals  [GitHub](https://github.com/h2oai/h2o-3/commit/5ef3008351b36fd8b1261d162cce6e60a071a462)
- [GitHub](https://github.com/h2oai/h2o-3/commit/4971f63b283fcb90ffa076574d4ff597a8ae4356): Added time information to buildinfo.json.
- [GitHub](https://github.com/h2oai/h2o-3/commit/9c33e5718f0ce9cb7220ea6f5152b0e751e5ec50): Put build metadata into a json file.
- -[GitHub](https://github.com/h2oai/h2o-3/commit/4971f63b283fcb90ffa076574d4ff597a8ae4356): Add time information to buildinfo.json.
- [GitHub](https://github.com/h2oai/h2o-3/commit/1d22bfa9fb23b0ebe1b9a389688c5701c0362033): Delete any prior main CV models of the same key if CV model building is cancelled before the main model started to build.
- [GitHub](https://github.com/h2oai/h2o-3/commit/524d94d31f40d19e1fc055995860a6d4bdba7b67): Change loading name parameter to a String to address a Flow issue.
- [GitHub](https://github.com/h2oai/h2o-3/commit/48e7aa47cacade1c964d89920e0218ebedae182b): Remove extra assertion to avoid NPEs after client call of bulk remove after done() is called but before the finally is done with updateModelOutput.
- [GitHub](https://github.com/h2oai/h2o-3/commit/c5a52b88746e8c82c04ea01cbb2c4a6910749a34): Ensures that date time methods return year/month/day values in the currently set timezone.
- [GitHub](https://github.com/h2oai/h2o-3/commit/8789d8a1bbf8260d596bcc1692fe1f41dcdf81aa): Frees memory from streamed zip reads after the chunk has been parsed.
- [GitHub](https://github.com/h2oai/h2o-3/commit/c6c76e04b7efb1a3be470603e7c4b44dc8cc7767): Unifies categorical strings to UTF-8 and warns the user about all conversion.
- [GitHub](https://github.com/h2oai/h2o-3/commit/917bfe406fb59d7a0d9deecea524a91dd049073a): add isNA checks to scale
- [GitHub](https://github.com/h2oai/h2o-3/commit/c98348d7d4f4883d887047670783dbb9ed3eeb32): Do not start UDPRecevier thread (unless running with useUDP option)


##### Web UI

- [#14925](https://github.com/h2oai/h2o-3/issues/14925): Flow: use streamining endpoint /3/DownloadDataset.bin



#### Bug Fixes

##### Algorithms

- [#14748](https://github.com/h2oai/h2o-3/issues/14748): Deadlock while running GBM
- [GitHub](https://github.com/h2oai/h2o-3/commit/b2fd9150aeb8c0816d2e09a09bc133517d6aa72f): Fix name for `standardized_coefficient_magnitudes`.
- [#14737](https://github.com/h2oai/h2o-3/issues/14737): Setting gbm's balance_classes to True produces suspect models
- [#14808](https://github.com/h2oai/h2o-3/issues/14808): K-Means: negative sum-of-squares after mean imputation
- [GitHub](https://github.com/h2oai/h2o-3/commit/09a73ba1d1f5b24b56af842d75259df4ae52af96): Set the iters counter during kmeans center initialization correctly
- [GitHub](https://github.com/h2oai/h2o-3/commit/bfa9cd5179f7b1dce85895db80b28ec9ec743f71): fixed parenthesis in GLM POJO generation
- [GitHub](https://github.com/h2oai/h2o-3/commit/ed0dfe29aab903586a64565a531ecc52b3414dce): Should be updating model each iteration with the newly fitted kmeans clusters, not the old ones!
- [#14826](https://github.com/h2oai/h2o-3/issues/14826): GLRM with Simplex Fails with Infinite Objective
- [#14631](https://github.com/h2oai/h2o-3/issues/14631): GBM:Math correctness for Gamma with offsets/weights
- [#13443](https://github.com/h2oai/h2o-3/issues/13443): Trees in GBM change for identical models [GitHub](https://github.com/h2oai/h2o-3/commit/7838e6773fb128c4ab549930c167fd109369fc29)
- [#14888](https://github.com/h2oai/h2o-3/issues/14888): R^2 stopping criterion isn't working [GitHub](https://github.com/h2oai/h2o-3/commit/b074b2a1d0915f701a8c06e69fa53e7fd0cb8cdc)
- [#14739](https://github.com/h2oai/h2o-3/issues/14739): GLM: cross-validation bug  [GitHub](https://github.com/h2oai/h2o-3/commit/0cfdd972c118ff158c88ffa4b627de8a245ecaba)
- [#14646](https://github.com/h2oai/h2o-3/issues/14646): GLM : Lending club dataset => build GLM model => 100% complete => click on model => null pointer exception [GitHub](https://github.com/h2oai/h2o-3/commit/676dff79ec179059e9aa77a09357091369f47791)
- [#14943](https://github.com/h2oai/h2o-3/issues/14943): error returned on prediction for xval model
- [#14892](https://github.com/h2oai/h2o-3/issues/14892): Properly implement Maxout/MaxoutWithDropout [GitHub](https://github.com/h2oai/h2o-3/commit/633288f3ceaaa4e4f4a0f39d6d6d1f1b635711c5)
- [GitHub](https://github.com/h2oai/h2o-3/commit/669e364d1ca519bebc87781341f226ecadcefff0): print actual number of columns (was just #cols) in DRF init
- [#14976](https://github.com/h2oai/h2o-3/issues/14976): Fix setting the proper job state in DL models [GitHub](https://github.com/h2oai/h2o-3/commit/e564e70404afb06e29c4cdb806787a9998f98124)
- [#14913](https://github.com/h2oai/h2o-3/issues/14913): Splitframe with rapids is not blocking
- [#14953](https://github.com/h2oai/h2o-3/issues/14953): nfold: when user cancels an nfold job, fold data still remains in the cluster memory
- [#14952](https://github.com/h2oai/h2o-3/issues/14952): nfold: cancel results in a  java.lang.AssertionError
- [#14873](https://github.com/h2oai/h2o-3/issues/14873): Canceled GBM with CV keeps lock
- [GitHub](https://github.com/h2oai/h2o-3/commit/02c3b4a20c09a52924a588118a4032edc2208538): Fix DL checkpoint restart with new data.


##### API

- [#14919](https://github.com/h2oai/h2o-3/issues/14919): Change Schema behavior to accept a single number in place of array [GitHub](https://github.com/h2oai/h2o-3/commit/4451e192b58233180371047acb4336aae2629210)
- [#14878](https://github.com/h2oai/h2o-3/issues/14878): Iced deserialization fails for Enum Arrays



##### Grid

- [#14835](https://github.com/h2oai/h2o-3/issues/14835): Grid: progress bar not working for grid jobs
- [#14834](https://github.com/h2oai/h2o-3/issues/14834): Grid: the meta info should not be dumped on the R screen, once the grid job is over
- [GitHub](https://github.com/h2oai/h2o-3/commit/ed586eec9ea847fb10888d6a9caa55766a680678): [#14835] Fix grid update.
- [#14833](https://github.com/h2oai/h2o-3/issues/14833): Grid search: observe issues with model naming/overwriting and error msg propagation [GitHub](https://github.com/h2oai/h2o-3/commit/92a5eaeac52628d8ced9e48487c8b4d6ed003e23)
- [private-#309](https://github.com/h2oai/private-h2o-3/issues/309): R: kmeans grid search doesn't work
- [#14862](https://github.com/h2oai/h2o-3/issues/14862): Grid appends new models even though models already exist.
- [#14833](https://github.com/h2oai/h2o-3/issues/14833): Grid search: observe issues with model naming/overwriting and error msg propagation
- [#14904](https://github.com/h2oai/h2o-3/issues/14904): Grid: glm grid on alpha fails with error "Expected '[' while reading a double[], but found 1.0"
- [#14836](https://github.com/h2oai/h2o-3/issues/14836): Grid: if user specify the parameter value he is running the grid on, would be good to warn him/her
- [#14902](https://github.com/h2oai/h2o-3/issues/14902): Grid: randomForest: unsupported grid params and wrong error msg

##### Hadoop

- [#14986](https://github.com/h2oai/h2o-3/issues/14986): importModel from hdfs doesn't work
- [#14977](https://github.com/h2oai/h2o-3/issues/14977): Clicking shutdown in the Flow UI dropdown does not exit the Hadoop cluster


##### Python

- [#14753](https://github.com/h2oai/h2o-3/issues/14753): Python client h2o.remove_vecs (ExprNode) makes bad ast
- [#14759](https://github.com/h2oai/h2o-3/issues/14759): Unable to read H2OFrame from Python
- [#14728](https://github.com/h2oai/h2o-3/issues/14728): Python importFile does not import all files in directory, only one file [GitHub](https://github.com/h2oai/h2o-3/commit/7af19a70c5ff5887feab1732d444ce345d0737b7)
- [GitHub](https://github.com/h2oai/h2o-3/commit/0d8e8bcb74e89505324d8c2a3b795680a33aeea7): parameter name is "dir" not "path"
- [#14657](https://github.com/h2oai/h2o-3/issues/14657): Python: Options for handling NAs in group_by is broken
- [#14389](https://github.com/h2oai/h2o-3/issues/14389): Intermittent Unimplemented rapids exception: pyunit_var.py . Also prior test got unimplemented too, but test didn't fail (client wasn't notified)
- [#14099](https://github.com/h2oai/h2o-3/issues/14099): Python: Need to be able to access resource genmodel.jar
- [GitHub](https://github.com/h2oai/h2o-3/commit/fb651714adcff4a407442160173c6499faabc79b): Fix download of pojo in Python.


##### R

- [GitHub](https://github.com/h2oai/h2o-3/commit/6851fa43a971668157e9f16aeda1446043760e0b): Fixed bug in `h2o.ensemble .make_Z` function
- [#14760](https://github.com/h2oai/h2o-3/issues/14760): R: h2o.importFile doesn't allow user to choose column type during parse
- [#14732](https://github.com/h2oai/h2o-3/issues/14732): R: Fails to return summary on subsetted frame [GitHub](https://github.com/h2oai/h2o-3/commit/025500c8f48c9a61053238da7b3630e04386ac32)
- [#14872](https://github.com/h2oai/h2o-3/issues/14872): R: Adding column to frame changes string enums in column to numerics
- [#14900](https://github.com/h2oai/h2o-3/issues/14900): R: h2o.levels return only the first factor of factor levels
- [#14828](https://github.com/h2oai/h2o-3/issues/14828): R: sd function should convert enum column into numeric and calculate standard deviation [GitHub](https://github.com/h2oai/h2o-3/commit/6e9c562b72f79e2fd1d579fc9ca2a784acddab80)
- [#14224](https://github.com/h2oai/h2o-3/issues/14224): R: h2o.hist needs to run pretty function for pretty breakpoints to get same results as R's hist [GitHub](https://github.com/h2oai/h2o-3/commit/f3e935bba3e94e5ece27b67856da45e0ac616431)
- [#14827](https://github.com/h2oai/h2o-3/issues/14827): R: h2o.performance returns error (not warning) when model is reloaded into H2O
- [#14687](https://github.com/h2oai/h2o-3/issues/14687): h2o R : subsetting data :h2o removing wrong columns, when asked to delete more than 1 columns
- [GitHub](https://github.com/h2oai/h2o-3/commit/425dbd9ee95487bce5424ed76e919821da0a2b10): fix h2o.levels issue
- [#14936](https://github.com/h2oai/h2o-3/issues/14936): R: setting weights_column = NULL causes unwanted variables to be used as predictors




##### Sparkling Water

- [#13169](https://github.com/h2oai/h2o-3/issues/13169): create conversion tasks from primitive RDD
- [GitHub](https://github.com/h2oai/h2o-3/commit/f45577b04a16221b53e2c59d1388ab8e8e2de87a): Fix return value issue in distribution script.


##### System

- [private-#346](https://github.com/h2oai/private-h2o-3/issues/346): getFrame fails on Parsed Data
- [#13367](https://github.com/h2oai/h2o-3/issues/13367): Fix parsing for high-cardinality categorical features [GitHub](https://github.com/h2oai/h2o-3/commit/99ae85dbe9305992a8e3f4b9e792c8f73e61d2fa)
- [#14123](https://github.com/h2oai/h2o-3/issues/14123): Parse: Cancel parse unreliable; does not work at all times
- [#14831](https://github.com/h2oai/h2o-3/issues/14831): Ability to ignore files during parse [GitHub](https://github.com/h2oai/h2o-3/commit/43e08765d1d6be49a9a6a83daf57ef1aaa511061)
- [#13765](https://github.com/h2oai/h2o-3/issues/13765): Parse : Parsing compressed files takes too long
- [#14880](https://github.com/h2oai/h2o-3/issues/14880): Parse: 2 node cluster takes 49min vs  40sec on a 1 node cluster [GitHub](https://github.com/h2oai/h2o-3/commit/cb720caae81ac754a7cbb0452958d9cb7c92c2d8)
- [#14402](https://github.com/h2oai/h2o-3/issues/14402): Convert /3/DownloadDataset to streaming
- [#14953](https://github.com/h2oai/h2o-3/issues/14953): nfold: when user cancels an nfold job, fold data still remains in the cluster memory
- [#14952](https://github.com/h2oai/h2o-3/issues/14952): nfold: cancel results in a  java.lang.AssertionError
- [#14873](https://github.com/h2oai/h2o-3/issues/14873): Canceled GBM with CV keeps lock [GitHub](https://github.com/h2oai/h2o-3/commit/848c015b1ac6dee7517bd9d82eeae2919d01c9c6)
- [#14950](https://github.com/h2oai/h2o-3/issues/14950): CreateFrame isn't totally random
- [GitHub](https://github.com/h2oai/h2o-3/commit/6b12de4c31e5401ae0b7bd3283092f68b99bb45a): Fixes a bug that allowed big buffers to be constantly reallocated when it wasn't needed. This saves memory and time.
- [GitHub](https://github.com/h2oai/h2o-3/commit/009e888d07e89dcbab51556cb56bcbc6eaffdedc): Fix print statement.
- [GitHub](https://github.com/h2oai/h2o-3/commit/4c40cf1815c27369aa12c7c0ed19272cbd4a7499): Fixed orderly shutdown to work with flatfile.
- [#14956](https://github.com/h2oai/h2o-3/issues/14956): Parse  : Lending club dataset parse => cancelled by user
- [#14978](https://github.com/h2oai/h2o-3/issues/14978): Shutdown => unimplemented error on  curl -X POST 172.16.2.186:54321/3/Shutdown.html
- [#15010](https://github.com/h2oai/h2o-3/issues/15010): Download frame brings down cluster
- [#15007](https://github.com/h2oai/h2o-3/issues/15007): Cannot mix negative and positive array selection
- [#14974](https://github.com/h2oai/h2o-3/issues/14974): Save model to HDFS fails



##### Web UI

- [#14964](https://github.com/h2oai/h2o-3/issues/14964): Histograms in Flow are slightly off
- [#14979](https://github.com/h2oai/h2o-3/issues/14979): exportModel from Flow to HDFS doesn't work



---

### Simons (3.0.1.7) - 8/11/15

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-simons/7/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-simons/7/index.html</a>


#### New Features
The following changes represent features that have been added since the previous release:

##### Python

- [#13676](https://github.com/h2oai/h2o-3/issues/13676): Add nfolds to R/Python


##### Web UI

- [private-#320](https://github.com/h2oai/private-h2o-3/issues/320): Print Flow to PDF / Printer


#### Enhancements
The following changes are improvements to existing features (which includes changed default values):



##### Algorithms

- [GitHub](https://github.com/h2oai/h2o-3/commit/a15490b3507265e2768698ca768c98b6eb40d85b): add seed to the model building that uses balance_classes, for determinism/repeatability
- [GitHub](https://github.com/h2oai/h2o-3/commit/fa4118960f5ab97f391ff86b077482ee2575c6a4): Reduce the frequency at which tiny tree models are printed to stdout: Only print during the first 4 seconds if `score_each_iteration` is enabled.
- [GitHub](https://github.com/h2oai/h2o-3/commit/610e3fb8c3df087c9f21b19a33e97d53f0829e6e): Only call the limited printout for TwoDimTables during Model.toString () that prints all TwoDimTables of the model._output.
- [GitHub](https://github.com/h2oai/h2o-3/commit/0898eff8d2c7a01f29a23754eab2b0d954480faf): Only print up to 10 rows of TwoDimTables in ASCII logs (first/last 5).
- [GitHub](https://github.com/h2oai/h2o-3/commit/77e62aa916c11f57aa66cec12ccb707d946f76a3): Remove some overflow/underflow checks: Let exp(x) be small and log(x) be large.
- [GitHub](https://github.com/h2oai/h2o-3/commit/1e0945ea13bc18f2d5dcd8fee270e52be2bb250b): Add `nbins_top_level` parameter to DRF/GBM. Not yet in R.
- [GitHub](https://github.com/h2oai/h2o-3/commit/15eece855e8cfd1598aafadc42ffab9fb170e916): Disallow N-fold CV for GLM when lambda-search is on.


##### API

- [GitHub](https://github.com/h2oai/h2o-3/commit/a01fde0c855e9194a1fead2f39587d958f915438): Cleanup of public API of Schema.java. Improve its JavaDoc a lot.


##### Python

- [#14729](https://github.com/h2oai/h2o-3/issues/14729): Improve python online documentation
- [#14466](https://github.com/h2oai/h2o-3/issues/14466): Python : Weights R tests to be ported from R for GLM/GBM/RF/DL
- [GitHub](https://github.com/h2oai/h2o-3/commit/b53a2e564e0b85d6fb2da54a5ff232e20829a967): adjust to split frame jobs result
- [GitHub](https://github.com/h2oai/h2o-3/commit/05516cb5a2297a25125e80603c55e97fc2e5b92b): allow for update thingy to be a tuple (so rows and columns)
- [GitHub](https://github.com/h2oai/h2o-3/commit/8ebfd7c7ec6ff56732af96ffdb0b90908c9c0f01): when starting h2o jvm with h2o.init(), give h2o child process different id than parent, so it doesn't get killed on Ctrl-C
- [GitHub](https://github.com/h2oai/h2o-3/commit/71b6ea7fa61b0fe9962b6a5536e13e661ed5e656): add option to turn off progress bar print out
- [GitHub](https://github.com/h2oai/h2o-3/commit/bd38de613dad5528979d801007f080afc221b15e): add unicode to frame getter possibilities
- [GitHub](https://github.com/h2oai/h2o-3/commit/268e3791c211a00d03af7d01998443b1fb8b6080): remove remaining splats on dicts
- [GitHub](https://github.com/h2oai/h2o-3/commit/8ead75d3e83a34d479617a7dbd18748ada599d0d): no need to splat pass thru args
- [GitHub](https://github.com/h2oai/h2o-3/commit/0dab2b0232e0b21cd658ec765e57c8c93836d1ec): proper lookup of offset/weights/fold_column
- [GitHub](https://github.com/h2oai/h2o-3/commit/65a43018a6de8df24eef27065916ac33c3c0074f): data should be eagered before download_csv.
- [GitHub](https://github.com/h2oai/h2o-3/commit/99b0fa76e6efe9e268989985967fa545195e2b53): simplify model builder
- [GitHub](https://github.com/h2oai/h2o-3/commit/508ad0e28f40f537e906a372a2760ca6730ebe94): use None as default for "on" field
- [GitHub](https://github.com/h2oai/h2o-3/commit/802aa472f41030884e7f15b1ad13e5a9e555851c): add `get_jar` flag to `download_pojo`
- [GitHub](https://github.com/h2oai/h2o-3/commit/f227b8e730314ab3bd30269d44d24ef6c79383cb):remove all of the unnecessary calls to h2o.init and remove the unnecessary environment variable for version checking during testing



##### R

- [#14708](https://github.com/h2oai/h2o-3/issues/14708): Improve help message of h2o.init function
- [GitHub](https://github.com/h2oai/h2o-3/commit/887c4bda1219b9d59ddd52604a6d535a01681e94): add valid expression to list of accepted R CMD check outputs.
- [GitHub](https://github.com/h2oai/h2o-3/commit/7762d4a5a184847fd79f2c4f6c190f4aa712f37a): added h2o.anomaly demo to r package


##### System

- [GitHub](https://github.com/h2oai/h2o-3/commit/887be2cdcfef7b8e954950447b295d14c7e30b04): Add -JJ command line argument to allow extra JVM arguments to be passed.
- [GitHub](https://github.com/h2oai/h2o-3/commit/31e5cb6576cd9dd5e738db64f93de5d3f5fe6154): Refactored CSVStream to be more understandable. Fix empty chunk bug.
- [GitHub](https://github.com/h2oai/h2o-3/commit/dbd87534f7f9cd2321f4e646b1171e769845205d): Add hintFlushRemoteChunk to CSVStream.
- [GitHub](https://github.com/h2oai/h2o-3/commit/7b5258f0cfc71310d3105cc1a1bb80952632a073): Add parameterized route for frame export
- [GitHub](https://github.com/h2oai/h2o-3/commit/13709afc274ce0ed7796d63f57dbb2e658b56669): allow string vecs to be toEnum'd (with a sensible cap)
- [GitHub](https://github.com/h2oai/h2o-3/commit/a9ad86bd76a20953005d7eb1981c46849e1a5ad4): allow lists of numbers in reducer ops
- [GitHub](https://github.com/h2oai/h2o-3/commit/5d4eb4d3c96161c92dc5aa4ce84f91a636739778): Add warning message during POJO export if `offset_column` is specified (is not supported)
- [#14812](https://github.com/h2oai/h2o-3/issues/14812): cleanup: remove addToNavbar from RequestServer [GitHub](https://github.com/h2oai/h2o-3/commit/9362909fbce887c282783da6e9efa9e3a9a9b96c)
- [GitHub](https://github.com/h2oai/h2o-3/commit/9b4f8818eba32abfe4b393d2340df793204abe0d): Add "Open H2O Flow" message.
- [GitHub](https://github.com/h2oai/h2o-3/commit/36b2143bf81d398d6fd8b1b08ad03ae3a33731a7): Code refactoring to allow GBM JUnits to work with H2OApp in multi-node mode.
- [GitHub](https://github.com/h2oai/h2o-3/commit/3b3bc6fc67237306495433b030833a7f6d3e603f): Replace additive float op by multiplication
- [GitHub](https://github.com/h2oai/h2o-3/commit/70a2e5d859f6c6f9075bdfb93e77aa12c23b1074): Reimplement checksum for Model.Parameters
- [GitHub](https://github.com/h2oai/h2o-3/commit/df6cc628edf2577d05fd0deae68a0d63b04d11c4): Remove debug prints.
- [#14816](https://github.com/h2oai/h2o-3/issues/14816): cleanup: remove the need for String[] path_params in RequestServer.register() [GitHub](https://github.com/h2oai/h2o-3/commit/5dfca019b1c69c2814911bdfe485fc888525ec99)
- [#14815](https://github.com/h2oai/h2o-3/issues/14815): cleanup: remove the writeHTML_impl methods from all the schemas
- [#14813](https://github.com/h2oai/h2o-3/issues/14813): cleanup: make _doc_method optional in the in Route constructors [GitHub](https://github.com/h2oai/h2o-3/commit/a0bd6d7bf065bc78ac34864c1e095ed53dacd5a1)
- [#14817](https://github.com/h2oai/h2o-3/issues/14817): cleanup: change RequestServer so that only one handler instance is created for each Route




#### Bug Fixes

The following changes are to resolve incorrect software behavior:


##### Algorithms

- [#14639](https://github.com/h2oai/h2o-3/issues/14639): gbm w gamma: does not seems to split at all; all  trees node pred=0 for attached data [GitHub](https://github.com/h2oai/h2o-3/commit/5796d9e9725bcee27278a31e42c7b77089e65710)
- [#14724](https://github.com/h2oai/h2o-3/issues/14724): GBM : Deviance testing for exp family
- [#14678](https://github.com/h2oai/h2o-3/issues/14678): gbm gamma: R vs h2o same split variable, slightly different leaf predictions
- [#14719](https://github.com/h2oai/h2o-3/issues/14719): DL : Math correctness for Tweedie with Offsets/Weights
- [#14722](https://github.com/h2oai/h2o-3/issues/14722): DL : Deviance testing for exp family
- [#14720](https://github.com/h2oai/h2o-3/issues/14720): DL : Math correctness for Poisson with Offsets/Weights
- [#14618](https://github.com/h2oai/h2o-3/issues/14618): null/residual deviances don't match for various weights cases
- [#14721](https://github.com/h2oai/h2o-3/issues/14721): DL : Math correctness for Gamma with Offsets/Weights
- [#14644](https://github.com/h2oai/h2o-3/issues/14644): gbm gamma: seeing train set mse incs after sometime
- [#14688](https://github.com/h2oai/h2o-3/issues/14688): gbm w tweedie: weird validation error behavior
- [#14737](https://github.com/h2oai/h2o-3/issues/14737): setting gbm's balance_classes to True produces suspect models
- [#14808](https://github.com/h2oai/h2o-3/issues/14808): K-Means: negative sum-of-squares after mean imputation
- [GitHub](https://github.com/h2oai/h2o-3/commit/09a73ba1d1f5b24b56af842d75259df4ae52af96): Set the iters counter during kmeans center initialization correctly
- [GitHub](https://github.com/h2oai/h2o-3/commit/bfa9cd5179f7b1dce85895db80b28ec9ec743f71): fixed parenthesis in GLM POJO generation
- [GitHub](https://github.com/h2oai/h2o-3/commit/ed0dfe29aab903586a64565a531ecc52b3414dce): Should be updating model each iteration with the newly fitted kmeans clusters, not the old ones!
- [#14826](https://github.com/h2oai/h2o-3/issues/14826): GLRM with Simplex Fails with Infinite Objective
- [#14631](https://github.com/h2oai/h2o-3/issues/14631): GBM:Math correctness for Gamma with offsets/weights


##### Python

- [#14742](https://github.com/h2oai/h2o-3/issues/14742): Fixes intermittent failure seen when Model Metrics were looked at too quickly after a cross validation run.
- [#14383](https://github.com/h2oai/h2o-3/issues/14383): h2o python h2o.locate() should stop and return "Not found" rather than passing path=None to h2o? causes confusion h2o message  [GitHub](https://github.com/h2oai/h2o-3/commit/c8bdebc4caf0153a721f68963642e0ce92c311ab)
- [#14600](https://github.com/h2oai/h2o-3/issues/14600): GBM getting intermittent assertion error on iris scoring in `pyunit_weights_api.py`
- [#14734](https://github.com/h2oai/h2o-3/issues/14734): sigterm caught by python is killing h2o [GitHub](https://github.com/h2oai/h2o-3/commit/f123741c5455fa7e21d6675789fb93ed796f617b)
- [#14383](https://github.com/h2oai/h2o-3/issues/14383): h2o python h2o.locate() should stop and return "Not found" rather than passing path=None to h2o? causes confusion h2o message
- [private-#313](https://github.com/h2oai/private-h2o-3/issues/313): Python fold_column option requires fold column to be in the training data
- [private-#317](https://github.com/h2oai/private-h2o-3/issues/317): Python client occasionally throws attached error
- [GitHub](https://github.com/h2oai/h2o-3/commit/bce2e56200ba61b34d7ff10986749b94fc836c02): add missing args to kmeans
- [GitHub](https://github.com/h2oai/h2o-3/commit/99ad8f2d7eabd7aedf2f89c725bfaf09527e3cee): add missing kmeans params in
- [GitHub](https://github.com/h2oai/h2o-3/commit/ac26cf2db625a7c26645ee6d4f6cc12f6803fded): add missing checkpoint param
- [#14748](https://github.com/h2oai/h2o-3/issues/14748): Deadlock while running GBM


##### R

- [#14789](https://github.com/h2oai/h2o-3/issues/14789): h2o.glm throws an error when `fold_column` and `validation_frame` are both specified
- [#14625](https://github.com/h2oai/h2o-3/issues/14625): h2oR: when try to get a slice from pca eigenvectors get some formatting error [GitHub](https://github.com/h2oai/h2o-3/commit/8380c9697cb057f2437c8f14deea3a702f810805)
- [GitHub](https://github.com/h2oai/h2o-3/commit/bce4e036ad52d3a4dd75960653f016dc6c076622): fix broken %in% in R
- [#14790](https://github.com/h2oai/h2o-3/issues/14790): Cross-validation metrics are not displayed in R (and Python?)
- [#14798](https://github.com/h2oai/h2o-3/issues/14798): Autoencoder model doesn't display properly in R (training metrics) [GitHub](https://github.com/h2oai/h2o-3/commit/5a9880fa00615481da4c1897af3d974c42529e36)

##### System

- [#14754](https://github.com/h2oai/h2o-3/issues/14754): can't convert iris species column to a character column.
- [#14489](https://github.com/h2oai/h2o-3/issues/14489): Kmeans pojo naming inconsistency
- [GitHub](https://github.com/h2oai/h2o-3/commit/f64576a6a981ad2cc9b95e1653949bfbe4bc2de0): fix parse of range ast
- [GitHub](https://github.com/h2oai/h2o-3/commit/30f1fe3396dfc926b6ed959169b0046bbd784164): Sets POJO file name to match the class name. Prior behavior would allow them to be different and give a compile error.


##### Web UI

- [#14718](https://github.com/h2oai/h2o-3/issues/14718): Export frame not working in flow : H2OKeyNotFoundArgumentException



---

### Simons (3.0.1.4) - 7/29/15

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-simons/4/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-simons/4/index.html</a>

#### New Features

##### Algorithms
- [private-#460](https://github.com/h2oai/private-h2o-3/issues/460): Tweedie distribution for DL
- [private-#461](https://github.com/h2oai/private-h2o-3/issues/461): Poisson distribution for DL
- [private-#459](https://github.com/h2oai/private-h2o-3/issues/459): Gamma distribution for DL
- [#13675](https://github.com/h2oai/h2o-3/issues/13675): Enable nfolds for all algos (where reasonable) [GitHub](https://github.com/h2oai/h2o-3/commit/68d74cb438dd535acac18ce8233fdaa25882b6c5)
- [#14755](https://github.com/h2oai/h2o-3/issues/14755): Add toString() for all models (especially model metrics) [GitHub](https://github.com/h2oai/h2o-3/commit/c253f5ff73b1828de026f69f6846e1b85087b056)
- [GitHub](https://github.com/h2oai/h2o-3/commit/792a0789ef951bf0997251a05cb3dd8d5d92af9e): Enabling model checkpointing for DRF
- [GitHub](https://github.com/h2oai/h2o-3/commit/29b12729465cc4e0c71597616a748ad12ab1a099): Enable checkpointing for GBM.
- [#14662](https://github.com/h2oai/h2o-3/issues/14662): fold assignment in N-fold cross-validation


##### Python
- [#13385](https://github.com/h2oai/h2o-3/issues/13385): Expose ParseSetup to user in Python
- [#14217](https://github.com/h2oai/h2o-3/issues/14217): Python: getFrame and getModel missing
- [private-#368](https://github.com/h2oai/private-h2o-3/issues/368): support rbind in python
- [#14193](https://github.com/h2oai/h2o-3/issues/14193): python to have exportFile calll
- [GitHub](https://github.com/h2oai/h2o-3/commit/bf9cbf96641cbc027e257298cdc67ed6f5bfb065): add cross-validation parameter to metric accessors and respective pyunit
- [#14693](https://github.com/h2oai/h2o-3/issues/14693): Cross-validation metrics should be shown in R and Python for all models


##### R
- [#13384](https://github.com/h2oai/h2o-3/issues/13384): Expose ParseSetup to user in R
- [GitHub](https://github.com/h2oai/h2o-3/commit/d15c0df32a048fbb358ce3daf6968470de9faf6a): add mean residual deviance accessor to R interface
- [GitHub](https://github.com/h2oai/h2o-3/commit/dd93faa00c7c210aa05225874e608f3a8d9ca5f8): incorporate cross-validation metric access into the R client metric accessors
- [GitHub](https://github.com/h2oai/h2o-3/commit/cf477fb90beeeb901a0a999bdad562c3fa37d818): R interface for checkpointing in RF enabled


##### System

- [#14699](https://github.com/h2oai/h2o-3/issues/14699): Add 24-MAR-14 06.10.48.000000000 PM style date to autodetected


#### Enhancements


#####API

- [#14422](https://github.com/h2oai/h2o-3/issues/14422): design for cross-validation APIs [GitHub](https://github.com/h2oai/h2o-3/commit/6ceac99d25b40ca1a14523056e7b48dc4cd0c853)


##### Algorithms

- [GitHub](https://github.com/h2oai/h2o-3/commit/2c3d06389c9b3fcfeb7df59479653685b5dceb10): Add proper deviance computation for DL regression.
- [GitHub](https://github.com/h2oai/h2o-3/commit/aa5dfeaed742d5e510cbec55faf4668f02086010): Print GLM model details to the logs.
- [GitHub](https://github.com/h2oai/h2o-3/commit/e4b02fc55ba18b05f808d083c75fdcd572ebe88f): Disallow categorical response for GLM with non-binomial family.
- [GitHub](https://github.com/h2oai/h2o-3/commit/0b9aba8cc9cc7e92696dc627e253bd4c47511801): Disallow models with more than 1000 classes, can lead to too large values in DKV due to memory usage of 8*N^2 bytes (the Metrics objects which are in the model output)
- [GitHub](https://github.com/h2oai/h2o-3/commit/cc6384aebc6ef9c7429066f5303c90ce3b551689): DL: Don't train too long in single node mode with auto-tuning.
- [GitHub](https://github.com/h2oai/h2o-3/commit/b108a2b170c4ed4e08d19c1e492316eef8668a0a): Use mean residual deviance to do early stopping in DL.
- [GitHub](https://github.com/h2oai/h2o-3/commit/08da7157e0e3fed68a0411b77d19d00666fa34ea): Add a "AUTO" setting for fold_assignment (which is Random). This allows the code to reject non-default user-given values if n-fold CV is not enabled.


##### Python

- [private-#383](https://github.com/h2oai/private-h2o-3/issues/383): Python has to play nicely in a polyglot, long-running environment
- [GitHub](https://github.com/h2oai/h2o-3/commit/16c4b179cd0e35b59c0dd3c2831ab9c1d1f9970b): simplify ast in python frame slicer
- [GitHub](https://github.com/h2oai/h2o-3/commit/7f18d01b9c9a36f85418e7fa79a1a3b4b40a0a9d): add cross validation metrics and mean residual deviance to model show()
- [GitHub](https://github.com/h2oai/h2o-3/commit/93a371ad7a33199c3e962b61f3ed447d25d6adf3): any to take a frame, simplify python's `__contains__`


##### R

- [GitHub](https://github.com/h2oai/h2o-3/commit/b39364f34d9df926af44da29d0a43d096f9a2c0b): On detaching h2o R package, only shut down H2O instance if it was started by the R client
- [GitHub](https://github.com/h2oai/h2o-3/commit/4db6a89512bd695a3c698b64c3a5d4a298075049): update h2o load

##### System

- [GitHub](https://github.com/h2oai/h2o-3/commit/2a926c0950a98eff5a4c06aeaf0373e17176ecd8): Print a handy message (Open H2O Flow in your web browser) when the cluster comes up like Sparkling Water does.
- [GitHub](https://github.com/h2oai/h2o-3/commit/d7cdd52119fd718ae579e2ec9f5229324bb030e5): Replace memory leaky RCurl getURL with curlPerform.
- [GitHub](https://github.com/h2oai/h2o-3/commit/2aa09c4d560e17446b00a568158c1d5164df68df): Add -disable_web parameter.
- [GitHub](https://github.com/h2oai/h2o-3/commit/7c810020a03e5173e8e80f4dd6262d41e60b1651): allow numerics in match
- [GitHub](https://github.com/h2oai/h2o-3/commit/362b79501dbbbddcba98678cf66794a96e06ea14): More refactoring of h2o start. Includes:
    - H2OStarter - a generic class to start H2O. It does all dynamic
      registration
    - H2OTestStarter - a generic class to start h2o-core tests
- [GitHub](https://github.com/h2oai/h2o-3/commit/8dd11df6e61779e731bcdc6a99684a5e69a7df45): Use typed key when it is necessary. Key.make() now returns typed Key<T extends Keyed>. The trick is that type T can be derived by left side of assignment. If it is not possible to derive type of the Key, then developer has to use typed syntax: `Key.<Frame>make("myframe.hex")` The change simplifies Scala code which will be able to derive type key.
- [#14757](https://github.com/h2oai/h2o-3/issues/14757): Add Job state and start/end time to the model's output [GitHub](https://github.com/h2oai/h2o-3/commit/5ffa988bdd2da4f5a0bc55ef6688d4f63d2c52c7)
- [GitHub](https://github.com/h2oai/h2o-3/commit/10d0f2c30ea3640e5a738bbf4d4adef46817c949): add more places to look when trying to start jar from python's h2o.init
- [GitHub](https://github.com/h2oai/h2o-3/commit/9a28bee6af37ec74c1973aab2ff7d99e257704b5): Cosmetic name changes
- [GitHub](https://github.com/h2oai/h2o-3/commit/c99aed8a1529f71883a73d7c793b10f9bf58df6d): Fetch local node differently from remote node.
- [GitHub](https://github.com/h2oai/h2o-3/commit/9d639ce17a030c2eeaa430ee26b27c416410a20a): Don't clamp node_idx at 0 anymore.
- [GitHub](https://github.com/h2oai/h2o-3/commit/ce677104991836f621c5f5702de476c714aed56d): Added -log_dir option.


#### Bug Fixes


##### API

- [#13764](https://github.com/h2oai/h2o-3/issues/13764): Schema.parse() needs to be better behaved (like, not crash)


##### Algorithms

- [#14689](https://github.com/h2oai/h2o-3/issues/14689): pca:glrm -  give bad results for attached data (bec of plus plus initialization)
- [GitHub](https://github.com/h2oai/h2o-3/commit/028688c8eefda072475857c9e282c5888c55f319): Fix deviance calculation, use the sanitized parameters from the model info, where Auto parameter values have been replaced with actual values
- [GitHub](https://github.com/h2oai/h2o-3/commit/13c7700d98acee69508e2585c8400f3877c141dc): Fix offset in DL for exponential family (that doesn't do standardization)
- [GitHub](https://github.com/h2oai/h2o-3/commit/2e89aad34fb0d5589d6a08ff347d95f323005e30): Fix a bug where initial Y was set to all zeroes by kmeans++ when scaling was disabled
- [#14633](https://github.com/h2oai/h2o-3/issues/14633): GBM: Math correctness for weights
- [#14746](https://github.com/h2oai/h2o-3/issues/14746): dl: deviance off for large dataset [GitHub](https://github.com/h2oai/h2o-3/commit/8ec0e558ea40b0b4575daff5b3b791040e6c6392)
- [#14632](https://github.com/h2oai/h2o-3/issues/14632): GBM: Math correctness for Offsets
- [#14741](https://github.com/h2oai/h2o-3/issues/14741): drf: reporting incorrect mse on validation set [GitHub](https://github.com/h2oai/h2o-3/commit/dad2c4ba5902d30122479d6fffa3bda76f8e1cec)
- [GitHub](https://github.com/h2oai/h2o-3/commit/e08a2480e8b44bbfaf6f700792da6288d1188320): Fix DRF scoring with 0 trees.

##### Python

- [#14238](https://github.com/h2oai/h2o-3/issues/14238): Python: Requires asnumeric() function
- [GitHub](https://github.com/h2oai/h2o-3/commit/a665a883dd41319941fa73599ac11559eeab0c3c): python interface: add folds_column to x, if it doesn't already exist in x
- [#14727](https://github.com/h2oai/h2o-3/issues/14727): Python : Math correctness tests for Tweedie/Gamma/Possion with offsets/weights
- [#14726](https://github.com/h2oai/h2o-3/issues/14726): Python : Deviance tests for all algos in python [GitHub](https://github.com/h2oai/h2o-3/commit/c9641faa4f2bdfbef850e6eb15a2195e157da0af)
- [#14636](https://github.com/h2oai/h2o-3/issues/14636): intermittent: pyunit_weights_api.py, hex.tree.SharedTree$ScoreBuildOneTree@645acd60java.lang.AssertionError    at hex.tree.DRealHistogram.scoreMSE(DRealHistogram.java:118), iris dataset [GitHub](https://github.com/h2oai/h2o-3/commit/cf886a78d3c34a8ef34f1e5149b4208281a927da)

##### R
- [#14235](https://github.com/h2oai/h2o-3/issues/14235): R: no is.numeric method for H2O objects
- [#14587](https://github.com/h2oai/h2o-3/issues/14587): NPE in water.api.RequestServer, water.util.RString.replace(RString.java:132)...got flagged as WARN in log...I would think we should have all NPE's be ERROR / fatal? or ?? [GitHub](https://github.com/h2oai/h2o-3/commit/49b68c06c9c85b737f4c2e98a7d5f8d4815da1f5)
- [#14621](https://github.com/h2oai/h2o-3/issues/14621): h2o.strsplit needs isNA check
- [#14064](https://github.com/h2oai/h2o-3/issues/14064): h2o.setTimezone NPE
- [#14702](https://github.com/h2oai/h2o-3/issues/14702): R: cloud name creation can't handle user names with spaces

##### System

- [#14384](https://github.com/h2oai/h2o-3/issues/14384): apply causes assert errors mentioning deadlock in runit_small_client_mode ...build never completes after hours ..deadlock?
- [#14173](https://github.com/h2oai/h2o-3/issues/14173): docker build fails
- [private-#344](https://github.com/h2oai/private-h2o-3/issues/344): Bug in /parsesetup data preview [GitHub](https://github.com/h2oai/h2o-3/commit/ee0b787ac50cbe747622adaec2344c243834f035)
- [#14730](https://github.com/h2oai/h2o-3/issues/14730): H2O xval: when delete all models: get Error evaluating future[6] :Error calling DELETE /3/Models/gbm_cv_13
- [#14731](https://github.com/h2oai/h2o-3/issues/14731): H2O: when list frames after removing most frames, get: roll ups not possible vec deleted error [GitHub](https://github.com/h2oai/h2o-3/commit/7cf1212e19b1f9ab8071718fb31d1565f7942fef)


##### Web UI

- [#14745](https://github.com/h2oai/h2o-3/issues/14745): Flow: View Data fails when there is a UUID column (and maybe also a String column)
- [#14733](https://github.com/h2oai/h2o-3/issues/14733): xval: cancel job does not work [GitHub](https://github.com/h2oai/h2o-3/commit/d05fc8d818d3ec5c3ea6f28f4c791a2d8d0871fc)


---

### Simons (3.0.1.3) - 7/24/15

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-simons/3/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-simons/3/index.html</a>

#### New Features

##### Python

- [#14698](https://github.com/h2oai/h2o-3/issues/14698): Add save and load model to python api
- [#14290](https://github.com/h2oai/h2o-3/issues/14290): Python needs "str" operator, like R's
- [GitHub](https://github.com/h2oai/h2o-3/commit/df27d5c46011647ddb6363431ca085cc4dba37a5): turn on `H2OFrame __repr__`

#### Enhancements

##### API

- [GitHub](https://github.com/h2oai/h2o-3/commit/d22b508c215fb6033ed11fe5009de744bd38f2d7): Increase sleep from 2 to 3 because h2o itself does a sleep 2 on the REST API before triggering the shutdown.

##### System

- [#14694](https://github.com/h2oai/h2o-3/issues/14694): Make export file a  job [GitHub](https://github.com/h2oai/h2o-3/commit/31cdef5b6a48f11b6568e0131fcb4e0acb06f5ad)


#### Bug Fixes

The following changes are to resolve incorrect software behavior:

##### Algorithms

- [#14707](https://github.com/h2oai/h2o-3/issues/14707): gbm poisson w weights: deviance off
- [#14700](https://github.com/h2oai/h2o-3/issues/14700): gbm poisson with offset: seems to be giving wrong leaf predictions

##### Python

- [#14695](https://github.com/h2oai/h2o-3/issues/14695): Python `get_frame()` results in deleting a frame created by Flow
- [private-#321](https://github.com/h2oai/private-h2o-3/issues/321): Split frame from python
- [private-#322](https://github.com/h2oai/private-h2o-3/issues/322): python client H2OFrame constructor puts the header into the data (as the first row)

##### R

- [#14473](https://github.com/h2oai/h2o-3/issues/14473): Runit intermittent fails : runit_pub_180_ddply.R
- [#14643](https://github.com/h2oai/h2o-3/issues/14643): Client mode jobs fail on runit_hex_1750_strongRules_mem.R

##### System

- [GitHub](https://github.com/h2oai/h2o-3/commit/9f83f68ffc70f164de51188c4837345a6a12ac13): Model parameters should be always public.

### Simons (3.0.1.1) - 7/20/15

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-simons/1/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-simons/1/index.html</a>

#### New Features

##### Algorithms

- [private-#467](https://github.com/h2oai/private-h2o-3/issues/467): Tweedie distributions for GBM [GitHub](https://github.com/h2oai/h2o-3/commit/a5892087d08bcee9b8c017bd6173601d262d9f79)
- [private-#468](https://github.com/h2oai/private-h2o-3/issues/468): Poisson distributions for GBM [GitHub](https://github.com/h2oai/h2o-3/commit/861322058519cc3455e924449cbe7dfdecf67514)
- [#14095](https://github.com/h2oai/h2o-3/issues/14095): properly test PCA and mark it non-experimental

##### Python

- [#14408](https://github.com/h2oai/h2o-3/issues/14408): Python needs "nlevels" operator like R
- [#14405](https://github.com/h2oai/h2o-3/issues/14405): Python needs "levels" operator, like R
- [#14331](https://github.com/h2oai/h2o-3/issues/14331): Python needs h2o.trim, like in R
- [#14330](https://github.com/h2oai/h2o-3/issues/14330): Python needs h2o.toupper, like in R
- [#14329](https://github.com/h2oai/h2o-3/issues/14329): Python needs h2o.tolower, like in R
- [#14327](https://github.com/h2oai/h2o-3/issues/14327): Python needs h2o.strsplit, like in R
- [#14323](https://github.com/h2oai/h2o-3/issues/14323): Python needs h2o.shutdown, like in R
- [#14319](https://github.com/h2oai/h2o-3/issues/14319): Python needs h2o.rep_len, like in R
- [#14316](https://github.com/h2oai/h2o-3/issues/14316): Python needs h2o.nlevels, like in R
- [#14314](https://github.com/h2oai/h2o-3/issues/14314): Python needs h2o.ls, like in R
- [#14320](https://github.com/h2oai/h2o-3/issues/14320): Python needs h2o.saveModel, like in R
- [#14313](https://github.com/h2oai/h2o-3/issues/14313): Python needs h2o.loadModel, like in R
- [#14311](https://github.com/h2oai/h2o-3/issues/14311): Python needs h2o.interaction, like in R
- [#14310](https://github.com/h2oai/h2o-3/issues/14310): Python needs h2o.hist, like in R
- [#14328](https://github.com/h2oai/h2o-3/issues/14328): Python needs h2o.sub, like in R
- [#14309](https://github.com/h2oai/h2o-3/issues/14309): Python needs h2o.gsub, like in R
- [#14312](https://github.com/h2oai/h2o-3/issues/14312): Python needs h2o.listTimezones, like in R
- [#14322](https://github.com/h2oai/h2o-3/issues/14322): Python needs h2o.setTimezone, like in R
- [#14308](https://github.com/h2oai/h2o-3/issues/14308): Python needs h2o.getTimezone, like in R
- [#14305](https://github.com/h2oai/h2o-3/issues/14305): Python needs h2o.downloadCSV, like in R
- [#14304](https://github.com/h2oai/h2o-3/issues/14304): Python needs h2o.downloadAllLogs, like in R
- [#14303](https://github.com/h2oai/h2o-3/issues/14303): Python needs h2o.createFrame, like in R
- [#14302](https://github.com/h2oai/h2o-3/issues/14302): Python needs h2o.clusterStatus, like in R
- [#14299](https://github.com/h2oai/h2o-3/issues/14299): Python needs svd algo
- [#14298](https://github.com/h2oai/h2o-3/issues/14298): Python needs prcomp algo
- [#14297](https://github.com/h2oai/h2o-3/issues/14297): Python needs naiveBayes algo
- [#14296](https://github.com/h2oai/h2o-3/issues/14296): Python needs model num_iterations accessor for clustering models, like R's
- [#14294](https://github.com/h2oai/h2o-3/issues/14294): Python needs screeplot and plot methods, like R's. (should probably check for matplotlib)
- [#14293](https://github.com/h2oai/h2o-3/issues/14293): Python needs multinomial model hit_ratio_table accessor, like R's
- [#14292](https://github.com/h2oai/h2o-3/issues/14292): Python needs model scoreHistory accessor, like R's
- [#14291](https://github.com/h2oai/h2o-3/issues/14291): R needs weights and biases accessors for deeplearning models
- [#14289](https://github.com/h2oai/h2o-3/issues/14289): Python needs "as.Date" operator, like R's
- [#14288](https://github.com/h2oai/h2o-3/issues/14288): Python needs "rbind" operator, like R's
- [#14321](https://github.com/h2oai/h2o-3/issues/14321): Python needs h2o.setLevel and h2o.setLevels, like in R
- [#14287](https://github.com/h2oai/h2o-3/issues/14287): Python needs "setLevel" operator, like R's
- [#14282](https://github.com/h2oai/h2o-3/issues/14282): Python needs "anyFactor" operator, like R's
- [#14281](https://github.com/h2oai/h2o-3/issues/14281): Python needs "table" operator, like R's
- [#14277](https://github.com/h2oai/h2o-3/issues/14277): Python needs "as.numeric" operator, like R's
- [#14276](https://github.com/h2oai/h2o-3/issues/14276): Python needs "as.character" operator, like R's
- [#14269](https://github.com/h2oai/h2o-3/issues/14269): Python needs "signif" operator, like R's
- [#14268](https://github.com/h2oai/h2o-3/issues/14268): Python needs "round" operator, like R's
- [#14267](https://github.com/h2oai/h2o-3/issues/14267): Python need transpose operator, like R's t operator
- [#14265](https://github.com/h2oai/h2o-3/issues/14265): Python needs element-wise division and multiplication operators, like %/% and %-%in R
- [#14306](https://github.com/h2oai/h2o-3/issues/14306): Python needs h2o.exportHDFS, like in R
- [#14333](https://github.com/h2oai/h2o-3/issues/14333): Python and R need which operator [GitHub](https://github.com/h2oai/h2o-3/commit/a39de4dce02e5516279f29cc6f1933a8bc2c5562)
- [#14332](https://github.com/h2oai/h2o-3/issues/14332): Python and R needs isnumeric and ischaracter operators
- [#14318](https://github.com/h2oai/h2o-3/issues/14318): Python needs h2o.removeVecs, like in R
- [#14300](https://github.com/h2oai/h2o-3/issues/14300): Python needs h2o.assign, like in R [GitHub](https://github.com/h2oai/h2o-3/commit/44faa7f15801b9218db6dfa84cde85baa56afb62)
- [#14272](https://github.com/h2oai/h2o-3/issues/14272): Python and R h2o clients need "any" operator, like R's
- [#14271](https://github.com/h2oai/h2o-3/issues/14271): Python and R h2o clients need "prod" operator, like R's
- [#14270](https://github.com/h2oai/h2o-3/issues/14270): Python and R h2o clients need "range" operator, like R's
- [#14266](https://github.com/h2oai/h2o-3/issues/14266): Python and R h2o clients need "cummax", "cummin", "cumprod", and "cumsum" operators, like R's
- [#14301](https://github.com/h2oai/h2o-3/issues/14301): Python needs h2o.clearLog, like in R
- [#14326](https://github.com/h2oai/h2o-3/issues/14326): Python needs h2o.startLogging and h2o.stopLogging, like in R
- [#14317](https://github.com/h2oai/h2o-3/issues/14317): Python needs h2o.openLog, like in R
- [#14324](https://github.com/h2oai/h2o-3/issues/14324): Python needs h2o.startGLMJob, like in R
- [#14307](https://github.com/h2oai/h2o-3/issues/14307): Python needs h2o.getFutureModel, like in R
- [#14278](https://github.com/h2oai/h2o-3/issues/14278): Python needs "match" operator, like R's
- [#14274](https://github.com/h2oai/h2o-3/issues/14274): Python needs "%in%" operator, like R's
- [#14286](https://github.com/h2oai/h2o-3/issues/14286): Python needs "scale" operator, like R's
- [#14273](https://github.com/h2oai/h2o-3/issues/14273): Python needs "all" operator, like R's
- [GitHub](https://github.com/h2oai/h2o-3/commit/fbe17d13d5dfe258ff7c62def3e4e3869a5d25d5): add start_glm_job() and get_future_model() to python client. add H2OModelFuture class. add respective pyunit

##### R

- [#14251](https://github.com/h2oai/h2o-3/issues/14251): Add h2oEnsemble R package to h2o-3
- [#14295](https://github.com/h2oai/h2o-3/issues/14295): R needs centroid_stats accessor like Python, for clustering models

##### Rapids

- [#14605](https://github.com/h2oai/h2o-3/issues/14605): the equivalent of R's "any" should probably implemented in rapids
- [#14604](https://github.com/h2oai/h2o-3/issues/14604): the equivalent of R's cummin, cummax, cumprod, cumsum should probably implemented in rapids
- [#14603](https://github.com/h2oai/h2o-3/issues/14603): the equivalent of R's "range" should probably implemented in rapids
- [#14602](https://github.com/h2oai/h2o-3/issues/14602): the equivalent of R's "prod" should probably implemented in rapids
- [#14663](https://github.com/h2oai/h2o-3/issues/14663): the equivalent of R's "unique" should probably implemented in rapids [GitHub](https://github.com/h2oai/h2o-3/commit/713b27f1c0ec4f879f3f39146acb2f888fd27d40)


##### System

- [GitHub](https://github.com/h2oai/h2o-3/commit/f738707830052bfa499d83ff91c29d2e7d13e113): changed to new AMI
- [#13671](https://github.com/h2oai/h2o-3/issues/13671): Create cross-validation holdout sets using the per-row weights
- [GitHub](https://github.com/h2oai/h2o-3/commit/3c7f296804d72b9b6940aaccc63f329383ab01fb): Add user_name. Add ExtensionHandler1.
- [GitHub](https://github.com/h2oai/h2o-3/commit/0ddf0740ad2e13a2a45137b4d017775900066244): Added auth options to h2o.init().
- [GitHub](https://github.com/h2oai/h2o-3/commit/0f9f71335e16a2632c3072782143f47657883129): Added H2O.calcNextUniqueModelId().
- [GitHub](https://github.com/h2oai/h2o-3/commit/bc059e063205fcf051f474f8d97ff3ffd90ee066): Add ldap arg.

##### Web UI

- [private-#449](https://github.com/h2oai/private-h2o-3/issues/449): Flow: Ability to change column type post-Parse

#### Enhancements

##### Algorithms

- [GitHub](https://github.com/h2oai/h2o-3/commit/b2d289b377dc7535344d40a36fdc47f5138545cf): use fixed seed to avoid bad splits with some seeds
- [GitHub](https://github.com/h2oai/h2o-3/commit/643cdce000d7d372bff6a1511d9bcd6695ddcf0d): Change seed to avoid type flip from integer to double after row slicing, which leads to different split decisions
- [GitHub](https://github.com/h2oai/h2o-3/commit/1fad4e3f0b8e30ffa9138d4adb9be645ff20d74b): Add option during kmeans scoring to return matrix of indicator columns for cluster assignment, which is necessary for initializing GLRM
- [GitHub](https://github.com/h2oai/h2o-3/commit/763ea02e1bc6e30f5e1a56437c0f374ac59e67a1): Output number of processed observations in PCA
- [GitHub](https://github.com/h2oai/h2o-3/commit/7d13f34e6dcd4a09871bd3c19d9724e2d2d80660): Add validation into PCA with GramSVD
- [GitHub](https://github.com/h2oai/h2o-3/commit/84ae7075b22fb1f480e7076cc1c276a41969043c): Code cleanup of distributions. Also rename _n_folds -> _nfolds for consistency
- [GitHub](https://github.com/h2oai/h2o-3/commit/a6716f9ab503d653189c328a287aaf5213f6d737): Remove restriction to data frames with more than 1 column
- [GitHub](https://github.com/h2oai/h2o-3/commit/650b599938945bb91ca07bbb860207709a394564): Add debugging output for DL auto-tuning.
- [#13537](https://github.com/h2oai/h2o-3/issues/13537): implement algo-agnostic cross-validation mechanism via a column of weights
- [GitHub](https://github.com/h2oai/h2o-3/commit/8001b5336960629b738da9df36261ca6c538e760): When initializing with kmeans++ set X to matrix of indicator columns corresponding to cluster assignments, unless closed form solution exists
- [GitHub](https://github.com/h2oai/h2o-3/commit/8c8fc92eb4a36cee5751597430687729f3527c60): Always print DL auto-tuning info for now.
- [#14623](https://github.com/h2oai/h2o-3/issues/14623): pca: would be good to remove the redundant std dev from flow pca model object

##### API

- [GitHub](https://github.com/h2oai/h2o-3/commit/eb68b384ff43a94f6dd0468b2bc4c67de6c23350): Set Content-Type: application/x-www-form-urlencoded for regular POST requests.
- [private-#417](https://github.com/h2oai/private-h2o-3/issues/417): Move `response_column` parameter above `ignored_columns` parameter [GitHub](https://github.com/h2oai/h2o-3/commit/522b45f1339eefc21b7b0a76e1d42a6cc77bcc00)
    - All of the fields of a schema are now stored in the leaf child of the class hierarchy. Changed the implementation of fields() to simply return the fields variable of a schema. The function calls `H2O.fail()` if it attempts to access a field from a non-leaf child. `response_column` is now moved above `ignored_columns` for every applicable schema. 'own_fields' is also now renamed to 'fields'
- [GitHub](https://github.com/h2oai/h2o-3/commit/11ae769255c2502ecb1ae7438752b2449210b580): Don't use features from servlet api 3.0 or later anymore. Instead save the response status in a thread local variable and fish it out when needed.

##### Python

- [GitHub](https://github.com/h2oai/h2o-3/commit/1e5ec4a3fe89979e634f080d3e2e96eb2bcec64c): don't use the header of the timezone table for a choice
- [GitHub](https://github.com/h2oai/h2o-3/commit/e40e0c68e2b5b4e2c0cbc68e0a43490c6847416a): never delete models. ever.
- [GitHub](https://github.com/h2oai/h2o-3/commit/0ef4c0d9ec9d9ba62d9603ea0aadaec9a5b50842): add na_rm argument
- [GitHub](https://github.com/h2oai/h2o-3/commit/76227f4c38fc3e3e8d57b3bbd585ae04901d119b): add prod to python interface

##### System

- [GitHub](https://github.com/h2oai/h2o-3/commit/b2c6486fe67c9e4042330e83a8e1475d29217082): use Key instead of Vec in refcnter
- [GitHub](https://github.com/h2oai/h2o-3/commit/a940c528b6cb4db6fdd087c15077d42584ae179a): protect vecs in apply
- [GitHub](https://github.com/h2oai/h2o-3/commit/34d3d92feeec72e3122661393a839388afcb2a6c): Allows for more than one column to remain unnamed. The new naming will fill in the blanks.
- [GitHub](https://github.com/h2oai/h2o-3/commit/bc4f64e2f6b43a60665f711b758d7874e25f34af): Refactoring of hadoop mapper and driver.
- [GitHub](https://github.com/h2oai/h2o-3/commit/49c8c767caf1fce2b373977019c758570ee34959): Remove -hdfs option.
- [GitHub](https://github.com/h2oai/h2o-3/commit/2640b20b5a3567d65faed9b1090a6f846711ab23): Adds more checks for a parse cancel at more stages during the post ingestion file parse.
- [GitHub](https://github.com/h2oai/h2o-3/commit/29db2e5655bf29e2065dc95267588fb23d583293): Refactor method name for clarification.
- [GitHub](https://github.com/h2oai/h2o-3/commit/7ae285df8f448c248219784b9b0d16d513850e6f): Cleans up and comments the freeing of chunks from a parsed file.
- [GitHub](https://github.com/h2oai/h2o-3/commit/8b0f30fefb573273675f829dfb24073aa22540a3): Since more startup logic is getting added, simplify H2OClientApp as much as possible. Remove H2OClient entirely.
- [GitHub](https://github.com/h2oai/h2o-3/commit/100fbf07ff9874aebe620e962721e8d2c547cb1a): Add dedicated AddCommonResponseHeadersHandler handler to set common response headers up-front.
- [GitHub](https://github.com/h2oai/h2o-3/commit/1af5a632d5eafc56df1bb451a9f94ac21cb5a357): More refactoring of startup. Pushed a bunch of code from H2OApp into H2O. Added H2O.configureLogging().
- [GitHub](https://github.com/h2oai/h2o-3/commit/0047eea9b28d1842f8326917eafd41fcd655c988): Make Progress extend Keyed.
- [GitHub](https://github.com/h2oai/h2o-3/commit/4a0eda9a4fd798367a5133894459b7d165f89232): Make createServer() protected.
- [GitHub](https://github.com/h2oai/h2o-3/commit/2428fa3a6bc8178d5dfde323438acb29cc12a032): model_id should probably be a Key<Model>, not Key<Frame>.
- [GitHub](https://github.com/h2oai/h2o-3/commit/1778487a41aa4ccc76b97c437fc1b7625784116a): Change Jetty version from 9 to 8 to get Java 6 compatibility back.

##### Web UI

- [#14490](https://github.com/h2oai/h2o-3/issues/14490): show REST API and overall UI response times for each cell in Flow
- [private-#394](https://github.com/h2oai/private-h2o-3/issues/394): Flow: Emphasize run time in job-progress output
- [#14491](https://github.com/h2oai/h2o-3/issues/14491): show wall-clock start and run times in the Flow outline
- [#14671](https://github.com/h2oai/h2o-3/issues/14671): Hook up "Export" button for datasets (frames) in Flow.


#### Bug Fixes

##### Algorithms

- [#14608](https://github.com/h2oai/h2o-3/issues/14608): gbm w poisson: get  java.lang.AssertionError' at hex.tree.gbm.GBM$GBMDriver.buildNextKTrees on attached data
- [#14637](https://github.com/h2oai/h2o-3/issues/14637): kmeans: get AIOOB with user specified centroids [GitHub](https://github.com/h2oai/h2o-3/commit/231e33b42b5408ec4e664f7f614a8f37aabbab10)
    -  Throw an error if the number of rows in the user-specified initial centers is not equal to k.
- [#14620](https://github.com/h2oai/h2o-3/issues/14620): pca: gram-svd std dev differs for v2 vs v3 for attached data
- [GitHub](https://github.com/h2oai/h2o-3/commit/42831143c9b208596fa60f3d8f86c5bd1109ec64): Fix DL
- [GitHub](https://github.com/h2oai/h2o-3/commit/19794673a5e2a5cf4b5f5d550f4184266ae8799a): Fix a bug in PCA utilities for k = 1
- [#14664](https://github.com/h2oai/h2o-3/issues/14664): nfolds: flow-when set nfold =1 job hangs  for ever; in terminal get java.lang.AssertionError
- [#14670](https://github.com/h2oai/h2o-3/issues/14670): GBM/DRF: is balance_classes=TRUE and nfolds>1 valid? [GitHub](https://github.com/h2oai/h2o-3/commit/5f82d3b5f24f11f3a62823b139bd3dd0f44f6c44)
- [#13794](https://github.com/h2oai/h2o-3/issues/13794): GLM => `runit_demo_glm_uuid.R` : water.exceptions.H2OIllegalArgumentException
- [#14660](https://github.com/h2oai/h2o-3/issues/14660): Client (model-build) is blocked when passing illegal nfolds value. [GitHub](https://github.com/h2oai/h2o-3/commit/456fe73a8b120351fbfd5e3a21963439c00cd630)
- [#14654](https://github.com/h2oai/h2o-3/issues/14654): Cross Validation: if nfolds > number of observations, should it default to leave-one-out cross-validation?
- [#14509](https://github.com/h2oai/h2o-3/issues/14509): pca: on airlines get  java.lang.AssertionError at hex.svd.SVD$SVDDriver.compute2(SVD.java:219) [GitHub](https://github.com/h2oai/h2o-3/commit/0923c60d47cd089bda1163feeb425e3f2d7e586c)
- [#14573](https://github.com/h2oai/h2o-3/issues/14573): pca: glrm giving very different std dev than R and h2o's other methods for attached data
- [GitHub](https://github.com/h2oai/h2o-3/commit/0f38e2f0c732095b005dc75cbb0b5b6c04c0b031): Fix a potential race condition in tree validation scoring.
- [GitHub](https://github.com/h2oai/h2o-3/commit/6cfbfdfac28205ba99f3911eaf39ab154fc1cd76): Fix GLM parameter schema. Clean up hasOffset() and hasWeights()


##### Python

- [#14597](https://github.com/h2oai/h2o-3/issues/14597): column name missing (python client)
- [#14599](https://github.com/h2oai/h2o-3/issues/14599): python client's tail() header incorrect [GitHub](https://github.com/h2oai/h2o-3/commit/a5055880e9f2f527e99a9811e695170c5c5e00dc)
- [#14387](https://github.com/h2oai/h2o-3/issues/14387): intermittent assertion errors in `pyunit_citi_bike_small.py/pyunit_citi_bike_large.py`. Client apparently not notified
- [#14560](https://github.com/h2oai/h2o-3/issues/14560): "Trying to unlock null" assertion during `pyunit_citi_bike_large.py`
- [#14374](https://github.com/h2oai/h2o-3/issues/14374): match operator should take numerics

##### R

- [#14628](https://github.com/h2oai/h2o-3/issues/14628): R CMD Check failures [GitHub](https://github.com/h2oai/h2o-3/commit/d707fa0b56c9bc8d8e43861f5c690c1e8aaad809)
- [#14659](https://github.com/h2oai/h2o-3/issues/14659): R CMD Check failing on running examples [GitHub](https://github.com/h2oai/h2o-3/commit/b650fb588a3c9d8e8e524db4154a0fd72112fec6)
- [#14685](https://github.com/h2oai/h2o-3/issues/14685): R: group_by causes h2o to hang on multinode cluster
- [#14470](https://github.com/h2oai/h2o-3/issues/14470): Python and R h2o clients need "unique" operator, like R's [GitHub - R](https://github.com/h2oai/h2o-3/commit/90423fa68058d68524efb2306fe5a7b272ccd964) [GitHub - Python](https://github.com/h2oai/h2o-3/commit/a53f6913f3431732f6c854b7fc3b15c3ce11171b)
- [#14675](https://github.com/h2oai/h2o-3/issues/14675): is.numeric in R interface faulty [GitHub](https://github.com/h2oai/h2o-3/commit/fbb44071f7cd19b43a7ac40a8a0652d2010363ea)
- [#14683](https://github.com/h2oai/h2o-3/issues/14683): Intermittent: `runit_deeplearning_autoencoder_large.R` : gets wrong answer?
- [#14652](https://github.com/h2oai/h2o-3/issues/14652): 2 nfolds tests fail intermittently: `runit_RF_iris_nfolds.R` and `runit_GBM_cv_nfolds.R` [GitHub](https://github.com/h2oai/h2o-3/commit/9095d1e5d52de27e0622f7ac309d1afbcf09aefb)
- [#14682](https://github.com/h2oai/h2o-3/issues/14682): Intermittent: `runit_deeplearning_anomaly_large.R `: training slows down to 0 samples/ sec [GitHub](https://github.com/h2oai/h2o-3/commit/d43d47014ff812744c2522dad27246a5e6014738)

##### Rapids

- [#14677](https://github.com/h2oai/h2o-3/issues/14677): Rapids ASTAll faulty [GitHub](https://github.com/h2oai/h2o-3/commit/3f9e71ef6ad8251b1854a7c05a9945bc629df327)


##### Sparkling Water

- [#14534](https://github.com/h2oai/h2o-3/issues/14534): Migration to Spark 1.4

##### System

- [#14526](https://github.com/h2oai/h2o-3/issues/14526): Parser: Multifile Parse fails with 0-byte files in directory [GitHub](https://github.com/h2oai/h2o-3/commit/e95f0e4d20c2281e61009349c63e73520fcf30a2)
- [private-#375](https://github.com/h2oai/private-h2o-3/issues/375): Empty reply when parsing dataset with mismatching header and data column length
- [#14478](https://github.com/h2oai/h2o-3/issues/14478): Split frame : Big datasets : On 186K rows 3200 Cols split frame took 40 mins => which is too long
- [#14409](https://github.com/h2oai/h2o-3/issues/14409): Column naming can create duplicate column names
- [#14085](https://github.com/h2oai/h2o-3/issues/14085): NPE in Rollupstats after failed parse
- [#14122](https://github.com/h2oai/h2o-3/issues/14122): H2O parse: When cancel a parse job, key remains locked and hence unable to delete the file [GitHub](https://github.com/h2oai/h2o-3/commit/c2a110fb0a44173eb8549acff7ec51a9a23b64ad)
- [GitHub](https://github.com/h2oai/h2o-3/commit/44af14bdd8b249943dd72405090515f425c3b720): client mode deadlock issue resolution
- [#14635](https://github.com/h2oai/h2o-3/issues/14635): Client mode fails consistently sometimes : `GBM_offset_tweedie.R.out.txt`  :
- [GitHub](https://github.com/h2oai/h2o-3/commit/efb80e43867276dd6b4f64fc3cc7b3978383627a): nbhm bug: K == TOMBSTONE not key == TOMBSTONE
- [GitHub](https://github.com/h2oai/h2o-3/commit/6240c43a88f5a5aae92eb71c5bc1e568bc791977): Pulls out a GAID from resource in jar if the GAID doesn't equal the default. Presumably the GAID has been changed by the jar baking program.

##### Web UI

- [#13860](https://github.com/h2oai/h2o-3/issues/13860): Flows : Not able to load saved flows from hdfs/local [GitHub](https://github.com/h2oai/h2o-3/commit/4ea4ffe400512636919964b901e38581c65a68b7)
- [#13560](https://github.com/h2oai/h2o-3/issues/13560): Flow:Parse two different files simultaneously, flow should either complain or fill the additional (incompatible) rows with nas
- [#14496](https://github.com/h2oai/h2o-3/issues/14496): missing .java extension when downloading pojo [GitHub](https://github.com/h2oai/h2o-3/commit/c41e81d4b5daa1d214aeb6695c1095c72e8ada85)
- [#14609](https://github.com/h2oai/h2o-3/issues/14609): Changing columns type takes column list back to first page of columns
- [#14477](https://github.com/h2oai/h2o-3/issues/14477): Flow : Import file => Parse => Error compiling coffee-script Maximum call stack size exceeded
- [#14576](https://github.com/h2oai/h2o-3/issues/14576): Flow :=> Cannot save flow on hdfs
- [#14496](https://github.com/h2oai/h2o-3/issues/14496): missing .java extension when downloading pojo
- [#14619](https://github.com/h2oai/h2o-3/issues/14619): Flow: the column names do not modify when user changes the dataset in model builder

---

### Shannon (3.0.0.26) - 7/4/15

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-shannon/26/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-shannon/26/index.html</a>

#### New Features

##### Algorithms

- [#14562](https://github.com/h2oai/h2o-3/issues/14562): Expose standardization shift/mult values in the Model output in R/Python. [GitHub](https://github.com/h2oai/h2o-3/commit/af6fb8fa9ca2d75bf45fb5eb130720a76f5ed324)


##### Python

- [GitHub](https://github.com/h2oai/h2o-3/commit/fd19d6b21a35338c481455f3ca0974cc98c4957d): add h2o.shutdown to python client
- [GitHub](https://github.com/h2oai/h2o-3/commit/ce3a94cba9c00ae6beb9e45870fad9c7e0dbb575): add h2o.hist and respective pyunit
- [GitHub](https://github.com/h2oai/h2o-3/commit/ea8073d78276da011ed525f4654472d23e94e5cb): gbm weight pyunit (variable importances)

##### R

- [private-#334](https://github.com/h2oai/private-h2o-3/issues/334): Github home for R demos


##### Web UI

- [#13212](https://github.com/h2oai/h2o-3/issues/13212): Change data type in flow
- [#14254](https://github.com/h2oai/h2o-3/issues/14254): Flow needs as.factor and as.numeric after parse


#### Enhancements

##### Algorithms

- [#14463](https://github.com/h2oai/h2o-3/issues/14463): GBM : Weights math correctness tests in R
- [#14492](https://github.com/h2oai/h2o-3/issues/14492): GLM w tweedie: for attached data, R giving much better res dev than h2o
- [#14370](https://github.com/h2oai/h2o-3/issues/14370): Offsets/Weights: Math correctness for GLM
- [#14465](https://github.com/h2oai/h2o-3/issues/14465): RF : Weights Math correctness tests in R
- [private-#343](https://github.com/h2oai/private-h2o-3/issues/343): remove weights option from DRF and GBM in REST API, Python, R
- [#14527](https://github.com/h2oai/h2o-3/issues/14527): Threshold in GLM is hardcoded to 0
- [GitHub](https://github.com/h2oai/h2o-3/commit/dc379b117cc5f26c38ae276aba82b6bb3d0fef2b): Make min_rows a double instead of int: Is now weighted number of observations (min_obs in R).
- [GitHub](https://github.com/h2oai/h2o-3/commit/7cf9ba765c0fe8f1394439db69bc2aa54e004b75): Don't use sample weighted variance, but full weighted variance.
- [GitHub](https://github.com/h2oai/h2o-3/commit/bf9838e84f527b52756de45a752bd321a62ba6e4): Fix R^2 computation.
- [GitHub](https://github.com/h2oai/h2o-3/commit/b9cccbe02017a01167afed5ca1a64198d499fa0b): Skip rows with missing response in weighted mean computation.
- `_binomial_double_trees` disabled by default for DRF (was enabled).
- [GitHub](https://github.com/h2oai/h2o-3/commit/25d6735b0b621b0ce67c67d96b5113e03eb045f1): Relax tolerance.
- [private-#371](https://github.com/h2oai/private-h2o-3/issues/371) : Offset for GBM
- [private-#469](https://github.com/h2oai/private-h2o-3/issues/469) : Tweedie distributions for GLM


##### API

- [#14460](https://github.com/h2oai/h2o-3/issues/14460): generated REST API POJOS should be compiled and jar'd up as part of the build
- [GitHub](https://github.com/h2oai/h2o-3/commit/1c5df7bb74238433699b23d7b8be6bcd0ba9f4e7): Change schema for PCA, SVD, and GLRM to version 99

##### Python

- [GitHub](https://github.com/h2oai/h2o-3/commit/7295701c1b1fa45817aab2fba39d209f37185d6b): is factor returns TRUE/FALSE cast to scalar 1/0
- [GitHub](https://github.com/h2oai/h2o-3/commit/4f932f4775ce7844114e84e9cfd8086c06cffb96): take a slightly different syntactic approach to dropping column
- [GitHub](https://github.com/h2oai/h2o-3/commit/c001961bf39a75e9d44b3b98a5567ca13aa09b85): better list comp in interaction call
- [GitHub](https://github.com/h2oai/h2o-3/commit/82b8f9bc3a13bb5ac3eb5885ef3637dac05262ea): if `weights_column` argument is specified, attach the column to the training and/or validation frame (if not already specified as part of x/validation_x). if weights_column is not already part of x/validation_x, then a training_frame/validation_frame needs to be provided and the weights column is taken from here. respective pyunit added

##### R

- [GitHub](https://github.com/h2oai/h2o-3/commit/62937f80722011a07dc07d6c32c95fbe3c64ba7c): better ref handling in the [<- for python and R
- [GitHub](https://github.com/h2oai/h2o-3/commit/231632b832c85305b92098a24ec87cba7af013fc): Pass binomial_double_trees in the R wrapper for DRF.
- [GitHub](https://github.com/h2oai/h2o-3/commit/bc2b9c2f073822dba4442a61e2fcf26bf2257b66): carefully format NAs and non NAs
- [GitHub](https://github.com/h2oai/h2o-3/commit/ca70709db3576f5ee641d6e1ddc3c4877212d400): for loop over the x[[j]] to format NAs properly
- [GitHub](https://github.com/h2oai/h2o-3/commit/818fb9a8df0c210acde4051948f83475f20a628a): Added example to h2o-r/ensemble/create_h2o_wrappers.R

##### System

- [GitHub](https://github.com/h2oai/h2o-3/commit/b3b7dab9fe7cf7ef7dab0a7dc08985c028183f4a): allow for no y in model_builder
- [GitHub](https://github.com/h2oai/h2o-3/commit/c1b302914157c55ad7ef778ec49e07e01b03e79d): Enable auto-flag for Java6 generation.
- [GitHub](https://github.com/h2oai/h2o-3/commit/ac1a079e968e24b7f12471406b74ebb5c3785ac0): better compression in split frame
- [#14564](https://github.com/h2oai/h2o-3/issues/14564): All basic file accessors in PersistHDFS should check file permissions
- [#14487](https://github.com/h2oai/h2o-3/issues/14487): getFrames should show a Parse button for raw frames


##### Web UI

- [#14518](https://github.com/h2oai/h2o-3/issues/14518): Flow => Build model => ignored columns table => should have column width resizing based on column names width => looks odd if column names are short
- [#14519](https://github.com/h2oai/h2o-3/issues/14519): Flow: Build model => Search for 1 column => select it  => build model shows list of columns instead of 1 column
- [#14232](https://github.com/h2oai/h2o-3/issues/14232): Flow: Add Impute

#### Bug Fixes


##### Algorithms

- [#14528](https://github.com/h2oai/h2o-3/issues/14528): dl with offset: when offset same as response, do not get 0 mse
- [#14529](https://github.com/h2oai/h2o-3/issues/14529): h2oR: dl with offset giving : Error in args$x_ignore : object of type 'closure' is not subsettable
- [#14458](https://github.com/h2oai/h2o-3/issues/14458): gbm weights: give different terminal node predictions than R for attached data
- [#14540](https://github.com/h2oai/h2o-3/issues/14540): Investigate effectiveness of _binomial_double_trees (DRF) [GitHub](https://github.com/h2oai/h2o-3/commit/88dc897d69ce3e8f83ebbb7bd1d68cee2a0437a0)
- [#14545](https://github.com/h2oai/h2o-3/issues/14545): Actually pass 'binomial_double_trees' argument given to R wrapper to DRF.
- [#14415](https://github.com/h2oai/h2o-3/issues/14415): DL: h2o.saveModel cannot save metrics when a deeplearning model has a validation_frame
- [#14550](https://github.com/h2oai/h2o-3/issues/14550): GBM test time predictions without weights seem off when training with weights [GitHub](https://github.com/h2oai/h2o-3/commit/e4e260fa1ac5a856152bb3ceadcfffe53ee7c138)
- [#14505](https://github.com/h2oai/h2o-3/issues/14505): GLM: doubled weights should produce the same result as doubling the observations [GitHub](https://github.com/h2oai/h2o-3/commit/e302509a1db68d2695026a201e5128a66bb066f3)
- [#14503](https://github.com/h2oai/h2o-3/issues/14503): GLM: it appears that observations with 0 weights are not ignored, as they should be.
- [GitHub](https://github.com/h2oai/h2o-3/commit/b4f82be57c4f8b6e00be095a08eb6fd34f40dbed): Fix a bug in PCA scoring that was handling categorical NAs inconsistently
- [#14552](https://github.com/h2oai/h2o-3/issues/14552): Regression 3060 fails on GLRM in R tests
- [#14556](https://github.com/h2oai/h2o-3/issues/14556): change Grid endpoints and schemas to v99 since they are still in flux
- [#14559](https://github.com/h2oai/h2o-3/issues/14559): GLM : build model => airlinesbillion dataset => IRLSM/LBFGS => fails with array index out of bound exception
- [#14577](https://github.com/h2oai/h2o-3/issues/14577): gbm w offset: predict seems to be wrong
- [#14570](https://github.com/h2oai/h2o-3/issues/14570): Frame name creation fails when file name contains csv or zip (not as extension)
- [#14548](https://github.com/h2oai/h2o-3/issues/14548): DL predictions on test set require weights if trained with weights
- [#14568](https://github.com/h2oai/h2o-3/issues/14568): Flow: After running pca when call get Model/ jobs get: Failed to find schema for version: 3 and type: PCA
- [#14547](https://github.com/h2oai/h2o-3/issues/14547): Test variable importances for weights for GBM/DRF/DL
- [#14486](https://github.com/h2oai/h2o-3/issues/14486): With R, deep learning autoencoder using all columns in frame, not just those specified in x parameter
- [#14563](https://github.com/h2oai/h2o-3/issues/14563): dl var importance:there is a .missing(NA) variable in Dl variable importnce even when data has no nas


##### Python

- [#14510](https://github.com/h2oai/h2o-3/issues/14510): h2o.save_model fails on windoz due to path nonsense
- [GitHub](https://github.com/h2oai/h2o-3/commit/27d3e1f1258a3ac1224b1a2dc5b58fa340d9d301): python leaked key check for Vecs, Chunks, and Frames
- [#14579](https://github.com/h2oai/h2o-3/issues/14579): frame dimension mismatch between upload/import method

##### R

- [#14571](https://github.com/h2oai/h2o-3/issues/14571): h2o.loadModel() from hdfs
- [#14581](https://github.com/h2oai/h2o-3/issues/14581): R CMD Check failing on : The Date field is over a month old.

##### System

- [#14483](https://github.com/h2oai/h2o-3/issues/14483): Large number of columns (~30000) on importFile (flow) is slow / unresponsive for long time
- [#13829](https://github.com/h2oai/h2o-3/issues/13829): Split frame : Flow should not show raw frames for SplitFrame dialog (water.exceptions.H2OIllegalArgumentException)
- [#14430](https://github.com/h2oai/h2o-3/issues/14430): bug in GLM POJO: seems threshold for binary predictions is always 0
- [#14538](https://github.com/h2oai/h2o-3/issues/14538): Cannot save model on windows since Key contains '@' (illegal character to path)
- [GitHub](https://github.com/h2oai/h2o-3/commit/7ad8406f895172da23b2e79a94a295ebc0fbea87): Fixes the timezone lists.
- [GitHub](https://github.com/h2oai/h2o-3/commit/923db4ff6ddd6efb49ac7ce07a5d4226e9ceb4b7): R CMD check fix for date
- [GitHub](https://github.com/h2oai/h2o-3/commit/30b4e51c1f13d6a7b89e67c81ef08b138a2b08cd): add ec2 back into project

##### Web UI

- [private-#597](https://github.com/h2oai/private-h2o-3/issues/597): Flow : Import file 100k.svm => Something went wrong while displaying page


---

### Shannon (3.0.0.25) - 6/25/15

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-shannon/25/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-shannon/25/index.html</a>


#### Enhancements

##### API

- [#14423](https://github.com/h2oai/h2o-3/issues/14423): branch 3.0.0.2 to REGRESSION_REST_API_3 and cherry-pick the /99/Rapids changes to it

### ##Web UI

- [#14518](https://github.com/h2oai/h2o-3/issues/14518): Flow => Build model => ignored columns table => should have column width resizing based on column names width => looks odd if column names are short
- [#14519](https://github.com/h2oai/h2o-3/issues/14519): Flow : Build model => Search for 1 column => select it  => build model shows list of columns instead of 1 column

#### Bug Fixes

The following changes are to resolve incorrect software behavior:

##### Algorithms

- [#14458](https://github.com/h2oai/h2o-3/issues/14458): gbm weights: give different terminal node predictions than R for attached data
- [GitHub](https://github.com/h2oai/h2o-3/commit/f17dc5e033ffb0ebd7e8fe16f37bca24aec197a4): Fix offset for DL.
- [GitHub](https://github.com/h2oai/h2o-3/commit/f1547e6a0497519646358bc39c73cf25c7935919): Gracefully handle 0 weight for GBM.

##### Python

- [#14522](https://github.com/h2oai/h2o-3/issues/14522): Weights API: weights column not found in python client


##### R

- [GitHub](https://github.com/h2oai/h2o-3/commit/b9bf679f27baec53cd5e5a46202b4e58cc0108f8): Fix R wrapper for DL for weights/offset.

##### Web UI

- [#14500](https://github.com/h2oai/h2o-3/issues/14500): Flow model builder: the na filter does not select all ignored columns; just the first 100.


---

### Shannon (3.0.0.24) - 6/25/15

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-shannon/24/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-shannon/24/index.html</a>

#### New Features

##### Algorithms

- [GitHub](https://github.com/h2oai/h2o-3/commit/cd7011b4810f11316a06fe33df0fd7d540268bce): Allow validation for unsupervised models.


##### R

- [GitHub](https://github.com/h2oai/h2o-3/commit/2a22657e5788be6b7b85362923c3de02ae4c0b16): Added runit GBM weights
- [GitHub](https://github.com/h2oai/h2o-3/commit/8f0b9dc95a155fbdf9a373a9181c80a3eb3e1ed6): Updated runit_GBM_weights.R

##### Python

- [GitHub](https://github.com/h2oai/h2o-3/commit/0606097d95a7accce3460a73daec463ad7ea4165): add h2o.set_timezone h2o.get_timezone and h2o.list_timezones to python client and respective pyunit.
- [GitHub](https://github.com/h2oai/h2o-3/commit/1eabf6db7cb45166ea6fbd01997b52eb41c6079d): add h2o.save_model and h2o.load_model to python client and respective pyunit


#### Enhancements


##### Algorithms

- [GitHub](https://github.com/h2oai/h2o-3/commit/8b646239e9433c719d390b03ba475715cf3b4f5e): Skip rows with weight 0.
- [GitHub](https://github.com/h2oai/h2o-3/commit/c6f11a9069d3553694dee3e33f574f482567613d): x_ignore must be set when autoencoder is TRUE


##### System

- [GitHub](https://github.com/h2oai/h2o-3/commit/c11201060001be2da98fb101dbdd8ffbe18e85bf): Fix Java bindings generator to generate code under project's location.
- [GitHub](https://github.com/h2oai/h2o-3/commit/8768ba503b79d7f485c7c757f056dc170c9aca45): Adds input parameter check to ParseSetup.

#### Bug Fixes



##### Algorithms

- [#14501](https://github.com/h2oai/h2o-3/issues/14501): dl with ae: get ava.lang.UnsupportedOperationException: Trying to predict with an unstable model.
- [GitHub](https://github.com/h2oai/h2o-3/commit/b5869fce2ff51c5afbad397324e220942f0490c3): Bring back accidentally removed hiding of classification-related fields for unsupervised models.

##### API

- [#14427](https://github.com/h2oai/h2o-3/issues/14427): fix REST API POJO generation for enums, + java.util.map import

---

### Shannon (3.0.0.23) - 6/19/15

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-shannon/23/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-shannon/23/index.html</a>

#### New Features

##### Algorithms

- [private-#617](https://github.com/h2oai/private-h2o-3/issues/617): Offset for GLM
- [private-#631](https://github.com/h2oai/private-h2o-3/issues/631): Add observation weights to GLM (was private-#631)
- [#13669](https://github.com/h2oai/h2o-3/issues/13669): Add observation weights to all metrics
- [#13667](https://github.com/h2oai/h2o-3/issues/13667): Pass a weight Vec as input to all algos
- [private-#629](https://github.com/h2oai/private-h2o-3/issues/629): Add observation weights to GBM
- [private-#630](https://github.com/h2oai/private-h2o-3/issues/630): Add observation weights to DL
- [private-#625](https://github.com/h2oai/private-h2o-3/issues/625): Add observation weights to DRF
- [#13164](https://github.com/h2oai/h2o-3/issues/13164): Add observation weights to GLM, GBM, DRF, DL (classification)
- [private-#370](https://github.com/h2oai/private-h2o-3/issues/370): Support Offsets for DL [GitHub](https://github.com/h2oai/h2o-3/commit/c6d6dc953c477aeaa5fed3c40af1b5583f590386)
- [GitHub](https://github.com/h2oai/h2o-3/commit/e72ba587c0aace574b0f600f0e3c72c2f551df80): Use weights/offsets in GBM.


##### API

- [#13076](https://github.com/h2oai/h2o-3/issues/13076): do back-end work to allow document navigation from one Schema to another
- [#13117](https://github.com/h2oai/h2o-3/issues/13117): doing summary means calling it with each columns name, index not supported?


##### Python

- [GitHub](https://github.com/h2oai/h2o-3/commit/220c71470e40de92e7c5a94833ce71bb8addcd00): add num_iterations accessor to python client and respective pyunit
- [GitHub](https://github.com/h2oai/h2o-3/commit/4206cda35543bef6ff8c930a3a66a5bfe01d30ba): add score_history accessor to python client and respective pyunit
- [GitHub](https://github.com/h2oai/h2o-3/commit/709afea1e9d3edef1ebaead58d33aeb6bdc08da3): add hit ratio table accessor to python interface and respective pyunit
- [GitHub](https://github.com/h2oai/h2o-3/commit/04b4d82345d10578070ca3cbd558dab82b09807b): add h2o.naivebayes and respective pyunits
- [GitHub](https://github.com/h2oai/h2o-3/commit/46571731b0ad92829b8775a62e93bb7a51307b4e): add h2o.prcomp and respective pyunits.
- [#13673](https://github.com/h2oai/h2o-3/issues/13673): Add user-given input weight parameters to Python
- [GitHub](https://github.com/h2oai/h2o-3/commit/483fe5c8dc8d80a93c8ff2688221e3eb802d92cf): add h2o.create_frame to python client and respective pyunit
- [GitHub](https://github.com/h2oai/h2o-3/commit/f1b0c315cdafa0bca76330e65cfca3462db29dc7): add h2o.interaction and respective pyunit
- [GitHub](https://github.com/h2oai/h2o-3/commit/fba655b02f6c33a0d5c4d74de25fa21b96d7c364): add h2o.strplit to python client and respective pyunit
- [GitHub](https://github.com/h2oai/h2o-3/commit/09bec2687d32c717ea8e86331591acf1ba75b67a): add h2o.toupper and h2o.tolower to python client and respective pyunit
- [GitHub](https://github.com/h2oai/h2o-3/commit/d370a2f4dc2aa73f6df7139d13c705b00d30ce1a): add h2o.sub and h2o.gsub to python interface and respective pyunit
- [GitHub](https://github.com/h2oai/h2o-3/commit/5496d10baa1ca1cb420f198b0a98e81cdbe000ec): add h2o.trim() to python client and respective pyunit
- [GitHub](https://github.com/h2oai/h2o-3/commit/dfe0e8ed44e1c64824779d1cbc2322c159668a46): add h2o.rep_len to python client and respective pyunit
- [GitHub](https://github.com/h2oai/h2o-3/commit/4cde35ac1dc3b96833206860c974e4ad9d099d27): add h2o.svd to python client and respective golden pyunit
- [GitHub](https://github.com/h2oai/h2o-3/commit/e45ea385eea19f5c5964d96e5acae8c7c7b201b9): add scree plot functionality to python client and respective pyunit
- [GitHub](https://github.com/h2oai/h2o-3/commit/d583778e72cde01f73fbfaa30d10f8e3d3a6ab0e): add plotting functionality to python client and respective pyunit

##### R

- [GitHub](https://github.com/h2oai/h2o-3/commit/edf3cfafc49307226425c38a4c5bef6fdd5ed7a9): added h2o.weights and h2o.biases accessors to R client and update respective runit
- [GitHub](https://github.com/h2oai/h2o-3/commit/06413c20912e36d41a9a158e28cbb697f4542d34): add h2o.centroid_stats to R client and respective runit
- [#13672](https://github.com/h2oai/h2o-3/issues/13672): Add user-given input weight parameters to R
- [GitHub](https://github.com/h2oai/h2o-3/commit/3c5a80edcfe274294d43c837e7d6abb2216834e4): Add offset/weights to DRF/GBM R wrappers.


##### Web UI

- [#14482](https://github.com/h2oai/h2o-3/issues/14482): Add cancelJob() routine to Flow


#### Enhancements

##### Algorithms

- [#13668](https://github.com/h2oai/h2o-3/issues/13668): Use the user-given weight Vec as observation weights for all algos
- [GitHub](https://github.com/h2oai/h2o-3/commit/42abac7758390f5f7b0b59fadddb0b07294d238e): Refactor the code to let the caller compute the weighted sigma.
- [GitHub](https://github.com/h2oai/h2o-3/commit/1025e08abff8200c4106b4a46aaf2175dfee6734): Modify prior class distribution to be computed from weighted response.
- [GitHub](https://github.com/h2oai/h2o-3/commit/eec4f863fc198adaf774314897bd8d3fb8df411e): Put back the defaultThreshold that's based on training/validation metrics. Was accidentally removed together with SupervisedModel.
- [GitHub](https://github.com/h2oai/h2o-3/commit/a9f5261991f96a511f1cf8d0863a9c9b1c14caf0): Always sample to at least #class labels when doing stratified sampling.
- [GitHub](https://github.com/h2oai/h2o-3/commit/4e30718943840d0bf8cde77d95d0211034ece15b): Cutout for NAs in GLM score0(data[],...), same as for score0(Chunk[],…)


##### R

- [#13844](https://github.com/h2oai/h2o-3/issues/13844): All h2o things in R should have an `h2o.something` version so it's unambiguous [GitHub](https://github.com/h2oai/h2o-3/commit/e488674502f3d02e853ecde93497c327f91ddad6)
- [GitHub](https://github.com/h2oai/h2o-3/commit/b99163db673b29eaa187d261a28365f80c0efdb9): export clusterIsUp and clusterInfo commands
- [GitHub](https://github.com/h2oai/h2o-3/commit/b514dd703904097feb1f0f6a8dc732948da5f4ec): update accessors in the shim
- [GitHub](https://github.com/h2oai/h2o-3/commit/62ef0590b1bb5b231ea98a3aea8a358bda9631b5): gbm with async exec


##### System

- [private-#345](https://github.com/h2oai/private-h2o-3/issues/345): Wide frame handling for model builders
- [GitHub](https://github.com/h2oai/h2o-3/commit/f408e1a3306c7b7768bbfeae1d3a90edfc583039): Remove application plugin from assembly to speedup build process.
- [GitHub](https://github.com/h2oai/h2o-3/commit/c3e91c670b69698d94dcf123511dc595b2d61927): add byteSize to ls
- [GitHub](https://github.com/h2oai/h2o-3/commit/ac10731a5695f699a45a8bfbd807a42432c82aec): option to launch randomForest async
- [GitHub](https://github.com/h2oai/h2o-3/commit/d986952c6328b8b831a20231bffa129067e3cc03): Return HDFS persist manager for URIs starting with s3n and s3a
- [GitHub](https://github.com/h2oai/h2o-3/commit/889a6573d6ff05616dc7a8d578e83444d64df184): quote strings when writing to disk


#### Bug Fixes

##### Algorithms

- [#14195](https://github.com/h2oai/h2o-3/issues/14195): pca: when cancel the job the key remains locked
- [#14439](https://github.com/h2oai/h2o-3/issues/14439): Error in GBM if response column is constant [GitHub](https://github.com/h2oai/h2o-3/commit/5c9bfa7d72107baff323e255930e0b461498f744)
- [#14447](https://github.com/h2oai/h2o-3/issues/14447): dl with obs weights: nas in weights cause  'java.lang.AssertionError [GitHub](https://github.com/h2oai/h2o-3/commit/d296d7429d3a6b53ac12497b2c344ef6387c94e7)
- [#14429](https://github.com/h2oai/h2o-3/issues/14429): pca: data with nas, v2 vs v3 slightly different results [GitHub](https://github.com/h2oai/h2o-3/commit/c289d8731f99bf096975df2839f0223886dfef33)
- [#14448](https://github.com/h2oai/h2o-3/issues/14448): dl w/obs wts: when all wts are zero, get java.lang.AssertionError [GitHub](https://github.com/h2oai/h2o-3/commit/cf3e5e4fdf94bd278105b2bbca0d6e106913577e)
- [GitHub](https://github.com/h2oai/h2o-3/commit/959fe1d845db59086a9980c0baa97dbdccbe41c8): Fix check for offset (allow offset for logistic regression).
- [GitHub](https://github.com/h2oai/h2o-3/commit/10174b8a2199578b475a5588405db434026d4fe8): Gracefully handle exception when launching single-node DRF/GBM in client mode.
- [GitHub](https://github.com/h2oai/h2o-3/commit/a082d08f36d1e0b7cafd9492711feb0d5772f697): Hack around the fact that hasWeights()/hasOffset() isn't available on remote nodes and that SharedTree is sent to remote nodes and its private internal classes need access to the above methods...
- [GitHub](https://github.com/h2oai/h2o-3/commit/11b276c478944cac3a7010c6d9ee3871d83d1b71): Fix scoring when NAs are predicted.

##### Python

- [#14440](https://github.com/h2oai/h2o-3/issues/14440): pyunit_citi_bike_large.py : test failing consistently on regression jobs
- [#14443](https://github.com/h2oai/h2o-3/issues/14443): Regression job : Pyunit small tests groupie and pub_444_spaces failing consistently
- [#14346](https://github.com/h2oai/h2o-3/issues/14346): Regression of pyunit_small,  Groupby.py
- [#14360](https://github.com/h2oai/h2o-3/issues/14360): intermittent fail in pyunit_citi_bike_small.py: -Unimplemented- failed lookup on token
- [#14442](https://github.com/h2oai/h2o-3/issues/14442): pyunit_citi_bike_small.py : failing consistently on regression jobs
- [#14437](https://github.com/h2oai/h2o-3/issues/14437): matplotlib.pyplot import failure on MASTER jenkins pyunit small jobs [GitHub](https://github.com/h2oai/h2o-3/commit/b2edebe88984ff71782c6524c7d5e4cb18fb1f11)
- [GitHub](https://github.com/h2oai/h2o-3/commit/0bfbee20cf3fc9321ba9f7c22bb0914c7f830cd9): minor fix to python's h2o.create_frame
- [GitHub](https://github.com/h2oai/h2o-3/commit/e5b7ad8515999b90f841219357ebf03d2caccfce): update the path to jar in connection.py

##### R

- [#14446](https://github.com/h2oai/h2o-3/issues/14446): Client mode failed tests : runit_GBM_one_node.R, runit_RF_one_node.R, runit_v_3_apply.R, runit_v_4_createfunctions.R [GitHub](https://github.com/h2oai/h2o-3/commit/f270c3c99931f211303046c5bc2b36db004a170b)
- [#14213](https://github.com/h2oai/h2o-3/issues/14213): Split Frame causes AIOOBE on Chicago crimes data [GitHub](https://github.com/h2oai/h2o-3/commit/869926304eecb4be2e0d64c6d1fbf43e37a62cb6)
- [#13736](https://github.com/h2oai/h2o-3/issues/13736): runit_demo_NOPASS_h2o_impute_R : h2o.impute() is missing. seems like we want that?
- [#13575](https://github.com/h2oai/h2o-3/issues/13575): H2O-R-  does not give the full column summary
- [#14444](https://github.com/h2oai/h2o-3/issues/14444): Regression : Runit small jobs failing on tests :
- [#13731](https://github.com/h2oai/h2o-3/issues/13731): runit_NOPASS_pub-668 R tests uses all() ...h2o says all is unimplemented
- [#14475](https://github.com/h2oai/h2o-3/issues/14475): R: h2o.ls() needs to return data sizes
- [#14407](https://github.com/h2oai/h2o-3/issues/14407): Intermitent runit fail : runit_GBM_ecology.R [GitHub](https://github.com/h2oai/h2o-3/commit/ccc11bc30a68bf82028b83d7e53e43d48cd67c50)
- [#14435](https://github.com/h2oai/h2o-3/issues/14435): R: toupper/tolower don't work [GitHub](https://github.com/h2oai/h2o-3/commit/26d7a37e50714a2ad025acbb561f2c7e52b1b9cb) [GitHub](https://github.com/h2oai/h2o-3/commit/8afa2ff12b0a3343dd902f3912f9f9d509e775ca)
- [#14172](https://github.com/h2oai/h2o-3/issues/14172): R: dataset is imported but can't return head of frame

##### Sparkling Water

- [#13958](https://github.com/h2oai/h2o-3/issues/13958): Download page for Sparkling Water should point to the right R-client and Python client
- [#14400](https://github.com/h2oai/h2o-3/issues/14400): Sparkling water => Flow => Million song/KDD Cup path issues [GitHub](https://github.com/h2oai/h2o-3/commit/9cd11646e5ea4b1e4ea4120ebe7ece8d049140a7)

##### Web UI
- [#14404](https://github.com/h2oai/h2o-3/issues/14404): Flow UI: Change Help > FAQ link to h2o-docs/index.html#FAQ



---

### Shannon (3.0.0.22) - 6/13/15

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-shannon/22/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-shannon/22/index.html</a>


### #New Features

### ##API

- [#13625](https://github.com/h2oai/h2o-3/issues/13625): Generate Java bindings for REST API: POJOs for the entities (schemas)

### ##Python

- [GitHub](https://github.com/h2oai/h2o-3/commit/9ab55a5e612af9d807f069863a50667dd6970484): added h2o.anyfactor() and respective pyunit
- [GitHub](https://github.com/h2oai/h2o-3/commit/6fa028bab1eb81800dabc0167b05bb7a4fc12731): add h2o.scale and respective pyunit
- [GitHub](https://github.com/h2oai/h2o-3/commit/700fbfff9a835a964e6d62ea1c52853160e11902): added levels, nlevels, setLevel and setLevels and respective pyunit...#14405 #14405 #14405 #14405 #14405
- [GitHub](https://github.com/h2oai/h2o-3/commit/688da52517ab5582fbb6527c03950dbb365ce037): add H2OFrame.as_date and pyunit addition. H2OFrame.setLevel should return a H2OFrame not a H2OVec.

### #Enhancements


### ##Algorithms

- [GitHub](https://github.com/h2oai/h2o-3/commit/34f4110ac2d5a7fb6b47f1919851ade2e9f8f279): Add `_build_tree_one_node` option to GBM

### ## API

- [private-#353](https://github.com/h2oai/private-h2o-3/issues/353): Additional attributes on /Frames and /Frames/foo/summary


### ##R

- [#13697](https://github.com/h2oai/h2o-3/issues/13697): Release h2o-dev to CRAN
- Adding parameter `parse_type` to upload/import file [(GitHub)](https://github.com/h2oai/h2o-3/commit/7074685e0cea8ea956c98ebd02883045b52df63b)

### ##Python

- [GitHub](https://github.com/h2oai/h2o-3/commit/9c697c0bc55195ae13365250b587efd49fd9cace): print out where h2o jar is looked for
- [GitHub](https://github.com/h2oai/h2o-3/commit/8e16c258324223a492ef9b39003082709b5715fa):add h2o.ls and respective pyunit


### ##System

- [#13708](https://github.com/h2oai/h2o-3/issues/13708): refector the duplicated code in FramesV2
- [#14258](https://github.com/h2oai/h2o-3/issues/14258): Add horizontal pagination of frames to Flow [GitHub](https://github.com/h2oai/h2o-3/commit/5b9f0b84e79aa4dc09c7350c9b37c27d954b4c14)
- [#13599](https://github.com/h2oai/h2o-3/issues/13599): Add Xmx reporting to GA
- [GitHub](https://github.com/h2oai/h2o-3/commit/60a6e5d6705e04a2d8b7a6e45e13ae8a34013587):Added support for Freezable[][][] in serialization (added addAAA to auto buffer and DocGen, DocGen will just throw H2O.fail())
- [GitHub](https://github.com/h2oai/h2o-3/commit/75f6a6c87e943d2222597755788c8d9a23e8013f): No longer set yyyy-MM-dd and dd-MMM-yy dates that precede the epoch to be NA. Negative time values are fine. This unifies these two time formats with the behavior of as.Date.
- [GitHub](https://github.com/h2oai/h2o-3/commit/0bb1e10b5c888a7fd1274991348ea214302728db): Reduces the verbosity of parse tracing messages.
- [GitHub](https://github.com/h2oai/h2o-3/commit/04566d7a9efd15418885e46f3db9eba1d52b04d8): Rename AUTO->GUESS for figuring out file type.

### ## Web UI

- [private-#413](https://github.com/h2oai/private-h2o-3/issues/413): Add frame pagination
- [#14379](https://github.com/h2oai/h2o-3/issues/14379): Flow : Decision to be made on display of number of columns for wider datasets for Parse and Frame summary
- [#14378](https://github.com/h2oai/h2o-3/issues/14378): Usability improvements
- [#13258](https://github.com/h2oai/h2o-3/issues/13258): "View Data" display may need to be modified/shortened.


### #Bug Fixes


### ##Algorithms

- [#14339](https://github.com/h2oai/h2o-3/issues/14339): GLM: Buggy when likelihood equals infinity
- [#14368](https://github.com/h2oai/h2o-3/issues/14368): GLM: Some offsets hang
- [#14246](https://github.com/h2oai/h2o-3/issues/14246): GLM: get java.lang.AssertionError at hex.glm.GLM$GLMSingleLambdaTsk.compute2 for attached data
- [#14377](https://github.com/h2oai/h2o-3/issues/14377): pca: h2o-3 reporting incorrect proportion of variance and cum prop [GitHub](https://github.com/h2oai/h2o-3/commit/1c874f38b4927b3f9d2a30560dc697a78a2bfe13)
- [private-#408](https://github.com/h2oai/private-h2o-3/issues/408): GLM - beta constraints with categorical variables fails with AIOOB
- [private-#409](https://github.com/h2oai/private-h2o-3/issues/409): GLM - gradient not within tolerance when specifying beta_constraints w/ and w/o prior values



### ## Python

- [#14398](https://github.com/h2oai/h2o-3/issues/14398): Class Cast Exception ValStr to ValNum [GitHub](https://github.com/h2oai/h2o-3/commit/7ed0befac7b47e867c017e4a52f9e4036e5f2aad)
- [#14394](https://github.com/h2oai/h2o-3/issues/14394): python client parse fail on hdfs /datasets/airlines/airlines.test.csv
- [#14133](https://github.com/h2oai/h2o-3/issues/14133): Demo: Airlines Demo in Python [GitHub](https://github.com/h2oai/h2o-3/commit/8f82d1de294c83f2fa9f2ab9e05ab0f829b8ec7f)
- [#14261](https://github.com/h2oai/h2o-3/issues/14261): Python ifelse on H2OFrame never finishes
- [#14406](https://github.com/h2oai/h2o-3/issues/14406): Run.py modify to accept phantomjs timeout command line option [GitHub](https://github.com/h2oai/h2o-3/commit/d720f4441e26bcd2715fb40b14370013a126d7c0)

### ## R

- [#14134](https://github.com/h2oai/h2o-3/issues/14134): Demo: Chicago Crime Demo in R
- [#14218](https://github.com/h2oai/h2o-3/issues/14218): Merge causes IllegalArgumentException
- [#14418](https://github.com/h2oai/h2o-3/issues/14418): R: no argument parser_type in h2o.uploadFile/h2o.importFile [(GitHub)](https://github.com/h2oai/h2o-3/commit/b7a608d0031b25b23b869fdf9b0dd7ab4dc78fc6)


### ## System

- [#14396](https://github.com/h2oai/h2o-3/issues/14396): Phantomjs : Add timeout command line option
- [#14375](https://github.com/h2oai/h2o-3/issues/14375): Flow : Import file 15 M Rows 2.2K cols=> Parse these files => Change first column type => Unknown => Try to change other columns => Kind of hangs
- [#14380](https://github.com/h2oai/h2o-3/issues/14380): make the ParseSetup / Parse API more efficient for high column counts [GitHub](https://github.com/h2oai/h2o-3/commit/4cc459401afbd7598d5c9a79a4b858237d91fc4f)


---

### Shannon (3.0.0.21) - 6/12/15

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-shannon/21/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-shannon/21/index.html</a>

#### New Features

##### Python
- [private-#656](https://github.com/h2oai/private-h2o-3/issues/656): The ability to define features as categorical or continuous in the web UI and in the python API


#### Enhancements


##### Algorithms

- [GitHub](https://github.com/h2oai/h2o-3/commit/d39f0c885a02c9dafcabc78d4a108f7c8465eb32) Made intercept option public and added it to field list in parameter schema
- [GitHub](https://github.com/h2oai/h2o-3/commit/3ee8264d549f5cc62fe29e1b50e7e3d67ac6dde2) GLM: Updated null model intercept fit.
- [GitHub](https://github.com/h2oai/h2o-3/commit/74e7f6de084c69bdb54093d19fdb089401359ca2) GLM: Updated null-model constant term fitting when running with offset
- [GitHub](https://github.com/h2oai/h2o-3/commit/75903da9d1d1da3f34eb64024c1ff7d6955536de)  glm update
- [GitHub](https://github.com/h2oai/h2o-3/commit/3a6fab716dbc9a98106076c7a02d91291c1da88c) DL code refactoring to reduce file sizes

##### Python

- [GitHub](https://github.com/h2oai/h2o-3/commit/865e78d3b129df7dd776aca9d0a8d8824aab8287) add h2o.round() and h2o.signif() and additional pyunit checks
- [GitHub](https://github.com/h2oai/h2o-3/commit/c70f5a7b8eac16a0d00e1959346cab6ea28991e1) add h2o.all() and respective pyunit checks

##### R

- [GitHub](https://github.com/h2oai/h2o-3/commit/b25b2b3ce97ba1f87146850a472dc7166e1ef694) added intercept option top R


##### System

- [#13599](https://github.com/h2oai/h2o-3/issues/13599): Add Xmx reporting to GA [GitHub](https://github.com/h2oai/h2o-3/commit/ba6ce79f679efb49ac4e77a462cc1bb080f9c64b)

##### Web UI

- [GitHub](https://github.com/h2oai/h2o-3/commit/385e0c2a2d008c3ee347441b0ab840c3a819c8b2) Add horizontal pagination of /Frames to handle UI navigation of wide datasets more efficiently.
- [GitHub](https://github.com/h2oai/h2o-3/commit/d9c9a202a2f254ab004787e27d3bfe716ee76198) Only show the top 7 metrics for the max metrics table
- [GitHub](https://github.com/h2oai/h2o-3/commit/157c15833bdb8096e9e6f52286a6e557c59b8561) Make the max metrics table entries be called `max f1` etc.


#### Bug Fixes

The following changes are to resolve incorrect software behavior:


##### Algorithms

- [#14339](https://github.com/h2oai/h2o-3/issues/14339): GLM: Buggy when likelihood equals infinity [GitHub](https://github.com/h2oai/h2o-3/commit/d9512529da2e4feed0d2d8847626a0c85e385cbe)
- [#14368](https://github.com/h2oai/h2o-3/issues/14368): GLM: Some offsets hang
- [#14246](https://github.com/h2oai/h2o-3/issues/14246): GLM: get java.lang.AssertionError at hex.glm.GLM$GLMSingleLambdaTsk.compute2 for attached data
- [#14356](https://github.com/h2oai/h2o-3/issues/14356): pca: giving wrong std- dev for mentioned data
- [#14357](https://github.com/h2oai/h2o-3/issues/14357): pca: std dev numbers differ for v2 and v3 for attached data [GitHub](https://github.com/h2oai/h2o-3/commit/3b02b5b1dcc9c6401823f3f0ba00e1578e3b4826)
- [#14355](https://github.com/h2oai/h2o-3/issues/14355): GBM, RF: get an NPE when run with a validation set with no response [GitHub](https://github.com/h2oai/h2o-3/commit/fe4dd15b6931bc92065884a36969d7bd519e5ec0)
- [GitHub](https://github.com/h2oai/h2o-3/commit/900929e99eeda7c885a984fe8bcc790c0339dbbe) GLM fix - fixed fitting of null model constant term
- [GitHub](https://github.com/h2oai/h2o-3/commit/fdebe7ade04cb1e0bf1c488e268815c46cbf2052) Fix remote bug
- [GitHub](https://github.com/h2oai/h2o-3/commit/2e679013a5c2a39023240f38890690658411be08) Remove elastic averaging parameters from Flow.
- [#14372](https://github.com/h2oai/h2o-3/issues/14372): pca: predictions on the attached data from v2 and v3 differ


##### Python

- [#14261](https://github.com/h2oai/h2o-3/issues/14261): Python ifelse on H2OFrame never finishes [GitHub](https://github.com/h2oai/h2o-3/commit/e64909d307aaa80524ed38d25fc9152af9594879)

##### R

- [#13750](https://github.com/h2oai/h2o-3/issues/13750): Save model and restore model (from R)
- [#14214](https://github.com/h2oai/h2o-3/issues/14214): h2o-r/tests/testdir_misc/runit_mergecat.R failure (client mode only)

##### System

- [#14376](https://github.com/h2oai/h2o-3/issues/14376): move Rapids to /99 since it's going to be in flux for a while [GitHub](https://github.com/h2oai/h2o-3/commit/cc908d2bc16f270e190f889cb4e67a3884b2ac74)
- [GitHub](https://github.com/h2oai/h2o-3/commit/ea61945a2185e3201ec23aed4bebeaa86f2cc05a) Fixes an operator precedence issue, and replaces debug GA target with actual one.
- [GitHub](https://github.com/h2oai/h2o-3/commit/40d13b4bb8c2342ac4022e3017490e651b6c9a9b) Fix log download bug where all nodes were getting the same zip file.



---

### Shannon (3.0.0.18) - 6/9/15

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-shannon/18/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-shannon/18/index.html</a>

#### New Features

##### System

- [#14143](https://github.com/h2oai/h2o-3/issues/14143): implement h2o1-style model save/restore in h2o-3 [GitHub](https://github.com/h2oai/h2o-3/commit/204695c288d5d8fd833274461c3ee6ef19a65711)

##### Python

- [GitHub](https://github.com/h2oai/h2o-3/commit/baa8ac01fc93dac36b519ffbada962665c1ba802): Added --h2ojar option


#### Enhancements


##### Python

- [#13289](https://github.com/h2oai/h2o-3/issues/13289): Make python equivalent of as.h2o() work for numpy array and pandas arrays


#### Bug Fixes

##### Algorithms

- [#14345](https://github.com/h2oai/h2o-3/issues/14345): pca: get java.lang.AssertionError at hex.svd.SVD$SVDDriver.compute2(SVD.java:198)
- [#14350](https://github.com/h2oai/h2o-3/issues/14350): pca: predictions from h2o-3 and h2o-2 differs for attached data
- [#14354](https://github.com/h2oai/h2o-3/issues/14354): DL: when try to access the training frame from the link in the dl model get: Object not found

##### R

- [#13750](https://github.com/h2oai/h2o-3/issues/13750): Save model and restore model (from R) [GitHub](https://github.com/h2oai/h2o-3/commit/391ba5ba296aeb4b50ebf5658d90ab87f4bb2d49)


---

### Shannon (3.0.0.17) - 6/8/15

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-shannon/17/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-shannon/17/index.html</a>

#### New Features


##### Algorithms

- [private-#471](https://github.com/h2oai/private-h2o-3/issues/471):Poisson distributions for GLM

##### Python

- [#14248](https://github.com/h2oai/h2o-3/issues/14248): Python Interface needs H2O Cut Function [GitHub](https://github.com/h2oai/h2o-3/commit/f67341a2fa1b59d8365c9cf1600b21c85343ce03)
- [#14220](https://github.com/h2oai/h2o-3/issues/14220): Need equivalent of as.Date feature in Python [GitHub](https://github.com/h2oai/h2o-3/commit/99430c1fb5921365d9a2242f4c4d02a751e9e024)
- [#14145](https://github.com/h2oai/h2o-3/issues/14145): H2O Python needs Modulus Operations
- [private-#656](https://github.com/h2oai/private-h2o-3/issues/656): The ability to define features as categorical or continuous in the web UI and in the python API
- [#14215](https://github.com/h2oai/h2o-3/issues/14215): environment variable to disable the strict version check in the R and Python bindings


##### Web UI

- [#14153](https://github.com/h2oai/h2o-3/issues/14153): Flow: Good interactive confusion matrix for binomial
- [#14154](https://github.com/h2oai/h2o-3/issues/14154): Flow: Good confusion matrix for multinomial

#### Enhancements

##### Algorithms

- [GitHub](https://github.com/h2oai/h2o-3/commit/c308d5e2bed30378ea9e0032831e73ad4bc09f7a): GLM weights fix: regularize by sum of weights rather than number of observations
- [GitHub](https://github.com/h2oai/h2o-3/commit/6f23ac2ed1d3b36652e4b7d8ecf80c68a0b2e37c): GLM fix: added line search (and limited number of iterations) to constant term model fitting with offset (could enter infinite loop)
- [GitHub](https://github.com/h2oai/h2o-3/commit/fb4a82dbf74c49f9c55d5653dd8e2e038f9a998b): No longer warn if `binomial_double_trees` option is enabled for `_nclass`!=2
- [GitHub](https://github.com/h2oai/h2o-3/commit/ed7ec99e3f1ebb4c92ac21ea4016369a9f5b85d6): Fix CM table to have integer entries unless there are real-valued entries
- [GitHub](https://github.com/h2oai/h2o-3/commit/acbaa4806e70e0fb2868e7bedfba6eda21469424): Add extra assertion for `train_samples_per_iteration`
- [GitHub](https://github.com/h2oai/h2o-3/commit/1f3c110915b8c281df19cd21b0f09571fa30e618): Update model during runtime of algorithm.
- [GitHub](https://github.com/h2oai/h2o-3/commit/67c894f6af9064bdcfa9feb877b3f5abd1fc22db): Changes to glm forloop to add offsets and add NOPASS/NOFEATURE functionality back to run.py


##### R

- [GitHub](https://github.com/h2oai/h2o-3/commit/ab7cf2948c70b68de07e48d346d8e9c264263a16): month was off by one, runit test edited
- [GitHub](https://github.com/h2oai/h2o-3/commit/e450d67f2d4e7fefa79e63f92c1fd9212f463a8d): Comments to clarify the policy on dates in H2O.


##### System

- [private-#360](https://github.com/h2oai/private-h2o-3/issues/360): Logs should include JVM launch parameters


##### Web UI

- [#13459](https://github.com/h2oai/h2o-3/issues/13459): Show Frames for DL weights/biases in Flow
- [#14199](https://github.com/h2oai/h2o-3/issues/14199): add a "I like this" style button with LinkedIn or Github (beside the Flow Assist Me button)
- [#14223](https://github.com/h2oai/h2o-3/issues/14223): Flow: use new `_exclude_fields` query parameter to speed up REST API usage

#### Bug Fixes


##### Algorithms

- [#14325](https://github.com/h2oai/h2o-3/issues/14325): GLM: model with weights different in R than in H2o for attached data
- [#14264](https://github.com/h2oai/h2o-3/issues/14264): GLM: when run with -ive weights, would be good to tell the user that -ive weights not allowed instead of throwing exception
- [#14242](https://github.com/h2oai/h2o-3/issues/14242): GLM: reporting incorrect null deviance [GitHub](https://github.com/h2oai/h2o-3/commit/8117c82014db5a5da461f3051a84fdda0de56fcc)
- [#14336](https://github.com/h2oai/h2o-3/issues/14336): GLM: when run with weights and offset get wrong ans
- [#14241](https://github.com/h2oai/h2o-3/issues/14241): GLM: name ordering for the coefficients is incorrect [GitHub](https://github.com/h2oai/h2o-3/commit/368d649246a019a629f020bfd69f3e0bf7d8983c)
- [#14239](https://github.com/h2oai/h2o-3/issues/14239): pca: wrong std dev for data with nas rest numeric cols [GitHub](https://github.com/h2oai/h2o-3/commit/ac0b63e86934bfa363a539fcf5760d40ca10ed7a)
- [#14196](https://github.com/h2oai/h2o-3/issues/14196): pca: progress bar not showing progress just the initial and final progress status [GitHub](https://github.com/h2oai/h2o-3/commit/519f2326587efd7bcea7fb8459e2883bfd0915db)
- [#14182](https://github.com/h2oai/h2o-3/issues/14182): pca: from flow when try to invoke build model, displays-ERROR FETCHING INITIAL MODEL BUILDER STATE
- [#14190](https://github.com/h2oai/h2o-3/issues/14190): pca: with enum column reporting (some junk) wrong stdev/ rotation [GitHub](https://github.com/h2oai/h2o-3/commit/91b1a954c6f9959bf4aabc440bef4c917bf649ee)
- [#14206](https://github.com/h2oai/h2o-3/issues/14206): pca: no std dev getting reported for attached data
- [#14211](https://github.com/h2oai/h2o-3/issues/14211): pca: std dev for attached data differ when run on  h2o-3 and h2o-2
- [#14236](https://github.com/h2oai/h2o-3/issues/14236): h2o.glm with offset column: get Error in .h2o.startModelJob(conn, algo, params) :    Offset column 'logInsured' not found in the training frame.

##### R

- [#14212](https://github.com/h2oai/h2o-3/issues/14212): h2o.setTimezone throwing an error [GitHub](https://github.com/h2oai/h2o-3/commit/6dbecb2707b9483218d40a95d717729db72f587b)
- [#14207](https://github.com/h2oai/h2o-3/issues/14207): R: Most GLM accessors fail [GitHub](https://github.com/h2oai/h2o-3/commit/790c5f4850362712e39b83c4489f315fc5ca89b8)
- [#14205](https://github.com/h2oai/h2o-3/issues/14205): R: Cannot extract an enum value using data[row,col] [GitHub](https://github.com/h2oai/h2o-3/commit/7704a16e510458c3e4b7cf539fb35bd93163dcde)
- [private-#365](https://github.com/h2oai/private-h2o-3/issues/365): Feature engineering: log (1+x) fails [GitHub](https://github.com/h2oai/h2o-3/commit/ab13a9229a7fb93eace068efe3f99eb248359fa7)
- [#14229](https://github.com/h2oai/h2o-3/issues/14229): h2o.glm: no way to specify offset or weights from h2o R [GitHub](https://github.com/h2oai/h2o-3/commit/9245e785614f9748344990df5000ebeec3bea398)
- [#14233](https://github.com/h2oai/h2o-3/issues/14233): create_frame: hangs with following msg in the terminal, java.lang.IllegalArgumentException: n must be positive
- [#14335](https://github.com/h2oai/h2o-3/issues/14335): runit_hex_1841_asdate_datemanipulation.R fails intermittently [GitHub](https://github.com/h2oai/h2o-3/commit/b95e094c45630eca07d2f99e5a27ac9409603e28)
- [#14335](https://github.com/h2oai/h2o-3/issues/14335): runit_hex_1841_asdate_datemanipulation.R fails intermittently


##### Sparkling Water

- [#13683](https://github.com/h2oai/h2o-3/issues/13683): Upgrade SparklingWater to Spark 1.3


##### System

- [#14263](https://github.com/h2oai/h2o-3/issues/14263): Confusion Matrix: class java.lang.ArrayIndexOutOfBoundsException', with msg '2' java.lang.ArrayIndexOutOfBoundsException: 2 at hex.ConfusionMatrix.createConfusionMatrixHeader [Github](https://github.com/h2oai/h2o-3/commit/63efca9d0a1a30074cc78bde90c564f9b8c766ff)
- [private-#377](https://github.com/h2oai/private-h2o-3/issues/377): SVMLight Parse Bug [GitHub](https://github.com/h2oai/h2o-3/commit/f60e97f4f913769de5a36b34a802057e7bb68db2)
- [#14185](https://github.com/h2oai/h2o-3/issues/14185): implement JSON field-filtering features: `_exclude_fields`
- [GitHub](https://github.com/h2oai/h2o-3/commit/c7892ce1a3bcb7f745f80f5f3fd5d7a14bc1f345): Fix a missing field update in Job.
- [#13081](https://github.com/h2oai/h2o-3/issues/13081): Handling of strings columns in summary is broken
- [#14208](https://github.com/h2oai/h2o-3/issues/14208): Parse: get AIOOB when parses the attached file with first two cols as enum while h2o-2 does fine
- [#14351](https://github.com/h2oai/h2o-3/issues/14351): Get AIOOBE when parsing a file with fewer column names than columns [GitHub](https://github.com/h2oai/h2o-3/commit/17b975b11c91ffd33d42b8b087e691e5f4ba0416)
- [#14338](https://github.com/h2oai/h2o-3/issues/14338): Variable importance Object


##### Web UI

- [#14176](https://github.com/h2oai/h2o-3/issues/14176): Flow: Selecting "Cancel" for "Load Notebook" prompt clears current notebook anyway
- [#14151](https://github.com/h2oai/h2o-3/issues/14151): Model builder takes forever to load the column names in Flow, hence cannot build any models
- [#14228](https://github.com/h2oai/h2o-3/issues/14228): Flow GLM: from Flow the drop down with column names does not show up and hence not able to select the offset column
- [#14354](https://github.com/h2oai/h2o-3/issues/14354): DL: when try to access the training frame from the link in the dl model get: Object not found [GitHub](https://github.com/h2oai/h2o-3/commit/dee129ce73d24a54107f9634cc3fcfa18a783664)



---

### Shannon (3.0.0.13) - 5/30/15

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-shannon/13/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-shannon/13/index.html</a>

#### New Features


##### Algorithms

- [private-#655](https://github.com/h2oai/private-h2o-3/issues/655): Add Random Forests for regression [GitHub](https://github.com/h2oai/h2o-3/commit/66b1b67ba212445607615f2db65d96d87ac6029c)

##### Python

- [#14146](https://github.com/h2oai/h2o-3/issues/14146): Converting H2OFrame into Python object
- [#14145](https://github.com/h2oai/h2o-3/issues/14145): H2O Python needs Modulus Operations

##### R

- [#14166](https://github.com/h2oai/h2o-3/issues/14166): Merge should handle non-numeric columns [(github)](https://github.com/h2oai/h2o-3/commit/3ef148bd93c053c06eeb8414bc9290a394d082f8)
- [#14076](https://github.com/h2oai/h2o-3/issues/14076): R: add weekdays() function in addition to month() and year()


#### Enhancements

##### Algorithms

- [github](https://github.com/h2oai/h2o-3/commit/09e5d53b6b1b3a1bfb45b6e5a12e1a05d877102f): Updated weights handling, test.
- [private-#376](https://github.com/h2oai/private-h2o-3/issues/376)poor GBM performance on KDD Cup 2009 competition dataset [(github)](https://github.com/h2oai/h2o-3/commit/36b99ed538218d8f675266f97e2816b877c189bf)
- [private-#374](https://github.com/h2oai/private-h2o-3/issues/374): varImp() function for DRF and GBM [(github)](https://github.com/h2oai/h2o-3/commit/4bc6f08e8fbc55c2d5795fd30f0bc4d0481e2499)
- [github](https://github.com/h2oai/h2o-3/commit/201f8d11c1773cb3119c0294784bf47c08cf42ba): Change some of the defaults

##### API

- [#13661](https://github.com/h2oai/h2o-3/issues/13661): have the /Frames/{key}/summary API call Vec.startRollupStats

##### R/Python

- [#13471](https://github.com/h2oai/h2o-3/issues/13471): Port MissingInserter to R/Python
- [#13624](https://github.com/h2oai/h2o-3/issues/13624): Display TwoDimTable of HitRatios in R/Python
- [github](https://github.com/h2oai/h2o-3/commit/6ed0f24693ab872179336289207345517d6925de): minor change to h2o.demo()
- [github](https://github.com/h2oai/h2o-3/commit/a7fbe9f0734cfece37ab408eb644c853a344b964): add h2o.demo() facility to python package, along with some built-in (small) data
- [github](https://github.com/h2oai/h2o-3/commit/3475e47fd2271e317d167c07755561d19c6b8fc8): remove cols param


#### Bug Fixes

##### Algorithms

- [#14189](https://github.com/h2oai/h2o-3/issues/14189): pca: descaled pca, std dev seems to be wrong for attached data [github](https://github.com/h2oai/h2o-3/commit/edfa4e30e72ecb02cc4c99c8d8a02313b58c0f63)
- [#14191](https://github.com/h2oai/h2o-3/issues/14191): pca: would be good to have the std dev numbered bec difficult to relate to the principal components [(github)](https://github.com/h2oai/h2o-3/commit/90ef7c083823fca05a0f94bf8c7e451ea64b646a)
- [#14179](https://github.com/h2oai/h2o-3/issues/14179): pca: get ArrayIndexOutOfBoundsException [(github)](https://github.com/h2oai/h2o-3/commit/1390a4bd4de3adcdb924658e9122f230f7521fea)
- [#14181](https://github.com/h2oai/h2o-3/issues/14181): pca: giving wrong std dev/rotation-labels for iris with species as enum [(github)](https://github.com/h2oai/h2o-3/commit/7ae347fcd26e96cdbd8b5fa3a42585cb5530fe01)
- [#14177](https://github.com/h2oai/h2o-3/issues/14177): DL with <1 epochs has wrong initial estimated time [(github)](https://github.com/h2oai/h2o-3/commit/5b6854954f037b645002accbf35f0670b01df41f)
- [github](https://github.com/h2oai/h2o-3/commit/5cbd138e06797954e6aa6996c5733a1eaf927316): Fix missing AUC for training data in DL.
- [github](https://github.com/h2oai/h2o-3/commit/761b6ef5d75d7327d446e0b23e1fe74bc509b6a3): Add the seed back to GBM imbalanced test (was set to 0 by default before, now explicit)


##### R

- [#14167](https://github.com/h2oai/h2o-3/issues/14167): R: h2o.hist broken for breaks that is a list of the break intervals [(github)](https://github.com/h2oai/h2o-3/commit/6118b04367cfc58c54f0c5ff51faf9b72a06088a)
- [#14184](https://github.com/h2oai/h2o-3/issues/14184): Frame summary from R and Python need to use the Frame summary endpoint [(github)](https://github.com/h2oai/h2o-3/commit/1ed38e5a4686e7ea4da56752d270e4d07450f402)
- [#14155](https://github.com/h2oai/h2o-3/issues/14155): R summary() is slow when large number of columns
- [#14077](https://github.com/h2oai/h2o-3/issues/14077): R: R should be able to take a of paths similar to how python does

---

### Shannon (3.0.0.11) - 5/22/15

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-shannon/11/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-shannon/11/index.html</a>

#### Enhancements

##### Algorithms

- [#14157](https://github.com/h2oai/h2o-3/issues/14157): DRF: investigate if larger seeds giving better models
- [#14156](https://github.com/h2oai/h2o-3/issues/14156): Add logloss/AUC/Error to GBM/DRF Logs & ScoringHistory
- [#14148](https://github.com/h2oai/h2o-3/issues/14148): Use only 1 tree for DRF binomial [(github)](https://github.com/h2oai/h2o-3/commit/84fce9cfab37a6c4fd73a22e9258685bc2fb124e)
- [#14149](https://github.com/h2oai/h2o-3/issues/14149): Wrong ROC is shown for DRF (Training ROC, even though Validation is given)
- [#14142](https://github.com/h2oai/h2o-3/issues/14142): Speed up sorting of histograms with O(N log N) instead of O(N^2)

##### System

- [#14132](https://github.com/h2oai/h2o-3/issues/14132): Accept s3a URLs
- [private-#384](https://github.com/h2oai/private-h2o-3/issues/384): ImportFiles should not download files from HTTP

#### Bug Fixes

##### Algorithms

- [private-#429](https://github.com/h2oai/private-h2o-3/issues/429): model output consistency
- [private-#381](https://github.com/h2oai/private-h2o-3/issues/381): DRF in h2o 3.0 is worse than in h2o 2.0 for Airline
- [#14158](https://github.com/h2oai/h2o-3/issues/14158): DRF has wrong training metrics when validation is given


##### API

- [#13492](https://github.com/h2oai/h2o-3/issues/13492): H2OPredict: does not complain when you build a model with one dataset and predict on completely different dataset

##### Python

- [#14161](https://github.com/h2oai/h2o-3/issues/14161): Python version check should fail hard by default
- [#14163](https://github.com/h2oai/h2o-3/issues/14163): Python binding version mismatch check should fail hard and be on by default
- [private-#521](https://github.com/h2oai/private-h2o-3/issues/521): Port Python tests for Deep Learning


### ##R

- [#14140](https://github.com/h2oai/h2o-3/issues/14140): R: h2o.hist doesn't support breaks argument
- [#14139](https://github.com/h2oai/h2o-3/issues/14139): R: h2o.hist takes too long to run
- [#14130](https://github.com/h2oai/h2o-3/issues/14130): R CMD Check: URLs not working
- [#14129](https://github.com/h2oai/h2o-3/issues/14129): R CMD check not happy with our use of .OnAttach
- [#14152](https://github.com/h2oai/h2o-3/issues/14152): R: h2o.hist FD implementation broken
- [#14147](https://github.com/h2oai/h2o-3/issues/14147): R: h2o.group_by broken
- [private-#382](https://github.com/h2oai/private-h2o-3/issues/382): the fix to H2O startup for the host unreachable from R causes a security hole
- [#14165](https://github.com/h2oai/h2o-3/issues/14165): FramesHandler.summary() needs to run summary on all Vecs concurrently.


##### System

- [#13850](https://github.com/h2oai/h2o-3/issues/13850): Building a model without training file -> NPE
- [private-#385](https://github.com/h2oai/private-h2o-3/issues/385): importFile fails: Error in fromJSON(txt, ...) : unexpected character: A
- [#14117](https://github.com/h2oai/h2o-3/issues/14117): Parse: upload and import gives different chunk compression on the same file
- [#14034](https://github.com/h2oai/h2o-3/issues/14034): Parse: h2o parses arff file incorrectly
- [#14159](https://github.com/h2oai/h2o-3/issues/14159): Rapids should queue and block on the back-end to prevent overlapping calls
- [#14162](https://github.com/h2oai/h2o-3/issues/14162): importFile fails for paths containing spaces



##### Web UI

- [#14160](https://github.com/h2oai/h2o-3/issues/14160): Flow: when upload file fails, the control does not come back to the flow screen, and have to refresh the whole page to get it back
- [#14111](https://github.com/h2oai/h2o-3/issues/14111): GBM crashes after calling getJobs in Flow

---

### Shannon (3.0.0.7) - 5/18/15

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-shannon/7/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-shannon/7/index.html</a>

#### Enhancements

##### API

- [#13702](https://github.com/h2oai/h2o-3/issues/13702): take a final look at all REST API parameter names and help strings
- [#13746](https://github.com/h2oai/h2o-3/issues/13746): Rename DocsV1 + DocsHandler to MetadataV1 + MetadataHandler
- [#14118](https://github.com/h2oai/h2o-3/issues/14118): Performance improvements for big data sets => getModels
- [#14106](https://github.com/h2oai/h2o-3/issues/14106): Performance improvements for big data sets => Get frame summary


##### System

- [private-#384](https://github.com/h2oai/private-h2o-3/issues/384): ImportFiles should not download files from HTTP


##### Web UI

- [#14124](https://github.com/h2oai/h2o-3/issues/14124): Update/Fix Flow API for CreateFrame


#### Bug Fixes

The following changes are to resolve incorrect software behavior:


##### API

- [#13492](https://github.com/h2oai/h2o-3/issues/13492): H2OPredict: does not complain when you build a model with one dataset and predict on completely different dataset
- [#14027](https://github.com/h2oai/h2o-3/issues/14027): API : Get frames and Build model => takes long time to get frames
- [private-#513](https://github.com/h2oai/private-h2o-3/issues/513): Allow JobsV3 to return properly typed jobs, not always instances of JobV3
- [#14017](https://github.com/h2oai/h2o-3/issues/14017): rename straggler V2 schemas to V3

##### R

- [#14139](https://github.com/h2oai/h2o-3/issues/14139): R: h2o.hist takes too long to run


##### System

- [#14015](https://github.com/h2oai/h2o-3/issues/14015): Windows 7/8/2012 Multicast Error UDP
- [#13850](https://github.com/h2oai/h2o-3/issues/13850): Building a model without training file -> NPE
- [private-#429](https://github.com/h2oai/private-h2o-3/issues/429): model output consistency
- [#14115](https://github.com/h2oai/h2o-3/issues/14115): While predicting get:class water.fvec.RollupStats$ComputeRollupsTask; class java.lang.ArrayIndexOutOfBoundsException: 5
- [#14071](https://github.com/h2oai/h2o-3/issues/14071): POJO: Models with "." in key name (ex. pros.glm) can't access pojo endpoint
- [#14057](https://github.com/h2oai/h2o-3/issues/14057): Getting an IcedHashMap warning from H2O startup

##### Web UI

- [#14113](https://github.com/h2oai/h2o-3/issues/14113): getModels in Flow returns error
- [#13911](https://github.com/h2oai/h2o-3/issues/13911): Flow: When user hits build model without specifying the training frame, it would be good if Flow  guides the user. It presently shows an NPE msg
- [#14111](https://github.com/h2oai/h2o-3/issues/14111): GBM crashes after calling getJobs in Flow

---

### Shannon (3.0.0.2) - 5/15/15

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o/rel-shannon/2/index.html'>http://h2o-release.s3.amazonaws.com/h2o/rel-shannon/2/index.html</a>

#### New Features

##### ModelMetrics

- [#13298](https://github.com/h2oai/h2o-3/issues/13298): ModelMetrics by model category

##### WebUI

- [#13926](https://github.com/h2oai/h2o-3/issues/13926): ModelMetrics by model category - Autoencoder

#### Enhancements

##### Algorithms

- [github](https://github.com/h2oai/h2o-dev/commit/6bdd386d4f1d6bde8d045691c8f250266f3142fc): GLM update: skip lambda max during lambda search
- [github](https://github.com/h2oai/h2o-dev/commit/e73b1e8316a100f60061ceb44eab4ff3c18f0452): removed higher accuracy option
- [github](https://github.com/h2oai/h2o-dev/commit/dd11a6fc8e279a8ecd65d412251a43421d2d32fb): Rename constant col parameter
- [github](https://github.com/h2oai/h2o-dev/commit/fa215e4247b8ed48e250d24914d65f46c0ecf5ad): GLM update: added stopping criteria to lbfgs, tweaked some internal constants in ADMM
- [github](https://github.com/h2oai/h2o-dev/commit/31bd600396195c250c9f1f1b8c67c86d41763cda): Add support for `ignore_const_col` in DL


##### Python

- [#13840](https://github.com/h2oai/h2o-3/issues/13840): Binomial: show per-metric-optimal CM and per-threshold CM in Python
- [github](https://github.com/h2oai/h2o-dev/commit/353d5438fc09fd5581c6b07f567f596e062fab08): add filterNACols to python
- [github](https://github.com/h2oai/h2o-dev/commit/5a1971bb62805f1d862dca347e681e87b33a11da): h2o.delete replaced with h2o.removeFrameShallow
- [github](https://github.com/h2oai/h2o-dev/commit/98c8130036404735d42e2e8280a50626227a4f13): Add distribution summary to Python


##### R

- [github](https://github.com/h2oai/h2o-dev/commit/6e3d7938436bdf427e780269605896eb778aa74d): add filterNACols to R
- [github](https://github.com/h2oai/h2o-dev/commit/6b6c49605c6e673d4280542b719589589679d20e): explicitly set cols=TRUE for R style str on frames
- [github](https://github.com/h2oai/h2o-dev/commit/e342409fa536b6163873fda63118d9164ced46d3): enable faster str, bulk nlevels, bulk levels, bulk is.factor
- [github](https://github.com/h2oai/h2o-dev/commit/3d6e616fad8d1889670d2e270622425ab750961b): Add optional blocking parameter to h2o.uploadFile

##### System

- [#13664](https://github.com/h2oai/h2o-3/issues/13664) HTML version of the REST API docs should be available on the website
- [#13815](https://github.com/h2oai/h2o-3/issues/13815): class GenModel duplicates part of code of Model

##### Web UI

- [private-#492](https://github.com/h2oai/private-h2o-3/issues/492) Flow: Handle deep features prediction input and output
- [github](https://github.com/h2oai/h2o-dev/commit/7639e27): removed `use_all_factor_levels` from glm flows

#### Bug Fixes

##### Algorithms

- [private-#395](https://github.com/h2oai/private-h2o-3/issues/395): AIOOBE during Prediction with DL [github](https://github.com/h2oai/h2o-dev/commit/e19d952b6b3cc787b542ba49e72868a2d8ab10de)
- [github](https://github.com/h2oai/h2o-dev/commit/b1df59e7d2396836ce3574acda0c69f7a49f9d54): glm fix: don't force in null model for lambda search with user given list of lambdas
- [github](https://github.com/h2oai/h2o-dev/commit/51608cbb392e28c018a56f74c670d5ab88d99947): Fix domain in glm scoring output for binomial
- [github](https://github.com/h2oai/h2o-dev/commit/5796b1f2ded1f984df0737f750e3e6d65e69cbd7): GLM Fix - fix degrees of freedom when running without intercept (+/-1)
- [github](https://github.com/h2oai/h2o-dev/commit/f8ee8a5f64266cf5803af80dadb48495c6b02e7b): GLM fix: make valid data info be clone of train data info (needs exactly the same categorical offsets, ignore unseen levels)
- [github](https://github.com/h2oai/h2o-dev/commit/a8659171c3d6a69a1723322beefcff52345ad512): Fix glm scoring, fill in default domain {0,1} for binary columns when scoring

##### R

- [#14096](https://github.com/h2oai/h2o-3/issues/14096): R: Parse that works from flow doesn't work from R using as.h2o
- [#13785](https://github.com/h2oai/h2o-3/issues/13785): R: String Munging Functions Missing
- [#13577](https://github.com/h2oai/h2o-3/issues/13577): R: hist() doesn't currently work for H2O objects
- [#13808](https://github.com/h2oai/h2o-3/issues/13808): H2oR: model objects should return the CM when run classification like h2o1
- [#14093](https://github.com/h2oai/h2o-3/issues/14093): Remove Keys : Parse => Remove => doesn't complete
- [#14082](https://github.com/h2oai/h2o-3/issues/14082): R: h2o.rbind fails to join two dataset together
- [#13885](https://github.com/h2oai/h2o-3/issues/13885): R: all doesn't work
- [#13561](https://github.com/h2oai/h2o-3/issues/13561): H2O-R: str does not work
- [#14090](https://github.com/h2oai/h2o-3/issues/14090): H2OR: while printing a gbm model object, get invalid format '%d'; use format %f, %e, %g or %a for numeric objects
- [#13889](https://github.com/h2oai/h2o-3/issues/13889): R: Errors from some rapids calls seem to fail to return an error
- [private-#389](https://github.com/h2oai/private-h2o-3/issues/389): Performance bug from R with Expect: 100-continue
- [#14011](https://github.com/h2oai/h2o-3/issues/14011): h2o.performance: ignores the user specified threshold
- [#14051](https://github.com/h2oai/h2o-3/issues/14051): R: regression models don't show in print statement r2 but it exists in the model object
- [#14052](https://github.com/h2oai/h2o-3/issues/14052): R: missing accessors for glm specific fields
- [#14013](https://github.com/h2oai/h2o-3/issues/14013): After running some R and  py demos when invoke a build model from flow get- rollup stats problem vec deleted error
- [#14049](https://github.com/h2oai/h2o-3/issues/14049): R: missing implementation for h2o.r2
- [#14044](https://github.com/h2oai/h2o-3/issues/14044): Passing sep="," to h2o.importFile() fails with '400 Bad Request'
- [#14070](https://github.com/h2oai/h2o-3/issues/14070): Get NPE while predicting


##### System

- [#14072](https://github.com/h2oai/h2o-3/issues/14072): S3 gzip parse failure
- [#14061](https://github.com/h2oai/h2o-3/issues/14061): Probably want to cleanly disable multicast (not retry) and print suggestion message, if multicast not supported on picked multicast network interface
- [#14092](https://github.com/h2oai/h2o-3/issues/14092): User has no way to specify whether to drop constant columns
- [#14089](https://github.com/h2oai/h2o-3/issues/14089): Change all extdata imports to uploadFile
- [#14084](https://github.com/h2oai/h2o-3/issues/14084): .gz file parse exception from local filesystem


##### Web UI

- [#14114](https://github.com/h2oai/h2o-3/issues/14114): getPredictions in Flow returns error
- [#14001](https://github.com/h2oai/h2o-3/issues/14001): Flow : Drop NA Cols enable => Should automatically populate the ignored columns
- [#14022](https://github.com/h2oai/h2o-3/issues/14022): Flow GLM: formatting needed for the model parameter listing in the model object [github](https://github.com/h2oai/h2o-dev/commit/70babd4b275807913d21b77bd377e321636edee7)
- [#14088](https://github.com/h2oai/h2o-3/issues/14088): Flow: When predict on data with no response get :Error processing POST /3/Predictions/models/gbm-a179db76-ba96-420f-a643-0e166aea3af3/frames/subset_1  'undefined' is not an object (evaluating 'prediction.model')

---

## H2O-Dev

### Shackleford (0.2.3.6) - 5/8/15

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o-dev/rel-shackleford/6/index.html'>http://h2o-release.s3.amazonaws.com/h2o-dev/rel-shackleford/6/index.html</a>


#### New Features

##### Python

- Set up POJO download for Python client [(#13894)](https://github.com/h2oai/h2o-3/issues/13894) [(github)](https://github.com/h2oai/h2o-dev/commit/4b06cc2415f5d5b0bb0be6a6ef419ed6ff065ada)

### ##Sparkling Water

- Publish h2o-scala and h2o-app latest version to maven central [(#13422)](https://github.com/h2oai/h2o-3/issues/13422)

#### Enhancements

##### Algorithms

- Use AUC's default threshold for label-making for binomial classifiers predict() [(#14043)](https://github.com/h2oai/h2o-3/issues/14043) [(github)](https://github.com/h2oai/h2o-dev/commit/588a95df335d534080737832adf846e4c12ba7c6)
- GLM update [(github)](https://github.com/h2oai/h2o-dev/commit/c1c8e2e428554307870ac1a595bb35f60e258245)
- Cleanup AUC2, make incremental version [(github)](https://github.com/h2oai/h2o-dev/commit/2d7d064229f9577cafc9a6d08b47efc653e0c546)
- Name change: `override_with_best_model` -> `overwrite_with_best_model` [(github)](https://github.com/h2oai/h2o-dev/commit/f14dca82a529e2cb080800e258ca23dcb6ac9535)
- Couple of GLM updates [(github)](https://github.com/h2oai/h2o-dev/commit/05cec9710a3578789bb34f04a5134f4320ac7547)
- Disable `_replicate_training_data` for data that's larger than 10GB [(github)](https://github.com/h2oai/h2o-dev/commit/4a1fed5f292826a4bc89eafffc6c04bb7449644c)
- Added `replicate_training_data` param for DL [(github)](https://github.com/h2oai/h2o-dev/commit/e95e4870869d159f8d468e4193fc7201887f1661)
- Change a few kmeans output parameters so no longer dividing by `nrows` or `num_clusters` [(github)](https://github.com/h2oai/h2o-dev/commit/9933486a61113af5ef6d3ed329c70eb7fbdc61a8)
- GLMValidation Updated auc computation [(github)](https://github.com/h2oai/h2o-dev/commit/280e8f8390dfc5b4d6b5a571f06930bab9b5c7e5)
- Do not delete model metrics at end of GBM/DRF [(github)](https://github.com/h2oai/h2o-dev/commit/d10d4522eae38bfc3bf45208266b8b5e5806d524)


##### API

- Clean REST api for Parse [(#13977)](https://github.com/h2oai/h2o-3/issues/13977)
- Removes `is_valid`, `invalid_lines`, and domains from REST api [(github)](https://github.com/h2oai/h2o-dev/commit/f5997de8f59f2eefd454afeb0e91a6a1d5c6672b)
- Annotate domains output field as expert level [(github)](https://github.com/h2oai/h2o-dev/commit/523af95008d3fb3b5d2269bb87a1de3235f6f828)

##### Python

- Implement h2o.interaction() [(#13842)](https://github.com/h2oai/h2o-3/issues/13842) [(github)](https://github.com/h2oai/h2o-dev/commit/3d43cb22afa0892c2c913b15e7b4bb5d4889443b)
- nice tables in ipython! [(github)](https://github.com/h2oai/h2o-dev/commit/fc6ecdc3d000375307f5731569a36a3c4e4fbf4c)
- added deeplearning weights and biases accessors and respective pyunit. [(github)](https://github.com/h2oai/h2o-dev/commit/7eb9f22262533ca7e335e9580af8afc3cf54c4b0)

##### R

- Cleaner client POJO download for R [(#13893)](https://github.com/h2oai/h2o-3/issues/13893)
- Implement h2o.interaction() [(#13842)](https://github.com/h2oai/h2o-3/issues/13842) [(github)](https://github.com/h2oai/h2o-dev/commit/58fa2f1e89bddd97b13a3884e15385ad0a5905d8)
- R: h2o.impute missing [(#13783)](https://github.com/h2oai/h2o-3/issues/13783)
- `validation_frame` is passed through to h2o [(github)](https://github.com/h2oai/h2o-dev/commit/184fe3a546e43c9b3d5664a808f6b30d3eaddab8)
- Adding GBM accessor function runits [(github)](https://github.com/h2oai/h2o-dev/commit/41d039196088df081ad77610d3e2d6550868f11b)
- Adding changes to `h2o.hit_ratio_table` to be like other accessors (i.e., no train) [(github)](https://github.com/h2oai/h2o-dev/commit/dc4a20151d9b415fe4708cff1bafc4fe61e802e0)
- add h2o.getPOJO to R, fix impute ast build in python [(github)](https://github.com/h2oai/h2o-dev/commit/8f192a7c87fa30782249af2e85ea2470fae491da)



##### System

- Change NA strings to an array in ParseSetup [(#13979)](https://github.com/h2oai/h2o-3/issues/13979)
- Document way of passing S3 credentials for S3N [(#13930)](https://github.com/h2oai/h2o-3/issues/13930)
- Add H2O-dev doc on docs.h2o.ai via a new structure (proposed below) [(#13355)](https://github.com/h2oai/h2o-3/issues/13355)
- Rapids Ref Doc [(#13659)](https://github.com/h2oai/h2o-3/issues/13659)
- Show Timestamp and Duration for all model scoring histories [(#13999)](https://github.com/h2oai/h2o-3/issues/13999) [(github)](https://github.com/h2oai/h2o-dev/commit/c02aa5efaf28ac21915c6fc427fc9b099aabee23)
- Logs slow reads, mainly meant for noting slow S3 reads [(github)](https://github.com/h2oai/h2o-dev/commit/d3b19e38ab083ea327ecea60a354cc91a22b68a8)
- Make prediction frame column names non-integer [(github)](https://github.com/h2oai/h2o-dev/commit/7fb855ca5eb546c03d1b7ea84b5b48093958ae9a)
- Add String[] factor_columns instead of int[] factors [(github)](https://github.com/h2oai/h2o-dev/commit/c381da2ae1a51b268b1f359d0594f3aea5feef04)
- change the runtime exception to a Log.info() if interface doesn't support multicast [(github)](https://github.com/h2oai/h2o-dev/commit/68f277c0ba8508bbebb34afac19f6233129bb55e)
- More robust way to copy Flow files to web root per Prithvi [(github)](https://github.com/h2oai/h2o-dev/commit/4e1b067e6456074107332c10b1af66443395325a)
- Switches `na_string` from a single value per column to an array per column [(github)](https://github.com/h2oai/h2o-dev/commit/a37ec777c10158a7afb29d1d5502f3c8082f6453)

##### Web UI

- Model output improvements [(private-#512)](https://github.com/h2oai/private-h2o-3/issues/512)


#### Bug Fixes


##### Algorithms

- H2O cloud shuts down with some H2O.fail error, while building some kmeans clusters [(#14031)](https://github.com/h2oai/h2o-3/issues/14031) [(github)](https://github.com/h2oai/h2o-dev/commit/d95dec2a412e87e054fc000032da375023b87dce)
- GLM:beta constraint does not seem to be working [(#14063)](https://github.com/h2oai/h2o-3/issues/14063)
- GBM - random attack bug (probably because `max_after_balance_size` is really small) [(#14041)](https://github.com/h2oai/h2o-3/issues/14041) [(github)](https://github.com/h2oai/h2o-dev/commit/8625632c4759b07f75ac85acc43d69cdb9b38e15)
- GLM: LBFGS objval java lang assertion error [(#14023)](https://github.com/h2oai/h2o-3/issues/14023) [(github)](https://github.com/h2oai/h2o-dev/commit/dc4a20151d9b415fe4708cff1bafc4fe61e802e0)
- PCA Cholesky NPE [(#13906)](https://github.com/h2oai/h2o-3/issues/13906)
- GBM: H2o returns just 5525 trees, when ask for a much larger number of trees [(#13848)](https://github.com/h2oai/h2o-3/issues/13848)
- CM returned by AUC2 doesn't agree with manual-made labels from F1-optimal threshold [(private-#426)](https://github.com/h2oai/private-h2o-3/issues/426)
- AUC: h2o reporting wrong auc on a modified covtype data [(#13877)](https://github.com/h2oai/h2o-3/issues/13877)
- GLM: Build model => Predict => Residual deviance/Null deviance different from training/validation metrics [(#15421)](https://github.com/h2oai/h2o-3/issues/15421)
- KMeans metrics incomplete [(#14010)](https://github.com/h2oai/h2o-3/issues/14010)
- GLM: Java Assertion Error [(#14006)](https://github.com/h2oai/h2o-3/issues/14006)
- Random forest bug [(#13996)](https://github.com/h2oai/h2o-3/issues/13996)
- A particular random forest model has an empty (training) metric json `max_criteria_and_metric_scores` [(#13965)](https://github.com/h2oai/h2o-3/issues/13965)
- PCA results exhibit numerical inaccuracies compared to R [(#13532)](https://github.com/h2oai/h2o-3/issues/13532)
- DRF: reporting wrong depth for attached dataset [(#13988)](https://github.com/h2oai/h2o-3/issues/13988)
- added missing "names" column name to beta constraints processing [(github)](https://github.com/h2oai/h2o-dev/commit/fedcf159f8e842212812b0636b26ca9aa9ef1097)
- Fix `balance_classes` probability correction consistency between H2O and POJO [(github)](https://github.com/h2oai/h2o-dev/commit/5201f6da1196434866be6e70da996fb7c5967b7b)
- Fix in GLM scoring - check actual for NaNs as well [(github)](https://github.com/h2oai/h2o-dev/commit/e45c023a767dc26083f7fb26d9616ee234c03d2e)

##### Python

- Cannot import_file path=url python interface [(#14039)](https://github.com/h2oai/h2o-3/issues/14039)
- head()/tail() should show labels, rather than number encoding, for enum columns [(#13998)](https://github.com/h2oai/h2o-3/issues/13998)
- h2o.py: for binary response printing transpose and hence wrong cm [(#13994)](https://github.com/h2oai/h2o-3/issues/13994)

##### R

- Broken Summary in R [(#14053](https://github.com/h2oai/h2o-3/issues/14053)
- h2oR summary: displaying no labels in summary [(#13990)](https://github.com/h2oai/h2o-3/issues/13990)
- R/Python impute bugs [(#14035)](https://github.com/h2oai/h2o-3/issues/14035)
- R: h2o.varimp doubles the print statement [(#14048)](https://github.com/h2oai/h2o-3/issues/14048)
- R: h2o.varimp returns NULL when model has no variable importance [(#14058)](https://github.com/h2oai/h2o-3/issues/14058)
- h2oR: h2o.confusionMatrix(my_gbm, validation=F) should not show a null [(#13837)](https://github.com/h2oai/h2o-3/issues/13837)
- h2o.impute doesn't impute [(#14005)](https://github.com/h2oai/h2o-3/issues/14005)
- R: as.h2o cutting entries when trying to import data.frame into H2O [(private-#397)](https://github.com/h2oai/private-h2o-3/issues/397)
- The default names are too long, for an R-datafile parsed to H2O, and needs to be changed [(#13959)](https://github.com/h2oai/h2o-3/issues/13959)
- H2o.confusionMatrix: when invoked with threshold gives error [(#13991)](https://github.com/h2oai/h2o-3/issues/13991)
- removing train and adding error messages for valid = TRUE when there's not validation metrics [(github)](https://github.com/h2oai/h2o-dev/commit/cc3cf212300e252f987992e98d22a9fb6e46be3f)



##### System

- Download logs is returning the same log file bundle for every node [(#14036)](https://github.com/h2oai/h2o-3/issues/14036)
- ParseSetup is useless and misleading for SVMLight [(#13978)](https://github.com/h2oai/h2o-3/issues/13978)
- Fixes bug that was short circuiting the setting of column names [(github)](https://github.com/h2oai/h2o-dev/commit/5296456c425d9f9c0a467a2b65d448940f76c6a6)

##### Web UI

- Flow: Predict should not show mse confusion matrix etc [(#13972)](https://github.com/h2oai/h2o-3/issues/13972) [(github)](https://github.com/h2oai/h2o-dev/commit/6bc90e19cfefebd0db3ec4a46d3a157e258ff858)
- Flow: Raw frames left out after importing files from directory [(#14026)](https://github.com/h2oai/h2o-3/issues/14026)

---

### Shackleford (0.2.3.5) - 5/1/15

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o-dev/rel-shackleford/5/index.html'>http://h2o-release.s3.amazonaws.com/h2o-dev/rel-shackleford/5/index.html</a>

#### New Features

##### API

- Need a /Log REST API to log client-side errors to H2O's log [(private-#399)](https://github.com/h2oai/private-h2o-3/issues/399)


### ##Python

- add impute to python interface [(github)](https://github.com/h2oai/h2o-dev/commit/8a4d39e8bca6a4acfb8fc5f01a8febe07e519a08)

##### System

- Job admission control [(#13520)](https://github.com/h2oai/h2o-3/issues/13520) [(github)](https://github.com/h2oai/h2o-dev/commit/f5ef7323c72cf4be2dabf57a298fcc3d6687e9dd)
- Get Flow Exceptions/Stack Traces in H2O Logs [(#13905)](https://github.com/h2oai/h2o-3/issues/13905)

#### Enhancements

##### Algorithms

- GLM: Name to be changed from normalized to standardized in output to be consistent between input/output [(#13937)](https://github.com/h2oai/h2o-3/issues/13937)
- GLM: It would be really useful if the coefficient magnitudes are reported in descending order [(#13908)](https://github.com/h2oai/h2o-3/issues/13908)
- #13520: Limit DL models to 100M parameters [(github)](https://github.com/h2oai/h2o-dev/commit/5678a26447704021d8905e7c37dfcd37b74b7327)
- #13520: Add accurate memory-based admission control for GBM/DRF [(github)](https://github.com/h2oai/h2o-dev/commit/fc06a28c64d24ecb3a46a6a84d90809d2aae4875)
- relax the tolerance a little more...[(github)](https://github.com/h2oai/h2o-dev/commit/a24f4886b94b93f71452848af3a7d0f7b440779c)
- Tree depth correction [(github)](https://github.com/h2oai/h2o-dev/commit/2ad89a3eff0d8aa411b94b1d6f387051671b9bf8)
- Comment out `duration_in_ms` for now, as it's always left at 0 [(github)](https://github.com/h2oai/h2o-dev/commit/8008f017e10424623f966c141280d080f08f80b5)
- Updated min mem computation for glm [(github)](https://github.com/h2oai/h2o-dev/commit/446d5c30cdffcf04a4b7e0feaefa501187049efb)
- GLM update: added lambda search info to scoring history [(github)](https://github.com/h2oai/h2o-dev/commit/90ac3bb9cc07e4f50b50b08aad8a33279a0ff43d)

##### Python

- python .show() on model and metric objects should match R/Flow as much as possible [(private-#401)](https://github.com/h2oai/private-h2o-3/issues/401)
- GLM model output, details from Python [(private-#564)](https://github.com/h2oai/private-h2o-3/issues/564)
- GBM model output, details from Python [(private-#557)](https://github.com/h2oai/private-h2o-3/issues/557)
- Run GBM from Python [(private-#560)](https://github.com/h2oai/private-h2o-3/issues/560)
- map domain to result from /Frames if needed [(github)](https://github.com/h2oai/h2o-dev/commit/b1746a52cd4399d58385cd29914fa54870680093)
- added confusion matrix to metric output [(github)](https://github.com/h2oai/h2o-dev/commit/f913cc1643774e9c2ec5455620acf11cbd613711)
- update `metrics_base_confusion_matrices()` [(github)](https://github.com/h2oai/h2o-dev/commit/41c0a4b0079426860ac3b65079d6be0e46c6f69c)
- fetch out `string_data` if type is string [(github)](https://github.com/h2oai/h2o-dev/commit/995e135e0a49e492cccfb65974160b04c764eb11)

##### R

- GBM model output, details from R [(private-#558)](https://github.com/h2oai/private-h2o-3/issues/558)
- Run GBM from R [(private-#561)](https://github.com/h2oai/private-h2o-3/issues/561)
- check if it's a frame then check NA [(github)](https://github.com/h2oai/h2o-dev/commit/d61de7d0b8a9dac7d5d6c7f841e19c88983308a1)

##### System

- Report MTU to logs [(#13606)](https://github.com/h2oai/h2o-3/issues/13606) [(github)](https://github.com/h2oai/h2o-dev/commit/bbc3ad54373a2c865ce913917ef07c9892d62603)
- Make parameter changes Log.info() instead of Log.warn() [(github)](https://github.com/h2oai/h2o-dev/commit/7047a46fff612f41cc678f297cfcbc57ed8165fd)

##### Web UI

- Flow: Confusion matrix: good to have consistency in the column and row name (letter) case [(#13954)](https://github.com/h2oai/h2o-3/issues/13954)
- Run GBM Multinomial from Flow [(private-#548)](https://github.com/h2oai/private-h2o-3/issues/548)
- Run GBM Regression from Flow [(private-#547)](https://github.com/h2oai/private-h2o-3/issues/547)
- Sort model types in alphabetical order in Flow [(#13992)](https://github.com/h2oai/h2o-3/issues/13992)



#### Bug Fixes

The following changes are to resolve incorrect software behavior:

##### Algorithms

- GLM: Model output display issues [(#13939)](https://github.com/h2oai/h2o-3/issues/13939)
- h2o.glm: ignores validation set [(#13941)](https://github.com/h2oai/h2o-3/issues/13941)
- DRF: reports wrong number of leaves in a summary [(#13915)](https://github.com/h2oai/h2o-3/issues/13915)
- h2o.glm: summary of a prediction frame gives na's as labels [(#13942)](https://github.com/h2oai/h2o-3/issues/13942)
- GBM: reports wrong max depth for a binary model on german data [(#13827)](https://github.com/h2oai/h2o-3/issues/13827)
- GLM: Confusion matrix missing in R for binomial models [(#13933)](https://github.com/h2oai/h2o-3/issues/13933) [(github)](https://github.com/h2oai/h2o-dev/commit/d8845e3245491a85c2cc6c932d5fad2c260c19d3)
- GLM: On airlines(40g) get ArrayIndexOutOfBoundsException [(#13950)](https://github.com/h2oai/h2o-3/issues/13950)
- GLM: Build model => Predict => Residual deviance/Null deviance different from training/validation metrics [(#15421)](https://github.com/h2oai/h2o-3/issues/15421)
- Domains returned by GLM for binomial classification problem are integers, but should be mapped to their label [(#13981)](https://github.com/h2oai/h2o-3/issues/13981)
- GLM: Validation on non training data gives NaN Res Deviance and AIC [(#13987)](https://github.com/h2oai/h2o-3/issues/13987)
- Confusion matrix has nan's in it [(#13969)](https://github.com/h2oai/h2o-3/issues/13969)
- glm fix: pass `model_id` from R (was being dropped) [(github)](https://github.com/h2oai/h2o-dev/commit/9d8698177a9d0a70668d2d51005947d0adda0292)

##### Python

- H2OPy: warns about version mismatch even when installed the latest from master [(#13962)](https://github.com/h2oai/h2o-3/issues/13962)
- Columns of type enum lose string label in Python H2OFrame.show() [(#13948)](https://github.com/h2oai/h2o-3/issues/13948)
- Bug in H2OFrame.show() [(private-#396)](https://github.com/h2oai/private-h2o-3/issues/396) [(github)](https://github.com/h2oai/h2o-dev/commit/b319969cff0f0e7a805e49563e863a1dbb0e1aa0)


##### R

- h2o.confusionMatrix for binary response gives not-found thresholds [(#13940)](https://github.com/h2oai/h2o-3/issues/13940)
- GLM: model_id param is ignored in R [(#13989)](https://github.com/h2oai/h2o-3/issues/13989)
- h2o.confusionmatrix: mixing cases(letter) for categorical labels while printing multinomial cm [(#13980)](https://github.com/h2oai/h2o-3/issues/13980)
- fix the dupe thresholds error [(github)](https://github.com/h2oai/h2o-dev/commit/e40d4fd50cfd9438b2f693228ca20ad4d6648b46)
- extra arg in impute example [(github)](https://github.com/h2oai/h2o-dev/commit/5a41e7672fa30b2e66a1261df8976d18e89f0057)
- fix missing param data [(github)](https://github.com/h2oai/h2o-dev/commit/6719d94b30caf214fac2c61759905c7d5d57a9ac)


##### System

- Builds : Failing intermittently due to java.lang.StackOverflowError [(#13955)](https://github.com/h2oai/h2o-3/issues/13955)
- Get H2O cloud hang with NPE and roll up stats problem, when click on build model glm from flow, on laptop after running a few python demos and R scripts [(#13946)](https://github.com/h2oai/h2o-3/issues/13946)

##### Web UI

- Flow :=> Airlines dataset => Build models glm/gbm/dl => water.DException$DistributedException: from /172.16.2.183:54321; by class water.fvec.RollupStats$ComputeRollupsTask; class java.lang.NullPointerException: null [(#13595)](https://github.com/h2oai/h2o-3/issues/13595)
- Flow => Preview Pojo => collapse not working [(#13960)](https://github.com/h2oai/h2o-3/issues/13960)
- Flow => Any algorithm => Select response => Select Add all for ignored columns => Try to unselect some from ignored columns => Build => Response column IsDepDelayed not found in frame: allyears_1987_2013.hex. [(#13961)](https://github.com/h2oai/h2o-3/issues/13961)
- Flow => ROC curve select something on graph => Table is displayed for selection => Collapse ROC curve => Doesn't collapse table, collapses only graph [(#13985)](https://github.com/h2oai/h2o-3/issues/13985)


---

### Severi (0.2.2.16) - 4/29/15


#### New Features

##### Python

- Release h2o-dev to PyPi [(#13751)](https://github.com/h2oai/h2o-3/issues/13751)
- Python Documentation [(#13887)](https://github.com/h2oai/h2o-3/issues/13887)
- Python docs Wrap Up [(#13949)](https://github.com/h2oai/h2o-3/issues/13949)
- add getters for res/null dev, fix kmeans,dl getters [(github)](https://github.com/h2oai/h2o-dev/commit/3f9839c25628e44cba77b44905c38c21bee60a9c)



#### Enhancements

##### Algorithms

- Use partial-sum version of mat-vec for DL POJO [(#13920)](https://github.com/h2oai/h2o-3/issues/13920)
- Always store weights and biases for DLTest Junit [(github)](https://github.com/h2oai/h2o-dev/commit/5bcbad8e07fd592e2db701adf9b4974a5b4470b1)
- Show the DL model size in the model summary [(github)](https://github.com/h2oai/h2o-dev/commit/bdba19a99b863cd2f49ff1bdcd4ca648b60d1372)
- Remove assertion in hot loop [(github)](https://github.com/h2oai/h2o-dev/commit/9d1682e2821fc648dda02497ba5200e45bd6b6f5)
- Rename ADMM to IRLSM [(github)](https://github.com/h2oai/h2o-dev/commit/6a108d38e7b9473a792a5ba36b58a860166c84c4)
- Added no intercept option to glm [(github)](https://github.com/h2oai/h2o-dev/commit/6d99bd194cbc4500f519e306f28384d7dca407e1)
- Code cleanup. Moved ModelMetricsPCAV3 out of H2O-algos [(github)](https://github.com/h2oai/h2o-dev/commit/1f691681407b579ed0b71e4e6d452120dc3263dd)
- Improve DL model checkpoint logic [(github)](https://github.com/h2oai/h2o-dev/commit/9a13070c0de6ac2bf34b0e60c305de7358711965)
- Updated glm output [(github)](https://github.com/h2oai/h2o-dev/commit/4359a17f573bf27f0ac5e078143299de09011325)
- Renamed normalized coefficients to standardized coefficients in glm output [(github)](https://github.com/h2oai/h2o-dev/commit/39b814d37e9e161d1dd943741afcff59fd83d745)
- Use proper tie breaking for NB [(github)](https://github.com/h2oai/h2o-dev/commit/4bbbd1b6161e8d2d62f8d3d9cb600e3c6d678653)
- Add check that DL parameters aren't modified by model training [(github)](https://github.com/h2oai/h2o-dev/commit/84d4ab6bc63b314bab4f38e629e77fb8207f705f)
- Reduce tolerances [(github)](https://github.com/h2oai/h2o-dev/commit/0654d3c2d644abb9aa0d0c25e032db1a4fd219ad)
- If no observations of a response leveland prediction is numeric, assume it is drawn from standard normal distribution (mean 0, standard deviation 1). Add validation test with split frame for naive Bayes [(github)](https://github.com/h2oai/h2o-dev/commit/50a5d9cbb1f77db568a23573f6cff0cf45cb36af)



##### Python

- replaced H2OFrame.send_frame() calls with cbind Exprs so that lazy evaluation is enforced [(github)](https://github.com/h2oai/h2o-dev/commit/2799b8cb2d01270556d4481a40af4a8da6f0519f)
- change default xmx/s behavior of h2o.init() [(github)](https://github.com/h2oai/h2o-dev/commit/843a232c52e6b357dbd84db3253b3e33b8297803)
- better handling of single row return and print [(github)](https://github.com/h2oai/h2o-dev/commit/b2e782bf17352009992ad1252762f43977f95c8b)


##### R

- Added interpolation to quantile to match R type 7 [(github)](https://github.com/h2oai/h2o-dev/commit/a330ffb6ff30c5500e3fb6a80fe92ac8b123a4be)
- Removed and tidied if's in quantile.H2OFrame since it now uses match.arg [(github)](https://github.com/h2oai/h2o-dev/commit/237306039a3e2483c92ac310e157ec515b885530)
- Connected validation dataset to glm in R [(github)](https://github.com/h2oai/h2o-dev/commit/e71895bd3fc7507092f65cbde6a914f74dacf85d)
- Removing h2o.aic from seealso link (doesn't exist) and updating documentation [(github)](https://github.com/h2oai/h2o-dev/commit/8fa994efea831722dd333327789a858ed902bc79)


##### System

- Add number of rows (per node) to ChunkSummary [(#13922)](https://github.com/h2oai/h2o-3/issues/13922) [(github)](https://github.com/h2oai/h2o-dev/commit/06d33469e0fabb0ae452f29dc633647aef8c9bb3)
- allow nrow as alias for count in groupby [(github)](https://github.com/h2oai/h2o-dev/commit/fbeef36b9dfea422dfed7f209a196731d9312e8b)
- Only launches task to fill in SVM zeros if the file is SVM [(github)](https://github.com/h2oai/h2o-dev/commit/d816c52a34f2e8f549f8a3b0bf7d976333366553)
- Adds more log traces to track progress of post-ingest actions [(github)](https://github.com/h2oai/h2o-dev/commit/c0073164d8392fd2d079db840b84e6330bebe2e6)
- Adds svm as a file extension to the hex name cleanup [(github)](https://github.com/h2oai/h2o-dev/commit/0ad9eec48650491f5ec2e01c010be9987dac0a21)

##### Web UI

- Flow: Inspect data => Round decimal points to 1 to be consistent with h2o1 [(#13445)](https://github.com/h2oai/h2o-3/issues/13445)
- Setup POJO download method for Flow [(#13895)](https://github.com/h2oai/h2o-3/issues/13895)
- Pretty-print POJO preview in flow [(#13924)](https://github.com/h2oai/h2o-3/issues/13924)
- Flow: It would be good if 'get predictions' also shows the data [(#13870)](https://github.com/h2oai/h2o-3/issues/13870)
- GBM model output, details in Flow [(private-#556)](https://github.com/h2oai/private-h2o-3/issues/556)
- Display a linked data table for each visualization in Flow [(#13319)](https://github.com/h2oai/h2o-3/issues/13319)
- Run GBM binomial from Flow (needs proper CM) [(#13927)](https://github.com/h2oai/h2o-3/issues/13927)



#### Bug Fixes


##### Algorithms

- GLM: results from model and prediction on the same dataset do not match [(#13907)](https://github.com/h2oai/h2o-3/issues/13907)
- GLM: when select AUTO as solver, for prostate, glm gives all zero coefficients [(#13902)](https://github.com/h2oai/h2o-3/issues/13902)
- Large (DL) models cause oversize issues during serialization [(#13925)](https://github.com/h2oai/h2o-3/issues/13925)
- Fixed name change for ADMM [(github)](https://github.com/h2oai/h2o-dev/commit/bc126aa8d4d7c5901ef90120c7997c67466922ae)

##### API

- Fix schema warning on startup [(#13929)](https://github.com/h2oai/h2o-3/issues/13929) [(github)](https://github.com/h2oai/h2o-dev/commit/bd9ae8013bc0de261e7258af85784e9e6f20df5e)


##### Python

- H2OVec.row_select(H2OVec) fails on case where only 1 row is selected [(#13931)](https://github.com/h2oai/h2o-3/issues/13931)
- fix pyunit [(github)](https://github.com/h2oai/h2o-dev/commit/79344be836d9111fee77ddebe034234662d7064f)

##### R

- R: Parse of zip file fails, Summary fails on citibike data [(#13823)](https://github.com/h2oai/h2o-3/issues/13823)
- h2o. performance reports a different Null Deviance than the model object for the same dataset [(#13804)](https://github.com/h2oai/h2o-3/issues/13804)
- h2o.glm: no example on h2o.glm help page [(#13945)](https://github.com/h2oai/h2o-3/issues/13945)
- H2O R: Confusion matrices from R still confused [(#13890)](https://github.com/h2oai/h2o-3/issues/13890) [(github)](https://github.com/h2oai/h2o-dev/commit/36c887ddadd47682745b64812e081dcb2fa36659)
- R: h2o.confusionMatrix("H2OModel", ...) extra parameters not working [(#13936)](https://github.com/h2oai/h2o-3/issues/13936) [(github)](https://github.com/h2oai/h2o-dev/commit/ca59b2be46dd07caad60882b5c1daed0ee4837c6)
- h2o.confusionMatrix for binomial gives not-found thresholds on S3 -airlines 43g [(#13940)](https://github.com/h2oai/h2o-3/issues/13940)
- H2O summary quartiles outside tolerance of (max-min)/1000 [(#13663)](https://github.com/h2oai/h2o-3/issues/13663)
- fix space headers issue from R (was not url-encoding the column strings) [(github)](https://github.com/h2oai/h2o-dev/commit/f121b0324e981e229cd2704df11a0a946d4b2aeb)
- R CMD fixes [(github)](https://github.com/h2oai/h2o-dev/commit/62a1d7df8bceeea181b87d83f922db854f28b6db)
- Fixed broken R interface - make `validation_frame` non-mandatory [(github)](https://github.com/h2oai/h2o-dev/commit/18fba95392f94e566b80797839e5eb2899057333)

##### Sparkling Water

- Sparkling water : #UDP-Recv ERRR: UDP Receiver error on port 54322java.lang.ArrayIndexOutOfBoundsException:[(#13314)](https://github.com/h2oai/h2o-3/issues/13314)


##### System

- Mapr 3.1.1 : Memory is not being allocated for what is asked for instead the default is what cluster gets [(#13921)](https://github.com/h2oai/h2o-3/issues/13921)
- GLM: AIOOBwith msg '-14' at water.RPC$2.compute2(RPC.java:593) [(#13903)](https://github.com/h2oai/h2o-3/issues/13903)
- h2o.glm: model summary listing same info twice [(#13901)](https://github.com/h2oai/h2o-3/issues/13901)
- Parse: Detect and reject UTF-16 encoded files [(private-#404)](https://github.com/h2oai/private-h2o-3/issues/404)
- DataInfo Row categorical encoding AIOOBE [(private-#406)](https://github.com/h2oai/private-h2o-3/issues/406)
- Fix POJO Preview exception [(github)](https://github.com/h2oai/h2o-dev/commit/d553710f66ef989dc33a86608c5cf352a7d98168)
- Fix NPE in ChunkSummary [(github)](https://github.com/h2oai/h2o-dev/commit/cd113515257ee1c493fe84616deb0643400ef32c)
- fix global name collision [(github)](https://github.com/h2oai/h2o-dev/commit/bde0b6d8fed4009367b2e2ddf999bd71cbda3b3f)


### Severi (0.2.2.15) - 4/25/15

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o-dev/rel-severi/15/index.html'>http://h2o-release.s3.amazonaws.com/h2o-dev/rel-severi/15/index.html</a>

#### New Features


##### Python

- added min, max, sum, median for H2OVecs and respective pyunit [(github)](https://github.com/h2oai/h2o-dev/commit/3ec14f0bfe2d045ac57b3133a7ae12ea8e70aa3c)
- added min(), max(), and sum() functionality on H2OFrames and respective pyunits [(github)](https://github.com/h2oai/h2o-dev/commit/c86cf2bfa396f38b2a035405553a1f4bb34f55c0)


##### Web UI

- View POJO in Flow [(#13769)](https://github.com/h2oai/h2o-3/issues/13769)
- help > about page or add version on main page for easy bug reporting. [(#13792)](https://github.com/h2oai/h2o-3/issues/13792)
- POJO generation: GLM [(#13703)](https://github.com/h2oai/h2o-3/issues/13703) [(github)](https://github.com/h2oai/h2o-dev/commit/35683e29e39489bc2349461e78524328e4b24e63)
- GLM model output, details in Flow [(private-#563)](https://github.com/h2oai/private-h2o-3/issues/563)


#### Enhancements

##### Algorithms

- K means output clean up [(private-#486)](https://github.com/h2oai/private-h2o-3/issues/486)
- Add FNR/TNR/FPR/TPR to threshold tables, remove recall, specificity [(github)](https://github.com/h2oai/h2o-dev/commit/1de4910b8d295b2eaa79b8e96422f45746458d92)
- Add accessor for variable importances for DL [(github)](https://github.com/h2oai/h2o-dev/commit/e11323bca7cc4e58fb2d899a3c307f42f4a8624e)
- Relax CM error tolerance for F1-optimal threshold now that AUC2 doesn't necessarily create consistent thresholds with its own CMs. [(github)](https://github.com/h2oai/h2o-dev/commit/3ab3af08e28a64acc9a406ef5ff19bf6b1c7855a)
- Added scoring history to glm [(github)](https://github.com/h2oai/h2o-dev/commit/a652ba0388784bb54f0a69f524d21f08d66eabc5)
- Added model summary to glm [(github)](https://github.com/h2oai/h2o-dev/commit/c0d221cb964a072358602b2c13fd2c33b9fa9f4b)
- Add flag to support reading data from S3N [(github)](https://github.com/h2oai/h2o-dev/commit/b4efd2c9802a8e39bc5d24ea6593e420ecfbaea9)
- Added degrees of freedom to GLM metrics schemas [(github)](https://github.com/h2oai/h2o-dev/commit/6f153381b085e94358cc0e5e317d36dce3072131)
- Allow DL scoring_history to be unlimited in length [(github)](https://github.com/h2oai/h2o-dev/commit/5485b46d240415afa3ff3e7bc8a532791ae12419)
- add plotting for binomial models [(github)](https://github.com/h2oai/h2o-dev/commit/d332e98a12bcd40ceb9714067eefce64dad97125)
- Ignore certain parameters that are not applicable (class balancing, max CM size, etc.) [(github)](https://github.com/h2oai/h2o-dev/commit/5c70787a6e43697f57c0df918bb4cdbf93d18018)
- Updated glm scoring, fill training/validation metrics in model output [(github)](https://github.com/h2oai/h2o-dev/commit/9b3cc3ec2a8f81771e0eddaf663dbfd6690dbd04)
- Rename gbm loss parameter to distribution [(github)](https://github.com/h2oai/h2o-dev/commit/d9a1e9730f3296bc125965647e5aef2ae114368c)
- Fix GBM naming: loss -> distribution [(github)](https://github.com/h2oai/h2o-dev/commit/ef93923dc83f03a9ef16ed23bb1c411bd26e067e)
- GLM LBFGS update [(github)](https://github.com/h2oai/h2o-dev/commit/3c75a2edc20b7abc9a17b9732a0bac9c7f194feb)
- na.rm for quantile is default behavior [(github)](https://github.com/h2oai/h2o-dev/commit/3ac19b6f1cb7e2a64fa6b783a19e8ddb42713caf)
- GLM update: enabled `max_predictors` in REST, updated lbfgs [(github)](https://github.com/h2oai/h2o-dev/commit/a58d515364e749b1147452a98399eb8dfadd11af)
- Remove `keep_cross_validation_splits` for now from DL [(github)](https://github.com/h2oai/h2o-dev/commit/569ae442a4905a3dbbf47a3d5c03461ce68be36a)
- Get rid of sigma in the model metrics, instead show r2 [(github)](https://github.com/h2oai/h2o-dev/commit/b12bf9496a46f25f066f3bab512cd7d81795f0f4)
- Don't show `score_every_iteration` for DL [(github)](https://github.com/h2oai/h2o-dev/commit/089aedfed90ca30e715a58363c19f3f1fe47318c)
- Don't print too large confusion matrices in Tree models [(github)](https://github.com/h2oai/h2o-dev/commit/56d51f51e5fdc5f9f25d8838003236909637b272)

##### API

- publish h2o-model.jar via REST API [(#13767)](https://github.com/h2oai/h2o-3/issues/13767)
- move all schemas and endpoints to v3 [(#13463)](https://github.com/h2oai/h2o-3/issues/13463)
- clean up routes (remove AddToNavbar, fix /Quantiles, etc) [(#13610)](https://github.com/h2oai/h2o-3/issues/13610) [(github)](https://github.com/h2oai/h2o-dev/commit/7f6eff5b47aa1e273de4710a3b26408e3516f5af)
- More data in chunk_homes call. Add num_chunks_per_vec. Add num_vec. [(github)](https://github.com/h2oai/h2o-dev/commit/635d020b2dfc45364331903c282e82e3f20d028d)
- Added chunk_homes route for frames [(github)](https://github.com/h2oai/h2o-dev/commit/1ae94079762fdbfcdd1e39d65578752860c278c6)
- Update to use /3 routes [(github)](https://github.com/h2oai/h2o-dev/commit/be422ff963bb47daf9c8e7cbcb478e6a6dbbaea5)

##### Python

- Python client should check that version number == server version number [(#13786)](https://github.com/h2oai/h2o-3/issues/13786)
- Add asfactor for month [(github)](https://github.com/h2oai/h2o-dev/commit/43c9b82ab463e712910d1353013d499684021858)
- in Expr.show() only show 10 or less rows. remove locate from runit test because full path used [(github)](https://github.com/h2oai/h2o-dev/commit/51f4f69deba9b76837b35bf2a0b85ee2e4b20db7)
- change nulls to () [(github)](https://github.com/h2oai/h2o-dev/commit/a138cc25edc9f948d263732f665d352e44ee39c1)
- sigma is no longer part of ModelMetricsRegressionV3 [(github)](https://github.com/h2oai/h2o-dev/commit/6f2a7390ce0feb0a3d880f1bb42168642a665bb0)


##### R

- Fix integer -> int in R [(github)](https://github.com/h2oai/h2o-dev/commit/ce05247e29b5756108999689d0b10fa17edb84a8)
- add autoencoder show method [(github)](https://github.com/h2oai/h2o-dev/commit/31d70f3ddb4bad63b42ec12c8fd70b9d5745a7d1)
- accessor is $ not @ [(github)](https://github.com/h2oai/h2o-dev/commit/a43e3d6924004e34aa7b5400d149c7dab26afe70)
- add `hit_ratio_table` and `varimp` calls to R [(github)](https://github.com/h2oai/h2o-dev/commit/caa7dc001edc63928ca7a8dadba773dd25983f1d)
- add h2o.predict as alternative [(github)](https://github.com/h2oai/h2o-dev/commit/e5a48f8faaededa3fd445d4b1415665c96f1291c)
- update model output in R [(github)](https://github.com/h2oai/h2o-dev/commit/e5d101ad60c12513f2e4c7b1d16534962eb86291)


##### System

- Port MissingValueInserter EndPoint to h2o-dev. [(#13457)](https://github.com/h2oai/h2o-3/issues/13457)
- Rapids: require a (put "key" %frame) [(#13856)](https://github.com/h2oai/h2o-3/issues/13856)
- Need pojo base model jar file embedded in h2o-dev via build process [(#13768)](https://github.com/h2oai/h2o-3/issues/13768) [(github)](https://github.com/h2oai/h2o-dev/commit/85f73202157f0ab4ee3487de8fc095951e761196)
- Make .json the default [(#13611)](https://github.com/h2oai/h2o-3/issues/13611) [(github)](https://github.com/h2oai/h2o-dev/commit/f3e88060da1a6af73940587c16fef669b1d5bbd5)
- Rename class for clarification [(github)](https://github.com/h2oai/h2o-dev/commit/89c4fe32d333940865112d8922249fc48eebe096)
- Classifies all NA columns as numeric. Also improves preview sampling accuracy by trimming partial lines at end of chunk. [(github)](https://github.com/h2oai/h2o-dev/commit/6b1cf7a180428c04cdd445974a318f5777c7f607)
- Implements sampling of files within the ParseSetup preview. This prevents poor column type guesses from only sampling the beginning of a file. [(github)](https://github.com/h2oai/h2o-dev/commit/038da7398941558656c1bda52b8429f4022c449e).
- Rename fields `drop_na20_col` [(github)](https://github.com/h2oai/h2o-dev/commit/75131e9f1e6d1cd6788f239d72e11cf104028c3f)
- allow for many deletes as final statements in a block [(github)](https://github.com/h2oai/h2o-dev/commit/aa3e2d3ef00761ca4a4c942f33ffaf80951abc7b)
- rename initF -> init_f, dropNA20Cols -> drop_na20_cols [(github)](https://github.com/h2oai/h2o-dev/commit/e81eae78267d4981c74d866e40a48015d2086371)
- Removed tweedie param [(github)](https://github.com/h2oai/h2o-dev/commit/03902225aa912473ceb01e9cce045846949faecf)
- thresholds -> threshold [(github)](https://github.com/h2oai/h2o-dev/commit/69adcc8639c889b68ca0c97b7385a45c41d93401)
- JSON of TwoDimTable with all null values in the first column (no row headers) now doesn't have an empty column for of "" or nulls. [(github)](https://github.com/h2oai/h2o-dev/commit/de54085fe94aaa1e23aa74254fc5b8b64b85f76d)
- move H2O_Load, fix all the timezone functions [(github)](https://github.com/h2oai/h2o-dev/commit/871959887825aec1e246ae8e19e11d03db9637c5)
- Add extra verbose printout in case Frames don't match identically [(github)](https://github.com/h2oai/h2o-dev/commit/b8943f9228fe996887377f521ec135745d957033)
- allow delayed column lookup [(github)](https://github.com/h2oai/h2o-dev/commit/5060436d4d7ea7363dc74b9c0850258a38b2715a)
- add mixed type list [(github)](https://github.com/h2oai/h2o-dev/commit/99eb7106eadb0fcbe815752b181e085ba57349db)
- Added WaterMeterIo to count persist info [(github)](https://github.com/h2oai/h2o-dev/commit/2fa38aaff08584bcbf92ee2287343c2c40765d76)
- Remove special setChunkSize code in HDFS and NFS file vec [(github)](https://github.com/h2oai/h2o-dev/commit/136e7667a438a856ff06478b8ba7f6b716aced7b)
- add check for Frame on string parse [(github)](https://github.com/h2oai/h2o-dev/commit/f835768b080df1bc395bdbe0f60c2d35db8da0d8)
- Disable Memory Cleaner [(github)](https://github.com/h2oai/h2o-dev/commit/644f38f38c9f75a0008cb012c25c399a06805786)
- Handle '<' chars in Keys when swapping [(github)](https://github.com/h2oai/h2o-dev/commit/65e936912f236cacacd706bc30406f13b46acf7e)
- allow for colnames in slicing [(github)](https://github.com/h2oai/h2o-dev/commit/947e6cc1f0becb58a5d36387a6500b303293c6a8)
- Adjusts parse type detection. If column is all one string value, declare it an enum [(github)](https://github.com/h2oai/h2o-dev/commit/08e7845b786c445862554d4f4c5dac7c78204284)

##### Web UI

- nice algo names in the Flow dropdown (full word names) [(#13698)](https://github.com/h2oai/h2o-3/issues/13698)
- Compute and Display Hit Ratios [(#13622)](https://github.com/h2oai/h2o-3/issues/13622)
- Limit POJO preview to 1000 lines [(github)](https://github.com/h2oai/h2o-dev/commit/ce82fe74da9641d72c47dabd03514c7402998f76)


#### Bug Fixes


##### Algorithms

- GLM: lasso i.e alpha =1 seems to be giving wrong answers [(#13758)](https://github.com/h2oai/h2o-3/issues/13758)
- AUC: h2o reports .5 auc when actual auc is 1 [(#13867)](https://github.com/h2oai/h2o-3/issues/13867)
- h2o.glm: No output displayed for the model [(#13846)](https://github.com/h2oai/h2o-3/issues/13846)
- h2o.glm model object output needs a fix [(#13803)](https://github.com/h2oai/h2o-3/issues/13803)
- h2o.glm model object says : fill me in GLMModelOutputV2; I think I'm redundant [1] FALSE [(#13754)](https://github.com/h2oai/h2o-3/issues/13754)
- GLM : Build GLM Model => Java Assertion error [(#13678)](https://github.com/h2oai/h2o-3/issues/13678)
- GLM :=> Progress shows -100% [(#13849)](https://github.com/h2oai/h2o-3/issues/13849)
- GBM: Negative sign missing in initF value for ad dataset [(#13868)](https://github.com/h2oai/h2o-3/issues/13868)
- K-Means takes a validation set but doesn't use it [(#13814)](https://github.com/h2oai/h2o-3/issues/13814)
- Absolute_MCC is NaN (sometimes) [(#13836)](https://github.com/h2oai/h2o-3/issues/13836) [(github)](https://github.com/h2oai/h2o-dev/commit/4480f22b6b3a38abb776339bee506b356f589c90)
- GBM: A proper error msg should be thrown when the user sets the max depth =0 [(#13826)](https://github.com/h2oai/h2o-3/issues/13826) [(github)](https://github.com/h2oai/h2o-dev/commit/df77f3de5e8940f3598af67d520f185d1e478ec4)
- DRF Regression Assertion Error [(#13812)](https://github.com/h2oai/h2o-3/issues/13812)
- h2o.randomForest: if h2o is not returning the mse for the 0th tree then it should not be reported in the model object [(#13799)](https://github.com/h2oai/h2o-3/issues/13799)
- GBM: Got exception `class java.lang.AssertionError` with msg `null` java.lang.AssertionError at hex.tree.gbm.GBM$GBMDriver$GammaPass.map [(#13685)](https://github.com/h2oai/h2o-3/issues/13685)
- GBM: Got exception `class java.lang.AssertionError` with msg `null` java.lang.AssertionError at hex.ModelMetricsMultinomial$MetricBuildMultinomial.perRow [(private-#434)](https://github.com/h2oai/private-h2o-3/issues/434)
- GBM get java.lang.AssertionError: Coldata 2199.0 out of range C17:5086.0-19733.0 step=57.214844 nbins=256 isInt=1 [(private-#439)](https://github.com/h2oai/private-h2o-3/issues/439)
- GLM: glmnet objective function better than h2o.glm [(#13739)](https://github.com/h2oai/h2o-3/issues/13739)
- GLM: get AIOOB:-36 at hex.glm.GLMTask$GLMIterationTask.postGlobal(GLMTask.java:733) [(#13880)](https://github.com/h2oai/h2o-3/issues/13880) [(github)](https://github.com/h2oai/h2o-dev/commit/5bba2df2e208a0a7c7fd19732971575eb9dc2259)
- Fixed glm behavior in case no rows are left after filtering out NAs [(github)](https://github.com/h2oai/h2o-dev/commit/57dc0f3a168ed835c48aa29f6e0d6322c6a5523a)
- Fix memory leak in validation scoring in K-Means [(github)](https://github.com/h2oai/h2o-dev/commit/f3f01e4dfe66e0181df0ff85a2a9a108295df94c)

##### API

- API unification: DataFrame should be able to accept URI referencing file on local filesystem [(#13700)](https://github.com/h2oai/h2o-3/issues/13700) [(github)](https://github.com/h2oai/h2o-dev/commit/a72e77388c0f7b17e4595482f9afe42f14055ce9)


##### Python

- Python: describe returning all zeros [(#13863)](https://github.com/h2oai/h2o-3/issues/13863)
- python/R & merge() [(#13822)](https://github.com/h2oai/h2o-3/issues/13822)
- python Expr min, max, median, sum bug [(#13833)](https://github.com/h2oai/h2o-3/issues/13833) [(github)](https://github.com/h2oai/h2o-dev/commit/7839efd5899366a3b51ef79156717a718ab01c38)




##### R

- (R and Python) clients must not pass response to DL AutoEncoder model builder [(#13883)](https://github.com/h2oai/h2o-3/issues/13883) [(github)](https://github.com/h2oai/h2o-dev/commit/bc78ecfa5e0c37cebd55ed9ba7b3ae6163ebdc66)
- h2o.varimp, h2o.hit_ratio_table missing in R [(#13830)](https://github.com/h2oai/h2o-3/issues/13830)
- GLM: No help for h2o.glm from R [(#13722)](https://github.com/h2oai/h2o-3/issues/13722)
- h2o.confusionMatrix not working for binary response [(#13770)](https://github.com/h2oai/h2o-3/issues/13770) [(github)](https://github.com/h2oai/h2o-dev/commit/a834cbc80a62062c55456233ce27ba5e9c3a87a3)
- h2o.splitframe complains about destination keys [(#13771)](https://github.com/h2oai/h2o-3/issues/13771)
- h2o.assign does not work [(#13772)](https://github.com/h2oai/h2o-3/issues/13772) [(github)](https://github.com/h2oai/h2o-dev/commit/b007c0b59dbb03716571384adb3271fbe8385a55)
- H2oR: should display only first few entries of the variable importance in model object [(#13838)](https://github.com/h2oai/h2o-3/issues/13838)
- R: h2o.confusion matrix needs formatting [(#13753)](https://github.com/h2oai/h2o-3/issues/13753)
- R: h2o.confusionMatrix => No Confusion Matrices for H2ORegressionMetrics [(#13701)](https://github.com/h2oai/h2o-3/issues/13701)
- h2o.deeplearning: model object output needs a fix [(#13809)](https://github.com/h2oai/h2o-3/issues/13809)
- h2o.varimp, h2o.hit_ratio_table missing in R [(#13830)](https://github.com/h2oai/h2o-3/issues/13830)
- force gc more frequently [(github)](https://github.com/h2oai/h2o-dev/commit/0db9a3716ecf573ef4b3c71ec1116cc8b27e62c6)

##### System

- MapR FS loads are too slow [(#13912)](https://github.com/h2oai/h2o-3/issues/13912)
- ensure that HDFS works from Windows [(#13800)](https://github.com/h2oai/h2o-3/issues/13800)
- Summary: on a time column throws,'null' is not an object (evaluating 'column.domain[level.index]') in Flow [(#13855)](https://github.com/h2oai/h2o-3/issues/13855)
- Parse: An enum column gets parsed as int for the attached file [(#13598)](https://github.com/h2oai/h2o-3/issues/13598)
- Parse => 40Mx1_uniques => class java.lang.RuntimeException [(#13719)](https://github.com/h2oai/h2o-3/issues/13719)
- if there are fewer than 5 unique values in a dataset column, mins/maxs reports e+308 values [(#13160)](https://github.com/h2oai/h2o-3/issues/13160) [(github)](https://github.com/h2oai/h2o-dev/commit/49c966791a146687039350689bc09cee10f38820)
- Sparkling water - `DataFrame[T_UUID]` to `SchemaRDD[StringType]` [(#13760)](https://github.com/h2oai/h2o-3/issues/13760)
- Sparkling water - `DataFrame[T_NUM(Long)]` to `SchemaRDD[LongType]` [(#13756)](https://github.com/h2oai/h2o-3/issues/13756)
- Sparkling water - `DataFrame[T_ENUM]` to `SchemaRDD[StringType]` [(#13755)](https://github.com/h2oai/h2o-3/issues/13755)
- Inconsistency in row and col slicing [(private-#424)](https://github.com/h2oai/private-h2o-3/issues/424) [(github)](https://github.com/h2oai/h2o-dev/commit/edd8923a438282e3c24d086e1a03b88471d58114)
- rep_len expects literal length only [(private-#421)](https://github.com/h2oai/private-h2o-3/issues/421) [(github)](https://github.com/h2oai/h2o-dev/commit/1783a889a54d2b23da8bd8ec42774f52efbebc60)
- cbind and = don't work within a single rapids block [(private-#443)](https://github.com/h2oai/private-h2o-3/issues/443)
- Rapids response for c(value) does not have frame key [(private-#430)](https://github.com/h2oai/private-h2o-3/issues/430)
- S3 parse takes forever [(#13864)](https://github.com/h2oai/h2o-3/issues/13864)
- Parse => Enum unification fails in multi-node parse [(#13709)](https://github.com/h2oai/h2o-3/issues/13709) [(github)](https://github.com/h2oai/h2o-dev/commit/0db8c392070583f32849447b65784da18197c14d)
- All nodes are not getting updated with latest status of each other nodes info [(#13757)](https://github.com/h2oai/h2o-3/issues/13757)
- Cluster creation is sometimes rejecting new nodes (post jenkins-master-1128+) [(#13795)](https://github.com/h2oai/h2o-3/issues/13795)
- Parse => Multiple files 1 zip/ 1 csv gives Array index out of bounds [(#13828)](https://github.com/h2oai/h2o-3/issues/13828)
- Parse => failed for X5MRows6KCols ==> OOM => Cluster dies [(#13824)](https://github.com/h2oai/h2o-3/issues/13824)
- /frame/foo pagination weirded out [(private-#412)](https://github.com/h2oai/private-h2o-3/issues/412) [(github)](https://github.com/h2oai/h2o-dev/commit/c40da923d97720466fb372758d66509aa628e97c)
- Removed code that flipped enums to strings [(github)](https://github.com/h2oai/h2o-dev/commit/7d56bcee73cf3c90b498cadf8601610e5f145dbc)




##### Web UI

- Flow: It would be really useful to have the mse plots back in GBM [(#13875)](https://github.com/h2oai/h2o-3/issues/13875)
- State change in Flow is not fully validated [(#13904)](https://github.com/h2oai/h2o-3/issues/13904)
- Flows : Not able to load saved flows from hdfs [(#13860)](https://github.com/h2oai/h2o-3/issues/13860)
- Save Function in Flow crashes [(#13774)](https://github.com/h2oai/h2o-3/issues/13774) [(github)](https://github.com/h2oai/h2o-dev/commit/ad724bf7af86180d7045a99790602bd52908945f)
- Flow: should throw a proper error msg when user supplied response have more categories than algo can handle [(#13854)](https://github.com/h2oai/h2o-3/issues/13854)
- Flow display of a summary of a column with all missing values fails. [(private-#450)](https://github.com/h2oai/private-h2o-3/issues/450)
- Split frame UI improvements [(private-#414)](https://github.com/h2oai/private-h2o-3/issues/414)
- Flow : Decimal point precisions to be consistent to 4 as in h2o1 [(#13832)](https://github.com/h2oai/h2o-3/issues/13832)
- Flow: Prediction frame is outputing junk info [(#13813)](https://github.com/h2oai/h2o-3/issues/13813)
- EC2 => Cluster of 16 nodes => Water Meter => shows blank page [(#13819)](https://github.com/h2oai/h2o-3/issues/13819)
- Flow: Predict - "undefined is not an object (evaluating `prediction.thresholds_and_metric_scores.name`) [(#13540)](https://github.com/h2oai/h2o-3/issues/13540)
- Flow: inspect getModel for PCA returns error [(#13602)](https://github.com/h2oai/h2o-3/issues/13602)
- Flow, RF: Can't get Predict results; "undefined is not an object (evaluating `prediction.confusion_matrices.length`)" [(#13687)](https://github.com/h2oai/h2o-3/issues/13687)
- Flow, GBM: getModel is broken -Error processing GET /3/Models.json/gbm-b1641e2dc3-4bad-9f69-a5f4b67051ba null is not an object (evaluating `source.length`) [(#13787)](https://github.com/h2oai/h2o-3/issues/13787)



### Severi (0.2.2.1) - 4/10/15


#### New Features

##### R

- Implement /3/Frames/<my_frame>/summary [(#13020)](https://github.com/h2oai/h2o-3/issues/13020) [(github)](https://github.com/h2oai/h2o-dev/commit/07bc295e1687d88e40d8391ea78f91aff4183a6f)
- add allparameters slot to allow default values to be shown [(github)](https://github.com/h2oai/h2o-dev/commit/9699a4c43ce4936dbc3019c75b2a36bd1ef22b45)
- add log loss accessor [(github)](https://github.com/h2oai/h2o-dev/commit/22ace748ae4004305ae9edb04f17141d0dbd87d4)


#### Enhancements

##### Algorithms

- POJO generation: GBM [(#13704)](https://github.com/h2oai/h2o-3/issues/13704)
- POJO generation: DRF [(#13705)](https://github.com/h2oai/h2o-3/issues/13705)
- Compute and Display Hit Ratios [(#13622)](https://github.com/h2oai/h2o-3/issues/13622) [(github)](https://github.com/h2oai/h2o-dev/commit/04b13f2fb05b752dbd04121f50845bebcb6f9955)
- Add DL POJO scoring [(#13578)](https://github.com/h2oai/h2o-3/issues/13578)
- Allow validation dataset for AutoEncoder [(#13574)](https://github.com/h2oai/h2o-3/issues/13574)
- #13573: Add log loss to binomial and multinomial model metric [(github)](https://github.com/h2oai/h2o-dev/commit/8982a0a1ba575bd5ca6ca3e854382e03146743cd)
- Port MissingValueInserter EndPoint to h2o-dev [(#13457)](https://github.com/h2oai/h2o-3/issues/13457)
- increase tolerance to 2e-3 (was 1e-3 ..failed with 0.001647 relative difference [(github)](https://github.com/h2oai/h2o-dev/commit/9ce26530cc7d4d4aef55b5e0debc978bacc8ac78)
- change tolerance to 1e-3 [(github)](https://github.com/h2oai/h2o-dev/commit/bb5aa7806d37e1148029ef848a8df0d7a28cba2a)
- Add option to export weights and biases to REST API / Flow. [(github)](https://github.com/h2oai/h2o-dev/commit/2f711045f2678622a7d6d44f7210adb74a513ce6)
- Add scree plot for H2O PCA models and fix Runit test. [(github)](https://github.com/h2oai/h2o-dev/commit/5743019075e023590019fab9a4da8c09500643a0)
- Remove quantiles from the model builders list. [(github)](https://github.com/h2oai/h2o-dev/commit/6283dfbc626cb2b9a65df2f4b90a87371ef5c752)
- GLM update: added row filtering argument to line search task, fixed issues with dfork/asyncExec [(github)](https://github.com/h2oai/h2o-dev/commit/7492ed95915a85121f0042b5800d58bda2805a87)
- Updated rho-setting in GLM. [(github)](https://github.com/h2oai/h2o-dev/commit/a130fd6abbd13fff44e0eb813d31cc04afcedef7)
- No threshold 0.5; use the default (max F1) instead [(github)](https://github.com/h2oai/h2o-dev/commit/e56425d6f83aa0e1dc523acc3ed4b5a49d0223fc)
- GLM update: updated initilization, NA row filtering, default lambda is now empty, will be picked based on the fraction of lambda_max. [(github)](https://github.com/h2oai/h2o-dev/commit/04a3f8e496c00de9e35c8ee33a6d3ddb8466a3d8)
- Updated ADMM solver. [(github)](https://github.com/h2oai/h2o-dev/commit/1a6ef44a24463b2538731065fc39eef4531e062e)
- Added makeGLMModel call. [(github)](https://github.com/h2oai/h2o-dev/commit/9792ff032356982915d814c7918c48582bf3ffea)
- Start with classification error NaN at t=0 for DL, not with 1. [(github)](https://github.com/h2oai/h2o-dev/commit/c33ca1f385844c90c473fe2941bbb8b2c2ab663f)
- Relax DL POJO relative tolerance to 1e-2. [(github)](https://github.com/h2oai/h2o-dev/commit/f7a2fe37845c00980a23f8e68b34ad044fa647e2)
- Override nfeatures() method in DLModelOutput. [(github)](https://github.com/h2oai/h2o-dev/commit/7c6bcf844c8e162b8fb16ee1f7e208717b82d606)
- Renaming of fields in GLM [(github)](https://github.com/h2oai/h2o-dev/commit/d21180ab5ea973848d4cdcb896c32400c3d77d38)
- GLM: Take out Balance Classes [(#13782)](https://github.com/h2oai/h2o-3/issues/13782)



##### API

- schema metadata for Map fields should include the key and value types [(#13742)](https://github.com/h2oai/h2o-3/issues/13742) [(github)](https://github.com/h2oai/h2o-dev/commit/4b55db36f259740043b8418e23e298fb0ed5a43d)
- schema metadata should include the superclass [(#13743)](https://github.com/h2oai/h2o-3/issues/13743)
- rest api naming convention: n_folds vs ntrees [(#13727)](https://github.com/h2oai/h2o-3/issues/13727)
- schema metadata for Map fields should include the key and value types [(#13742)](https://github.com/h2oai/h2o-3/issues/13742)
- Create REST Endpoint for exposing .java pojo models [(#13766)](https://github.com/h2oai/h2o-3/issues/13766)






##### Python

- Run GLM from Python (including LBFGS) [(private-#567)](https://github.com/h2oai/private-h2o-3/issues/567)
- added H2OFrame show(), as_list(), and slicing pyunits [(github)](https://github.com/h2oai/h2o-dev/commit/b1febc33faa336924ffdb416d8d4a3cb8bba37fa)
- changed solver parameter to "L_BFGS" [(github)](https://github.com/h2oai/h2o-dev/commit/93e71509bcfa0e76d344819214a08b944ccbfb89)
- added multidimensional slicing of H2OFrames and Exprs. [(github)](https://github.com/h2oai/h2o-dev/commit/7d9be09ff0b68f92e46a0c7336dcf8134d026b88)
- add h2o.groupby to python interface [(github)](https://github.com/h2oai/h2o-dev/commit/aee9522f0c7edbd960ded78f5ba01daf6d54925b)
- added H2OModel.confusionMatrix() to return confusion matrix of a prediction [(github)](https://github.com/h2oai/h2o-dev/commit/6e6bc378f3a10c094752470de786be600a0a98b3)





##### R

- #13571, #13571, #13571.
  -R client now sends the data frame column names and data types to ParseSetup.
  -R client can get column names from a parsed frame or a list.
  -Respects client request for column data types [(github)](https://github.com/h2oai/h2o-dev/commit/ba063be25d3fbb658b016ff514083284e2d95d78)
- R: Cannot create new columns through R [(#13565)](https://github.com/h2oai/h2o-3/issues/13565)
- H2O-R: it would be more useful if h2o.confusion matrix reports the actual class labels instead of [,1] and [,2] [(#13536)](https://github.com/h2oai/h2o-3/issues/13536)
- Support both multinomial and binomial CM [(github)](https://github.com/h2oai/h2o-dev/commit/4ad2ed007635a7e8c2fd4fb0ae985cf00a81df15)



##### System

- Flow: Standardize `max_iters`/`max_iterations` parameters [(#13439)](https://github.com/h2oai/h2o-3/issues/13439) [(github)](https://github.com/h2oai/h2o-dev/commit/6586f1f2f233518a7ee6179ec2bc19d9d7b61d15)
- Add ERROR logging level for too-many-retries case [(#13156)](https://github.com/h2oai/h2o-3/issues/13156) [(github)](https://github.com/h2oai/h2o-dev/commit/ae5bdf26453643b58403a6a4fb136259ac9acd6b)
- Simplify checking of cluster health. Just report the status immediately. [(github)](https://github.com/h2oai/h2o-dev/commit/25fde3914460e7572cf3500f236d43e50a502aab)
- reduce timeout [(github)](https://github.com/h2oai/h2o-dev/commit/4c93ddfd92801fdef60961d44ccb7cf512f37a90)
- strings can have ' or " beginning [(github)](https://github.com/h2oai/h2o-dev/commit/034243f094ae67fb15e8d575146f6e64c8727d39)
- Throw a validation error in flow if any training data cols are non-numeric [(github)](https://github.com/h2oai/h2o-dev/commit/091c18331f19a5a1db8b3eb0b000ca72abd29f81)
- Add getHdfsHomeDirectory(). [(github)](https://github.com/h2oai/h2o-dev/commit/68c3f730576c21bd1191f8af9dd7fd9445b89f83)
- Added --verbose.  [(github)](https://github.com/h2oai/h2o-dev/commit/5e772f8314a340666e4e80b3480b2105ceb91251)


##### Web UI

- #13698: nice algo names in the Flow dropdown (full word names) [(github)](https://github.com/h2oai/h2o-dev/commit/ab87c26ae8ac17691034f4d9014ee17ba2168d89)
- Unbreak Flow's ConfusionMatrix display. [(github)](https://github.com/h2oai/h2o-dev/commit/45911f2ff28e2357d5545ac23135f090c10f13e0)
- POJO generation: DL [(#13706)](https://github.com/h2oai/h2o-3/issues/13706)



#### Bug Fixes


##### Algorithms

- GLM : Build GLM model with nfolds brings down the cloud => FATAL: unimplemented [(#13721)](https://github.com/h2oai/h2o-3/issues/13721) [(github)](https://github.com/h2oai/h2o-dev/commit/79123971fdea5660355f57de4e9a02d3712250b1)
- DL : Build DL Model => FATAL: unimplemented: n_folds >= 2 is not (yet) implemented => SHUTSDOWN CLOUD [(#13718)](https://github.com/h2oai/h2o-3/issues/13718) [(github)](https://github.com/h2oai/h2o-dev/commit/6f59755f28c3fc3cee549630bb5e22a985d185ab)
- GBM => Build GBM model => No enum constant  hex.tree.gbm.GBMModel.GBMParameters.Family.AUTO [(#13714)](https://github.com/h2oai/h2o-3/issues/13714)
- GBM: When run with loss = auto with a numeric column get- error :No enum constant hex.tree.gbm.GBMModel.GBMParameters.Family.AUTO
  [(#13699)](https://github.com/h2oai/h2o-3/issues/13699) [(github)](https://github.com/h2oai/h2o-dev/commit/15d5b5a6108d165f230a856aa3c38a4eb158ee93)
- gbm: does not complain when min_row >dataset size [(#13686)](https://github.com/h2oai/h2o-3/issues/13686) [(github)](https://github.com/h2oai/h2o-dev/commit/a3d9d1cca2aa070c536084ca1bb90eecfbf609e7)
- GLM: reports wrong residual degrees of freedom [(#13660)](https://github.com/h2oai/h2o-3/issues/13660)
- H2O dev reports less accurate aucs than H2O [(#13594)](https://github.com/h2oai/h2o-3/issues/13594)
- GLM : Build GLM model fails => ArrayIndexOutOfBoundsException [(#13593)](https://github.com/h2oai/h2o-3/issues/13593)
- divide by zero in modelmetrics for deep learning [(#13546)](https://github.com/h2oai/h2o-3/issues/13546)
- GBM: reports 0th tree mse value for the validation set, different than the train set ,When only train sets is provided [(#13562)](https://github.com/h2oai/h2o-3/issues/13562)
- GBM: Initial mse in bernoulli seems to be off [(#13556)](https://github.com/h2oai/h2o-3/issues/13556)
- GLM : Build Model fails with Array Index Out of Bound exception [(#13446)](https://github.com/h2oai/h2o-3/issues/13446) [(github)](https://github.com/h2oai/h2o-dev/commit/78773be9f40e1403457e42378baf0d1aeaf3e32d)
- Custom Functions don't work in apply() in R [(#13431)](https://github.com/h2oai/h2o-3/issues/13431)
- GLM failure: got NaNs and/or Infs in beta on airlines [(#13362)](https://github.com/h2oai/h2o-3/issues/13362)
- MetricBuilderMultinomial.perRow AssertionError while running GBM [(private-#440)](https://github.com/h2oai/private-h2o-3/issues/440)
- Problems during Train/Test adaptation between Enum/Numeric [(private-#451)](https://github.com/h2oai/private-h2o-3/issues/451)
- DRF/GBM balance_classes=True throws unimplemented exception [(private-#454)](https://github.com/h2oai/private-h2o-3/issues/454) [(github)](https://github.com/h2oai/h2o-dev/commit/3a4f7ee3fdb159187b5ae1789d55752192d893e6)
- AUC reported on training data is 0, but should be 1 [(private-#457)](https://github.com/h2oai/private-h2o-3/issues/457) [(github)](https://github.com/h2oai/h2o-dev/commit/312558524749a0b28bf22ffd8c34ebcd6996b350)
- glm pyunit intermittent failure [(private-#477)](https://github.com/h2oai/private-h2o-3/issues/477)
- Inconsistency in GBM results:Gives different results even when run with the same set of params [(private-#480)](https://github.com/h2oai/private-h2o-3/issues/480)
- get rid of nfolds= param since it's not supported in GLM yet [(￼github)](https://github.com/h2oai/h2o-dev/commit/8603ad35d4243ef598acadbfaa084c6852acd7ce)
- Fixed degrees of freedom (off by 1) in glm, added test. [(github)](https://github.com/h2oai/h2o-dev/commit/09e6d6f5222c40cb73f28c6df4e30d92b98f8361)
- GLM fix: fix filtering of rows with NAs and fix in sparse handling. [(github)](https://github.com/h2oai/h2o-dev/commit/5bad9b5c7bc2a3a4d4a2496ade7194a0438f17d9)
- Fix GLM job fail path to call Job.fail(). [(github)](https://github.com/h2oai/h2o-dev/commit/912663fb0e05b4670d014a0a4c7bff03410c467e)
- Full AUC computation, bug fixes [(github)](https://github.com/h2oai/h2o-dev/commit/9124cc321defb0b4defba7bef02cf387ff238c28)
- Fix ADMM for upper/lower bounds. (updated rho settings + update u-vector in ADMM for intercept) [(github)](https://github.com/h2oai/h2o-dev/commit/47a09ffe2271db050bd6d8042dfeaa40c4874b8a)
- Few glm fixes [(github)](https://github.com/h2oai/h2o-dev/commit/04a344ebede1f34b58e9aa82889bac1af9bd5f47)
- DL : KDD Algebra data set => Build DL model => ArrayIndexOutOfBoundsException [(#13688)](https://github.com/h2oai/h2o-3/issues/13688)
- GBm: Dev vs H2O for depth 5, minrow=10, on prostate, give different trees [(#13748)](https://github.com/h2oai/h2o-3/issues/13748)
- GBM param min_rows doesn't throw exception for negative values [(#13689)](https://github.com/h2oai/h2o-3/issues/13689)
- GBM : Build GBM Model => Too many levels in response column! (java.lang.IllegalArgumentException) => Should display proper error message [(#13690)](https://github.com/h2oai/h2o-3/issues/13690)
- GBM:Got exception 'class java.lang.AssertionError', with msg 'Something is wrong with GBM trees since returned prediction is Infinity [(#13713)](https://github.com/h2oai/h2o-3/issues/13713)


##### API

- Cannot adapt numeric response to factors made from numbers [(#13612)](https://github.com/h2oai/h2o-3/issues/13612)
- not specifying response\_column gets NPE (deep learning build_model()) I think other algos might have same thing [(#13148)](https://github.com/h2oai/h2o-3/issues/13148)
- NPE response has null msg, exception\_msg and dev\_msg [(private-#455)](https://github.com/h2oai/private-h2o-3/issues/455)
- Flow :=> Save Flow => On Mac and Windows 8.1 => NodePersistentStorage failure while attempting to overwrite (?) a flow [(#13558)](https://github.com/h2oai/h2o-3/issues/13558) [(github)](https://github.com/h2oai/h2o-dev/commit/db710a4dc7dda4570f5b87cb9e386be6c76f001e)
- the can_build field in ModelBuilderSchema needs values[] to be set [(#13744)](https://github.com/h2oai/h2o-3/issues/13744)
- value field in the field metadata isn't getting serialized as its native type [(#13745)](https://github.com/h2oai/h2o-3/issues/13745)


##### Python

- python api asfactor() on -1/1 column issue [(private-#474)](https://github.com/h2oai/private-h2o-3/issues/474)


##### R

- Rapids: Operations %/% and %% returns Illegal Argument Exception in R [(#13726)](https://github.com/h2oai/h2o-3/issues/13726)
- quantile: H2oR displays wrong quantile values when call the default quantile without specifying the probs [(#13680)](https://github.com/h2oai/h2o-3/issues/13680)[(github)](https://github.com/h2oai/h2o-dev/commit/9ef5e2befe08a5ff7ce13e8b4b39acf7171e8a1f)
- as.factor: If a user reruns as.factor on an already factor column, h2o should not show an exception [(#13614)](https://github.com/h2oai/h2o-3/issues/13614)
- as.factor works only on positive integers [(#13609)](https://github.com/h2oai/h2o-3/issues/13609) [(github)](https://github.com/h2oai/h2o-dev/commit/08f3acb62bec0f2c3808841d6b7f8d1382f616f0)
- H2O-R: model detail lists three mses, the first MSE slot does not contain any info about the model and hence, should be removed from the model details [(#13597)](https://github.com/h2oai/h2o-3/issues/13597) [(github)](https://github.com/h2oai/h2o-dev/commit/55f975d551432114a0088d19bd2397894410dd94)
- H2O-R: Strings: While slicing get Error From H2O: water.DException$DistributedException [(#13585)](https://github.com/h2oai/h2o-3/issues/13585)
- R: h2o.confusionMatrix should handle both models and model metric objects [(#13583)](https://github.com/h2oai/h2o-3/issues/13583)
- R: as.Date not functional with H2O objects [(#13576)](https://github.com/h2oai/h2o-3/issues/13576) [(github)](https://github.com/h2oai/h2o-dev/commit/f2f64b1ed29c8d7ab47252d84d8634240b3889d0)
- R: some apply functions don't work on H2OFrame objects [(#13572)](https://github.com/h2oai/h2o-3/issues/13572) [(github)](https://github.com/h2oai/h2o-dev/commit/10f1245dbbc5ac36024e8ce51932dd991ff50688)
- h2o.confusionMatrices for multinomial does not work [(#13570)](https://github.com/h2oai/h2o-3/issues/13570)
- R: slicing issues [(#13567)](https://github.com/h2oai/h2o-3/issues/13567)
- R: length and is.factor don't work in h2o.ddply [(#13566)](https://github.com/h2oai/h2o-3/issues/13566) [(github)](https://github.com/h2oai/h2o-dev/commit/bdc55a95a91af784a8b4497bbc8e4835fa1049bf)
- R: apply(hex, c(1,2), ...) doesn't properly raise an error [(#13548)](https://github.com/h2oai/h2o-3/issues/13548) [(github)](https://github.com/h2oai/h2o-dev/commit/75ddf7f82b4acabe77d0928b66ea7a51dbc5a8b4)
- R: Slicing negative indices to negative indices fails [(#13547)](https://github.com/h2oai/h2o-3/issues/13547) [(github)](https://github.com/h2oai/h2o-dev/commit/bf6620f70a3f09a8a57d2da563188c342d67aeb7)
- h2o.ddply: doesn't accept anonymous functions [(#13545)](https://github.com/h2oai/h2o-3/issues/13545) [(github)](https://github.com/h2oai/h2o-dev/commit/3c3c4e7134fe03e5a8a5cdd8530f59094264b7f3)
- ifelse() cannot return H2OFrames in R [(#13526)](https://github.com/h2oai/h2o-3/issues/13526)
- as.h2o loses track of headers [(#13524)](https://github.com/h2oai/h2o-3/issues/13524)
- H2O-R not showing meaningful error msg [(#13493)](https://github.com/h2oai/h2o-3/issues/13493)
- H2O.fail() had better fail [(#13462)](https://github.com/h2oai/h2o-3/issues/13462) [(github)](https://github.com/h2oai/h2o-dev/commit/16939a831a315c5f7ec221bc15fad5826fd4c677)
- fix issue in toEnum [(github)](https://github.com/h2oai/h2o-dev/commit/99fe517a00f54dea9ca4e64054c06a6e8cd1ea8c)
- fix colnames and new col creation [(github)](https://github.com/h2oai/h2o-dev/commit/61000a75eaa3b9a92dced1c66ecdce687cef64b2)
- R: h2o.init() is posting warning messages of an unhealthy cluster when the cluster is fine. [(#13724)](https://github.com/h2oai/h2o-3/issues/13724)
- h2o.split frame is failing [(#13541)](https://github.com/h2oai/h2o-3/issues/13541)





##### System

- key type failure should fail the request, not the cloud [(#13729)](https://github.com/h2oai/h2o-3/issues/13729) [(github)](https://github.com/h2oai/h2o-dev/commit/52ebdf0cd6d972acb15c8cf315e2d1105c5b1703)
- Parse => Import Medicare supplier file => Parse = > Illegal argument for field: column_names of schema: ParseV2: string and key arrays' values must be quoted, but the client sent: " [(#13710)](https://github.com/h2oai/h2o-3/issues/13710)
- Overwriting a constant vector with strings fails [(#13693)](https://github.com/h2oai/h2o-3/issues/13693)
- H2O - gets stuck while calculating quantile,no error msg, just keeps running a job that normally takes less than a sec [(#13677)](https://github.com/h2oai/h2o-3/issues/13677)
- Summary and quantile on a column with all missing values should not throw an exception [(#13665)](https://github.com/h2oai/h2o-3/issues/13665) [(github)](https://github.com/h2oai/h2o-dev/commit/7acd14a7d6bbdfa5ab6a7c2e8c2987622b229603)
- View Logs => class java.lang.RuntimeException: java.lang.IllegalArgumentException: File /home2/hdp/yarn/usercache/neeraja/appcache/application_1427144101512_0039/h2ologs/h2o_172.16.2.185_54321-3-info.log does not exist [(#13592)](https://github.com/h2oai/h2o-3/issues/13592)
- Parse: After parsing Chicago crime dataset => Not able to build models or Get frames [(#15418)](https://github.com/h2oai/h2o-3/issues/15418)
- Parse: Numbers completely parsed wrong [(#13568)](https://github.com/h2oai/h2o-3/issues/13568)
- Flow: converting a column to enum while parsing does not work [(#13564)](https://github.com/h2oai/h2o-3/issues/13564)
- Parse: Fail gracefully when asked to parse a zip file with different files in it [(#13557)](https://github.com/h2oai/h2o-3/issues/13557)[(github)](https://github.com/h2oai/h2o-dev/commit/23a60d68e9d77fe07ae9d940b0ebb6636ef40ee3)
- toDataFrame doesn't support sequence format schema (array, vectorUDT) [(#13449)](https://github.com/h2oai/h2o-3/issues/13449)
- Parse : Parsing random crap gives java.lang.ArrayIndexOutOfBoundsException: 13 [(#13420)](https://github.com/h2oai/h2o-3/issues/13420)
- The quote stripper for column names should report when the stripped chars are not the expected quotes [(￼#13416)](https://github.com/h2oai/h2o-3/issues/13416)
- import directory with large files,then Frames..really slow and disk grinds. Files are unparsed. Shouldn't be grinding [(#13113)](https://github.com/h2oai/h2o-3/issues/13113)
- NodePersistentStorage gets wiped out when hadoop cluster is restarted [(private-#488)](https://github.com/h2oai/private-h2o-3/issues/488)
- h2o.exec won't be supported [(github)](https://github.com/h2oai/h2o-dev/commit/81f685e5abb990d7f7669b137cfb07d7b01ea471)
- fixed import issue [(github)](https://github.com/h2oai/h2o-dev/commit/addf5b85b91b77366bca0a8c900ca2d308f29a09)
- fixed init param [(github)](https://github.com/h2oai/h2o-dev/commit/d459d1a7fb405f8a1f7b466caae99281feae370c)
- fix repeat as.factor NPE [(github)](https://github.com/h2oai/h2o-dev/commit/49fb24417ecfe26975fbff14bef084da50a034c7)
- startH2O set to False in init [(github)](https://github.com/h2oai/h2o-dev/commit/53ca9baf1bd70cd04b2ad03243eb9c7053300c52)
- hang on glm job removal [(#13717)](https://github.com/h2oai/h2o-3/issues/13717)
- Flow - changed column types need to be reflected in parsed data [(private-#484)](https://github.com/h2oai/private-h2o-3/issues/484)
- water.DException$DistributedException while running kmeans in multinode cluster [(#13682)](https://github.com/h2oai/h2o-3/issues/13682)
- Frame inspection prior to file parsing, corrupts parsing [(#13417)](https://github.com/h2oai/h2o-3/issues/13417)






##### Web UI

- Flow, DL: Need better fail message if "Autoencoder" and "use_all_factor_levels" are both selected [(#13715)](https://github.com/h2oai/h2o-3/issues/13715)
- When select AUTO while building a gbm model get ERROR FETCHING INITIAL MODEL BUILDER STATE [(#13588)](https://github.com/h2oai/h2o-3/issues/13588)
- Flow : Build h2o-dev-0.1.17.1009 : Building GLM model gives java.lang.ArrayIndexOutOfBoundsException: [(#13215)](https://github.com/h2oai/h2o-3/issues/13215) [(github)](https://github.com/h2oai/h2o-dev/commit/fe3cdad806750f6add0fc4c03bee9e66d61c59fa)
- Flow:Summary on flow broken for a long time [(#13773)](https://github.com/h2oai/h2o-3/issues/13773)

---

###  Serre (0.2.1.1) - 3/18/15

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o-dev/rel-serre/1/index.html'>http://h2o-release.s3.amazonaws.com/h2o-dev/rel-serre/1/index.html</a>




#### New Features


##### Algorithms

- Naive Bayes in H2O-dev [(#13171)](https://github.com/h2oai/h2o-3/issues/13171)
- GLM model output, details from R [(private-#565)](https://github.com/h2oai/private-h2o-3/issues/565)
- Run GLM Regression from Flow (including LBFGS) [(private-#549)](https://github.com/h2oai/private-h2o-3/issues/549)
- PCA [(#13170)](https://github.com/h2oai/h2o-3/issues/13170)
- Port Random Forest to h2o-dev [(#13447)](https://github.com/h2oai/h2o-3/issues/13447)
- Enable DRF model output [(github)](https://github.com/h2oai/h2o-flow/commit/44ee1bf98dd69f33251a7a959b1000cc7f290427)
- Add DRF to Flow (Model Output) [(#13517)](https://github.com/h2oai/h2o-3/issues/13517)
- Grid for GBM [(github)](https://github.com/h2oai/h2o-dev/commit/ce96d2859aa86e4df393a13e00fbb7fcf603c166)
- Run Deep Learning Regression from Flow [(private-#550)](https://github.com/h2oai/private-h2o-3/issues/550)

##### Python

- Add Python wrapper for DRF [(#13518)](https://github.com/h2oai/h2o-3/issues/13518)


##### R

- Add R wrapper for DRF [(#13514)](https://github.com/h2oai/h2o-3/issues/13514)


##### System

- Include uploadFile [(#13296)](https://github.com/h2oai/h2o-3/issues/13296) [(github)](https://github.com/h2oai/h2o-flow/commit/3f8fb91cf6d81aefdb0ad6deee801084e0cf864f)
- Added -flow_dir to hadoop driver [(github)](https://github.com/h2oai/h2o-dev/commit/9883b4d98ae0056e88db449ce1ebd20394d191ac)



##### Web UI

- Add Flow packs [(private-#483)](https://github.com/h2oai/private-h2o-3/issues/483) [(#13261)](https://github.com/h2oai/h2o-3/issues/13261)
- Integrate H2O Help inside Help panel [(#13124)](https://github.com/h2oai/h2o-3/issues/13124) [(github)](https://github.com/h2oai/h2o-flow/commit/62e3c06e91bc0576e15516381bb59f31dbdf38ca)
- Add quick toggle button to show/hide the sidebar [(github)](https://github.com/h2oai/h2o-flow/commit/b5fb2b54a04850c9b24bb0eb03769cb519039de6)
- Add New, Open toolbar buttons [(github)](https://github.com/h2oai/h2o-flow/commit/b6efd33c9c8c2f5fe73e9ba83c1441d768ec47f7)
- Auto-refresh data preview when parse setup input parameters are changed [(#13516)](https://github.com/h2oai/h2o-3/issues/13516)
- Flow: Add playbar with Run, Continue, Pause, Progress controls [(private-#481)](https://github.com/h2oai/private-h2o-3/issues/481)
- You can now stop/cancel a running flow


#### Enhancements


##### Algorithms

- Display GLM coefficients only if available [(#13458)](https://github.com/h2oai/h2o-3/issues/13458)
- Add random chance line to RoC chart [(private-#496)](https://github.com/h2oai/private-h2o-3/issues/496)
- Speed up DLSpiral test. Ignore Neurons test (MatVec) [(github)](https://github.com/h2oai/h2o-dev/commit/822862aa29fb63e52703ce91794a64e49bb96aed)
- Use getRNG for Dropout [(github)](https://github.com/h2oai/h2o-dev/commit/94a5b4e46a4501e85fb4889e5c8b196c46f74525)
- #13590: Add tests for determinism of RNGs [(github)](https://github.com/h2oai/h2o-dev/commit/e77c3ead2151a1202ec0b9c467641bc1c787e122)
- #13590: Implement Chi-Square test for RNGs [(github)](https://github.com/h2oai/h2o-dev/commit/690dd333c6bf51ff4e223cd15ef9dab004ed8904)
- Add DL model output toString() [(github)](https://github.com/h2oai/h2o-dev/commit/d206bb5b9996e87e8c0058dd8f1d7580d1ea0bb1)
- Add LogLoss to MultiNomial ModelMetrics [(#13573)](https://github.com/h2oai/h2o-3/issues/13573)
- Print number of categorical levels once we hit >1000 input neurons. [(github)](https://github.com/h2oai/h2o-dev/commit/ccf645af908d4964db3bc36a98c4ff9868838dc6)
- Updated the loss behavior for GBM. When loss is set to AUTO, if the response is an integer with 2 levels, then bernoullli (rather than gaussian) behavior is chosen. As a result, the `do_classification` flag is no longer necessary in Flow, since the loss completely specifies the desired behavior, and R users no longer to use `as.factor()` in their response to get the desired bernoulli behavior. The `score_each_iteration` flag has been removed as well. [(github)](https://github.com/h2oai/h2o-dev/commit/cc971e00869197625fefec894ab705c79db05fbb)
- Fully remove `_convert_to_enum` in all algos [(github)](https://github.com/h2oai/h2o-dev/commit/7fdf5d98c1f7caf88a3a928a28b2f86b06c5b2eb)
- Port MissingValueInserter EndPoint to h2o-dev. [(#13457)](https://github.com/h2oai/h2o-3/issues/13457)





##### API

- Display point layer for tree vs mse plots in GBM output [(#13551)](https://github.com/h2oai/h2o-3/issues/13551)
- Rename API inputs/outputs [(github)](https://github.com/h2oai/h2o-flow/commit/c7fc17afd3ff0a176e80d9d07d71c0bdd8f165eb)
- Rename Inf to Infinity [(github)](https://github.com/h2oai/h2o-flow/commit/ef5f5997d044dac9ab676b65174f09aa8785cfb6)


##### Python

- added H2OFrame.setNames(), H2OFrame.cbind(), H2OVec.cbind(), h2o.cbind(), and pyunit_cbind.py [(github)](https://github.com/h2oai/h2o-dev/commit/84a3ea920f2ea9ee76985f7ccadb1e9d3f935025)
- Make H2OVec.levels() return the levels [(github)](https://github.com/h2oai/h2o-dev/commit/ab07275a55930b574407d8c4ea8e2b29cd6acd77)
- H2OFrame.dim(), H2OFrame.append(), H2OVec.setName(), H2OVec.isna() additions. demo pyunit addition [(github)](https://github.com/h2oai/h2o-dev/commit/41e6668ca05c59e614e54477a6082345366c75c8)


##### System

- Customize H2O web UI port [(#13475)](https://github.com/h2oai/h2o-3/issues/13475)
- Make parse setup interactive [(#13516)](https://github.com/h2oai/h2o-3/issues/13516)
- Added --verbose [(github)](https://github.com/h2oai/h2o-dev/commit/5e772f8314a340666e4e80b3480b2105ceb91251)
- Adds some H2OParseExceptions. Removes all H2O.fail in parse (no parse issues should cause a fail)[(github)](https://github.com/h2oai/h2o-dev/commit/687b674d1dfb37f13542d15d1f04fe1b7c181f71)
- Allows parse to specify check_headers=HAS_HEADERS, but not provide column names [(github)](https://github.com/h2oai/h2o-dev/commit/ba48c0af1253d4bd6b05024991241fc6f7f8532a)
- Port MissingValueInserter EndPoint to h2o-dev [(#13457)](https://github.com/h2oai/h2o-3/issues/13457)



##### Web UI

- Add 'Clear cell' and 'Run all cells' toolbar buttons [(github)](https://github.com/h2oai/h2o-flow/commit/802b3a31ed8171a43cd1e566e5f77ba7fbf33549)
- Add 'Clear cell' and 'Clear all cells' commands [(#13484)](https://github.com/h2oai/h2o-3/issues/13484) [(github)](https://github.com/h2oai/h2o-flow/commit/2ecbe04325c865d0f5d8b2cb753ca15036ea2321)
- 'Run' button selects next cell after running
- ModelMetrics by model category: Clustering [(#13303)](https://github.com/h2oai/h2o-3/issues/13303)
- ModelMetrics by model category: Regression [(#13302)](https://github.com/h2oai/h2o-3/issues/13302)
- ModelMetrics by model category: Multinomial [(#13301)](https://github.com/h2oai/h2o-3/issues/13301)
- ModelMetrics by model category: Binomial [(#13300)](https://github.com/h2oai/h2o-3/issues/13300)
- Add ability to select and delete multiple models [(github)](https://github.com/h2oai/h2o-flow/commit/8a9d033deba68292347c1e027b461a4c9ba7f1e5)
- Add ability to select and delete multiple frames [(github)](https://github.com/h2oai/h2o-flow/commit/6d5455b041f5af6b6213694ee1aae8d4e4d57d2b)
- Flows now stop running when an error occurs
- Print full number of mismatches during POJO comparison check. [(github)](https://github.com/h2oai/h2o-dev/commit/e8b599b59f2117083d2f7979cd1a0ca957a41605)
- Make Grid multi-node safe [(github)](https://github.com/h2oai/h2o-dev/commit/915cf0bd4fa589c6d819ba1eba85811e30f87399)
- Beautify the vertical axis labels for Flow charts/visualization (more) [(#13330)](https://github.com/h2oai/h2o-3/issues/13330)

#### Bug Fixes

##### Algorithms

- GBM only populates either MSE_train or MSE_valid but displays both [(#13350)](https://github.com/h2oai/h2o-3/issues/13350)
- GBM: train error increases after hitting zero on prostate dataset [(#13555)](https://github.com/h2oai/h2o-3/issues/13555)
- GBM : Variable importance displays 0's for response param => should not display response in table at all [(#13424)](https://github.com/h2oai/h2o-3/issues/13424)
- GLM : R/Flow ==> Build GLM Model hangs at 4% [(#13448)](https://github.com/h2oai/h2o-3/issues/13448)
- Import file from R hangs at 75% for 15M Rows/2.2 K Columns [(private-#601)](https://github.com/h2oai/private-h2o-3/issues/601)
- Flow: GLM - 'model.output.coefficients_magnitude.name' not found, so can't view model [(#13458)](https://github.com/h2oai/h2o-3/issues/13458)
- GBM predict fails without response column [(#13470)](https://github.com/h2oai/h2o-3/issues/13470)
- GBM: When validation set is provided, gbm should report both mse_valid and mse_train [(#13490)](https://github.com/h2oai/h2o-3/issues/13490)
- PCA Assertion Error during Model Metrics [(#13530)](https://github.com/h2oai/h2o-3/issues/13530) [(github)](https://github.com/h2oai/h2o-dev/commit/69690db57ed9951a57df83b2ce30be30a49ca507)
- KMeans: Size of clusters in Model Output is different from the labels generated on the training set [(#13525)](https://github.com/h2oai/h2o-3/issues/13525) [(github)](https://github.com/h2oai/h2o-dev/commit/6f8a857c8a060af0d2434cda91469ef8c23c86ae)
- Inconsistency in GBM results:Gives different results even when run with the same set of params [(private-#480)](https://github.com/h2oai/private-h2o-3/issues/480)
- #13573: Fix some numerical edge cases [(github)](https://github.com/h2oai/h2o-dev/commit/4affd9baa005c08d6b1669e462ec7bfb4de5ec69)
- Fix two missing float -> double conversion changes in tree scoring. [(github)](https://github.com/h2oai/h2o-dev/commit/b2cc99822db9b59766f3293e4dbbeeea547cd81e)
- Flow: HIDDEN_DROPOUT_RATIOS for DL does not show default value [(#13232)](https://github.com/h2oai/h2o-3/issues/13232)
- Old GLM Parameters Missing [(#13426)](https://github.com/h2oai/h2o-3/issues/13426)
- GLM: R/Flow ==> Build GLM Model hangs at 4% [(#13448)](https://github.com/h2oai/h2o-3/issues/13448)





##### API

- SplitFrame on String column produce C0LChunk instead of CStrChunk [(#13460)](https://github.com/h2oai/h2o-3/issues/13460)
-  Error in node$h2o$node : $ operator is invalid for atomic vectors [(#13348)](https://github.com/h2oai/h2o-3/issues/13348)
-  Response from /ModelBuilders don't conform to standard error json shape when there are errors [(private-#536)](https://github.com/h2oai/private-h2o-3/issues/536) [(github)](https://github.com/h2oai/h2o-dev/commit/dadf385b3e3b2f68afe88096ecfd51e5bc9e01cb)

##### Python

- fix python syntax error [(github)](https://github.com/h2oai/h2o-dev/commit/a3c62f099088ac2206b83275ca096d4952f76e28)
- Fixes handling of None in python for a returned na_string. [(github)](https://github.com/h2oai/h2o-dev/commit/58c1af54b37909b8e9d06d23ed41fce4943eceb4)



##### R

- R : Inconsistency - Train set name with and without quotes work but Validation set name with quotes does not work [(#13482)](https://github.com/h2oai/h2o-3/issues/13482)
- h2o.confusionmatrices does not work [(#13559)](https://github.com/h2oai/h2o-3/issues/13559)
- How do i convert an enum column back to integer/double from R? [(#13529)](https://github.com/h2oai/h2o-3/issues/13529)
- Summary in R is faulty [(#13523)](https://github.com/h2oai/h2o-3/issues/13523)
- R: as.h2o should preserve R data types [(#13571)](https://github.com/h2oai/h2o-3/issues/13571)
- NPE in GBM Prediction with Sliced Test Data [(private-#472)](https://github.com/h2oai/private-h2o-3/issues/472) [(github)](https://github.com/h2oai/h2o-dev/commit/e605ab109488c7630223320fdd8bad486492050a)
- Import file from R hangs at 75% for 15M Rows/2.2 K Columns [(private-#601)](https://github.com/h2oai/private-h2o-3/issues/601)
- Custom Functions don't work in apply() in R [(#13431)](https://github.com/h2oai/h2o-3/issues/13431)
- got water.DException$DistributedException and then got java.lang.RuntimeException: Categorical renumber task [(private-#479)](https://github.com/h2oai/private-h2o-3/issues/479)
- H2O-R: as.h2o parses column name as one of the row entries [(#13584)](https://github.com/h2oai/h2o-3/issues/13584)
- R-H2O Managing Memory in a loop [(#15604)](https://github.com/h2oai/h2o-3/issues/15604)
- h2o.confusionMatrices for multinomial does not work [(#13570)](https://github.com/h2oai/h2o-3/issues/13570)
- H2O-R not showing meaningful error msg





##### System

- Flow: When balance class = F then flow should not show max_after_balance_size = 5 in the parameter listing [(#13550)](https://github.com/h2oai/h2o-3/issues/13550)
- 3 jvms, doing ModelMetrics on prostate, class water.KeySnapshot$GlobalUKeySetTask; class java.lang.AssertionError: --- Attempting to block on task (class water.TaskGetKey) with equal or lower priority. Can lead to deadlock! 122 <=  122 [(#13486)](https://github.com/h2oai/h2o-3/issues/13486)
- Not able to start h2o on hadoop [(#13479)](https://github.com/h2oai/h2o-3/issues/13479)
- one row (one col) dataset seems to get assertion error in parse setup request [(#13111)](https://github.com/h2oai/h2o-3/issues/13111)
- Parse : Import file (move.com) => Parse => First row contains column names => column names not selected [(private-#540)](https://github.com/h2oai/private-h2o-3/issues/540) [(github)](https://github.com/h2oai/h2o-dev/commit/6f6d7023f9f2bafcb5461f46cf2825f233779f4a)
- The NY0 parse rule, in summary. Doesn't look like it's counting the 0's as NAs like h2o [(#13166)](https://github.com/h2oai/h2o-3/issues/13166)
- 0 / Y / N parsing [(#13245)](https://github.com/h2oai/h2o-3/issues/13245)
- NodePersistentStorage gets wiped out when laptop is restarted. [(private-#497)](https://github.com/h2oai/private-h2o-3/issues/497)
- Building a model and making a prediction accepts invalid frame types [(#13097)](https://github.com/h2oai/h2o-3/issues/13097)
- Flow : Import file 15M rows 2.2 Cols => Parse => Error fetching job on UI =>Console : ERROR: Job was not successful Exiting with nonzero exit status [(private-#596)](https://github.com/h2oai/private-h2o-3/issues/596)
- Flow : Build GLM Model => Family tweedy => class hex.glm.LSMSolver$ADMMSolver$NonSPDMatrixException', with msg 'Matrix is not SPD, can't solve without regularization [(#13223)](https://github.com/h2oai/h2o-3/issues/13223)
- Flow : Import File : File doesn't exist on all the hdfs nodes => Fails without valid message [(#13315)](https://github.com/h2oai/h2o-3/issues/13315)
- Check reproducibility on multi-node vs single-node [(#13538)](https://github.com/h2oai/h2o-3/issues/13538)
- Parse : After parsing Chicago crime dataset => Not able to build models or Get frames [(#15418)](https://github.com/h2oai/h2o-3/issues/15418)





##### Web UI

- Flow : Build Model => Parameters => shows meta text for some params [(#13552)](https://github.com/h2oai/h2o-3/issues/13552)
- Flow: K-Means - "None" option should not appear in "Init" parameters [(#13451)](https://github.com/h2oai/h2o-3/issues/13451)
- Flow: PCA - "None" option appears twice in "Transform" list [(private-#487)](https://github.com/h2oai/private-h2o-3/issues/487)
- GBM Model : Params in flow show two times [(#13435)](https://github.com/h2oai/h2o-3/issues/13435)
- Flow multinomial confusion matrix visualization [(private-#473)](https://github.com/h2oai/private-h2o-3/issues/473)
- Flow: It would be good if flow can report the actual distribution, instead of just reporting "Auto" in the model parameter listing [(#13554)](https://github.com/h2oai/h2o-3/issues/13554)
- Unimplemented algos should be taken out from drop down of build model [(#13497)](https://github.com/h2oai/h2o-3/issues/13497)
- [MapR] unable to give hdfs file name from Flow [(#13408)](https://github.com/h2oai/h2o-3/issues/13408)





---

### Selberg (0.2.0.1) - 3/6/15

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o-dev/rel-selberg/1/index.html'>http://h2o-release.s3.amazonaws.com/h2o-dev/rel-selberg/1/index.html</a>

#### New Features


##### Algorithms

- Naive Bayes in H2O-dev [(#13171)](https://github.com/h2oai/h2o-3/issues/13171)
- GLM model output, details from R [(private-#565)](https://github.com/h2oai/private-h2o-3/issues/565)
- Run GLM Regression from Flow (including LBFGS) [(private-#549)](https://github.com/h2oai/private-h2o-3/issues/549)
- PCA [(#13170)](https://github.com/h2oai/h2o-3/issues/13170)
- Port Random Forest to h2o-dev [(#13447)](https://github.com/h2oai/h2o-3/issues/13447)
- Enable DRF model output [(github)](https://github.com/h2oai/h2o-flow/commit/44ee1bf98dd69f33251a7a959b1000cc7f290427)
- Add DRF to Flow (Model Output) [(#13517)](https://github.com/h2oai/h2o-3/issues/13517)
- Grid for GBM [(github)](https://github.com/h2oai/h2o-dev/commit/ce96d2859aa86e4df393a13e00fbb7fcf603c166)
- Run Deep Learning Regression from Flow [(private-#550)](https://github.com/h2oai/private-h2o-3/issues/550)

##### Python

- Add Python wrapper for DRF [(#13518)](https://github.com/h2oai/h2o-3/issues/13518)


##### R

- Add R wrapper for DRF [(#13514)](https://github.com/h2oai/h2o-3/issues/13514)



##### System

- Include uploadFile [(#13296)](https://github.com/h2oai/h2o-3/issues/13296) [(github)](https://github.com/h2oai/h2o-flow/commit/3f8fb91cf6d81aefdb0ad6deee801084e0cf864f)
- Added -flow_dir to hadoop driver [(github)](https://github.com/h2oai/h2o-dev/commit/9883b4d98ae0056e88db449ce1ebd20394d191ac)



##### Web UI

- Add Flow packs [(private-#483)](https://github.com/h2oai/private-h2o-3/issues/483) [(#13261)](https://github.com/h2oai/h2o-3/issues/13261)
- Integrate H2O Help inside Help panel [(#13124)](https://github.com/h2oai/h2o-3/issues/13124) [(github)](https://github.com/h2oai/h2o-flow/commit/62e3c06e91bc0576e15516381bb59f31dbdf38ca)
- Add quick toggle button to show/hide the sidebar [(github)](https://github.com/h2oai/h2o-flow/commit/b5fb2b54a04850c9b24bb0eb03769cb519039de6)
- Add New, Open toolbar buttons [(github)](https://github.com/h2oai/h2o-flow/commit/b6efd33c9c8c2f5fe73e9ba83c1441d768ec47f7)
- Auto-refresh data preview when parse setup input parameters are changed [(#13516)](https://github.com/h2oai/h2o-3/issues/13516)
  -Flow: Add playbar with Run, Continue, Pause, Progress controls [(private-#481)](https://github.com/h2oai/private-h2o-3/issues/481)
- You can now stop/cancel a running flow


#### Enhancements

The following changes are improvements to existing features (which includes changed default values):

##### Algorithms

- Display GLM coefficients only if available [(#13458)](https://github.com/h2oai/h2o-3/issues/13458)
- Add random chance line to RoC chart [(private-#496)](https://github.com/h2oai/private-h2o-3/issues/496)
- Allow validation dataset for AutoEncoder [(#13574)](https://github.com/h2oai/h2o-3/issues/13574)
- Speed up DLSpiral test. Ignore Neurons test (MatVec) [(github)](https://github.com/h2oai/h2o-dev/commit/822862aa29fb63e52703ce91794a64e49bb96aed)
- Use getRNG for Dropout [(github)](https://github.com/h2oai/h2o-dev/commit/94a5b4e46a4501e85fb4889e5c8b196c46f74525)
- #13590: Add tests for determinism of RNGs [(github)](https://github.com/h2oai/h2o-dev/commit/e77c3ead2151a1202ec0b9c467641bc1c787e122)
- #13590: Implement Chi-Square test for RNGs [(github)](https://github.com/h2oai/h2o-dev/commit/690dd333c6bf51ff4e223cd15ef9dab004ed8904)
- #13573: Add log loss to binomial and multinomial model metric [(github)](https://github.com/h2oai/h2o-dev/commit/8982a0a1ba575bd5ca6ca3e854382e03146743cd)
- Add DL model output toString() [(github)](https://github.com/h2oai/h2o-dev/commit/d206bb5b9996e87e8c0058dd8f1d7580d1ea0bb1)
- Add LogLoss to MultiNomial ModelMetrics [(#13573)](https://github.com/h2oai/h2o-3/issues/13573)
- Port MissingValueInserter EndPoint to h2o-dev [(#13457)](https://github.com/h2oai/h2o-3/issues/13457)
- Print number of categorical levels once we hit >1000 input neurons. [(github)](https://github.com/h2oai/h2o-dev/commit/ccf645af908d4964db3bc36a98c4ff9868838dc6)
- Updated the loss behavior for GBM. When loss is set to AUTO, if the response is an integer with 2 levels, then bernoullli (rather than gaussian) behavior is chosen. As a result, the `do_classification` flag is no longer necessary in Flow, since the loss completely specifies the desired behavior, and R users no longer to use `as.factor()` in their response to get the desired bernoulli behavior. The `score_each_iteration` flag has been removed as well. [(github)](https://github.com/h2oai/h2o-dev/commit/cc971e00869197625fefec894ab705c79db05fbb)
- Fully remove `_convert_to_enum` in all algos [(github)](https://github.com/h2oai/h2o-dev/commit/7fdf5d98c1f7caf88a3a928a28b2f86b06c5b2eb)
- Add DL POJO scoring [(#13578)](https://github.com/h2oai/h2o-3/issues/13578)





##### API

- Display point layer for tree vs mse plots in GBM output [(#13551)](https://github.com/h2oai/h2o-3/issues/13551)
- Rename API inputs/outputs [(github)](https://github.com/h2oai/h2o-flow/commit/c7fc17afd3ff0a176e80d9d07d71c0bdd8f165eb)
- Rename Inf to Infinity [(github)](https://github.com/h2oai/h2o-flow/commit/ef5f5997d044dac9ab676b65174f09aa8785cfb6)


##### Python

- added H2OFrame.setNames(), H2OFrame.cbind(), H2OVec.cbind(), h2o.cbind(), and pyunit_cbind.py [(github)](https://github.com/h2oai/h2o-dev/commit/84a3ea920f2ea9ee76985f7ccadb1e9d3f935025)
- Make H2OVec.levels() return the levels [(github)](https://github.com/h2oai/h2o-dev/commit/ab07275a55930b574407d8c4ea8e2b29cd6acd77)
- H2OFrame.dim(), H2OFrame.append(), H2OVec.setName(), H2OVec.isna() additions. demo pyunit addition [(github)](https://github.com/h2oai/h2o-dev/commit/41e6668ca05c59e614e54477a6082345366c75c8)


##### R

- #13571, #13571, #13571.
  -R client now sends the data frame column names and data types to ParseSetup.
  -R client can get column names from a parsed frame or a list.
  -Respects client request for column data types [(github)](https://github.com/h2oai/h2o-dev/commit/ba063be25d3fbb658b016ff514083284e2d95d78)

##### System

- Customize H2O web UI port [(#13475)](https://github.com/h2oai/h2o-3/issues/13475)
- Make parse setup interactive [(#13516)](https://github.com/h2oai/h2o-3/issues/13516)
- Added --verbose [(github)](https://github.com/h2oai/h2o-dev/commit/5e772f8314a340666e4e80b3480b2105ceb91251)
- Adds some H2OParseExceptions. Removes all H2O.fail in parse (no parse issues should cause a fail)[(github)](https://github.com/h2oai/h2o-dev/commit/687b674d1dfb37f13542d15d1f04fe1b7c181f71)
- Allows parse to specify check_headers=HAS_HEADERS, but not provide column names [(github)](https://github.com/h2oai/h2o-dev/commit/ba48c0af1253d4bd6b05024991241fc6f7f8532a)
- Port MissingValueInserter EndPoint to h2o-dev [(#13457)](https://github.com/h2oai/h2o-3/issues/13457)



##### Web UI

- Add 'Clear cell' and 'Run all cells' toolbar buttons [(github)](https://github.com/h2oai/h2o-flow/commit/802b3a31ed8171a43cd1e566e5f77ba7fbf33549)
- Add 'Clear cell' and 'Clear all cells' commands [(#13484)](https://github.com/h2oai/h2o-3/issues/13484) [(github)](https://github.com/h2oai/h2o-flow/commit/2ecbe04325c865d0f5d8b2cb753ca15036ea2321)
- 'Run' button selects next cell after running
- ModelMetrics by model category: Clustering [(#13303)](https://github.com/h2oai/h2o-3/issues/13303)
- ModelMetrics by model category: Regression [(#13302)](https://github.com/h2oai/h2o-3/issues/13302)
- ModelMetrics by model category: Multinomial [(#13301)](https://github.com/h2oai/h2o-3/issues/13301)
- ModelMetrics by model category: Binomial [(#13300)](https://github.com/h2oai/h2o-3/issues/13300)
- Add ability to select and delete multiple models [(github)](https://github.com/h2oai/h2o-flow/commit/8a9d033deba68292347c1e027b461a4c9ba7f1e5)
- Add ability to select and delete multiple frames [(github)](https://github.com/h2oai/h2o-flow/commit/6d5455b041f5af6b6213694ee1aae8d4e4d57d2b)
- Flows now stop running when an error occurs
- Print full number of mismatches during POJO comparison check. [(github)](https://github.com/h2oai/h2o-dev/commit/e8b599b59f2117083d2f7979cd1a0ca957a41605)
- Make Grid multi-node safe [(github)](https://github.com/h2oai/h2o-dev/commit/915cf0bd4fa589c6d819ba1eba85811e30f87399)
- Beautify the vertical axis labels for Flow charts/visualization (more) [(#13330)](https://github.com/h2oai/h2o-3/issues/13330)

#### Bug Fixes

The following changes are to resolve incorrect software behavior:

##### Algorithms

- GBM only populates either MSE_train or MSE_valid but displays both [(#13350)](https://github.com/h2oai/h2o-3/issues/13350)
- GBM: train error increases after hitting zero on prostate dataset [(#13555)](https://github.com/h2oai/h2o-3/issues/13555)
- GBM : Variable importance displays 0's for response param => should not display response in table at all [(#13424)](https://github.com/h2oai/h2o-3/issues/13424)
- Inconsistency in GBM results:Gives different results even when run with the same set of params [(private-#480)](https://github.com/h2oai/private-h2o-3/issues/480)
- GLM : R/Flow ==> Build GLM Model hangs at 4% [(#13448)](https://github.com/h2oai/h2o-3/issues/13448)
- Import file from R hangs at 75% for 15M Rows/2.2 K Columns [(private-#601)](https://github.com/h2oai/private-h2o-3/issues/601)
- Flow: GLM - 'model.output.coefficients_magnitude.name' not found, so can't view model [(#13458)](https://github.com/h2oai/h2o-3/issues/13458)
- GBM predict fails without response column [(#13470)](https://github.com/h2oai/h2o-3/issues/13470)
- GBM: When validation set is provided, gbm should report both mse_valid and mse_train [(#13490)](https://github.com/h2oai/h2o-3/issues/13490)
- PCA Assertion Error during Model Metrics [(#13530)](https://github.com/h2oai/h2o-3/issues/13530) [(github)](https://github.com/h2oai/h2o-dev/commit/69690db57ed9951a57df83b2ce30be30a49ca507)
- KMeans: Size of clusters in Model Output is different from the labels generated on the training set [(#13525)](https://github.com/h2oai/h2o-3/issues/13525) [(github)](https://github.com/h2oai/h2o-dev/commit/6f8a857c8a060af0d2434cda91469ef8c23c86ae)
- Inconsistency in GBM results:Gives different results even when run with the same set of params [(private-#480)](https://github.com/h2oai/private-h2o-3/issues/480)
- divide by zero in modelmetrics for deep learning [(#13546)](https://github.com/h2oai/h2o-3/issues/13546)
- AUC reported on training data is 0, but should be 1 [(private-#457)](https://github.com/h2oai/private-h2o-3/issues/457) [(github)](https://github.com/h2oai/h2o-dev/commit/312558524749a0b28bf22ffd8c34ebcd6996b350)
- GBM: reports 0th tree mse value for the validation set, different than the train set ,When only train sets is provided [(#13562)](https://github.com/h2oai/h2o-3/issues/13562)
- #13573: Fix some numerical edge cases [(github)](https://github.com/h2oai/h2o-dev/commit/4affd9baa005c08d6b1669e462ec7bfb4de5ec69)
- Fix two missing float -> double conversion changes in tree scoring. [(github)](https://github.com/h2oai/h2o-dev/commit/b2cc99822db9b59766f3293e4dbbeeea547cd81e)
- Problems during Train/Test adaptation between Enum/Numeric [(private-#451)](https://github.com/h2oai/private-h2o-3/issues/451)
- DRF/GBM balance_classes=True throws unimplemented exception [(private-#454)](https://github.com/h2oai/private-h2o-3/issues/454)
- Flow: HIDDEN_DROPOUT_RATIOS for DL does not show default value [(#13232)](https://github.com/h2oai/h2o-3/issues/13232)
- Old GLM Parameters Missing [(#13426)](https://github.com/h2oai/h2o-3/issues/13426)
- GLM: R/Flow ==> Build GLM Model hangs at 4% [(#13448)](https://github.com/h2oai/h2o-3/issues/13448)
- GBM: Initial mse in bernoulli seems to be off [(#13556)](https://github.com/h2oai/h2o-3/issues/13556)




##### API

- SplitFrame on String column produce C0LChunk instead of CStrChunk [(#13460)](https://github.com/h2oai/h2o-3/issues/13460)
-  Error in node$h2o$node : $ operator is invalid for atomic vectors [(#13348)](https://github.com/h2oai/h2o-3/issues/13348)
-  Response from /ModelBuilders don't conform to standard error json shape when there are errors [(private-#536)](https://github.com/h2oai/private-h2o-3/issues/536)

##### Python

- fix python syntax error [(github)](https://github.com/h2oai/h2o-dev/commit/a3c62f099088ac2206b83275ca096d4952f76e28)
- Fixes handling of None in python for a returned na_string. [(github)](https://github.com/h2oai/h2o-dev/commit/58c1af54b37909b8e9d06d23ed41fce4943eceb4)


##### R

- R : Inconsistency - Train set name with and without quotes work but Validation set name with quotes does not work [(#13482)](https://github.com/h2oai/h2o-3/issues/13482)
- h2o.confusionmatrices does not work [(#13559)](https://github.com/h2oai/h2o-3/issues/13559)
- How do i convert an enum column back to integer/double from R? [(#13529)](https://github.com/h2oai/h2o-3/issues/13529)
- Summary in R is faulty [(#13523)](https://github.com/h2oai/h2o-3/issues/13523)
- Custom Functions don't work in apply() in R [(#13431)](https://github.com/h2oai/h2o-3/issues/13431)
- R: as.h2o should preserve R data types [(#13571)](https://github.com/h2oai/h2o-3/issues/13571)
- as.h2o loses track of headers [(#13524)](https://github.com/h2oai/h2o-3/issues/13524)
- NPE in GBM Prediction with Sliced Test Data [(private-#472)](https://github.com/h2oai/private-h2o-3/issues/472) [(github)](https://github.com/h2oai/h2o-dev/commit/e605ab109488c7630223320fdd8bad486492050a)
- Import file from R hangs at 75% for 15M Rows/2.2 K Columns [(private-#601)](https://github.com/h2oai/private-h2o-3/issues/601)
- Custom Functions don't work in apply() in R [(#13431)](https://github.com/h2oai/h2o-3/issues/13431)
- got water.DException$DistributedException and then got java.lang.RuntimeException: Categorical renumber task [(private-#479)](https://github.com/h2oai/private-h2o-3/issues/479)
- h2o.confusionMatrices for multinomial does not work [(#13570)](https://github.com/h2oai/h2o-3/issues/13570)
- R: h2o.confusionMatrix should handle both models and model metric objects [(#13583)](https://github.com/h2oai/h2o-3/issues/13583)
- H2O-R: as.h2o parses column name as one of the row entries [(#13584)](https://github.com/h2oai/h2o-3/issues/13584)


##### System

- Flow: When balance class = F then flow should not show max_after_balance_size = 5 in the parameter listing [(#13550)](https://github.com/h2oai/h2o-3/issues/13550)
- 3 jvms, doing ModelMetrics on prostate, class water.KeySnapshot$GlobalUKeySetTask; class java.lang.AssertionError: --- Attempting to block on task (class water.TaskGetKey) with equal or lower priority. Can lead to deadlock! 122 <=  122 [(#13486)](https://github.com/h2oai/h2o-3/issues/13486)
- Not able to start h2o on hadoop [(#13479)](https://github.com/h2oai/h2o-3/issues/13479)
- one row (one col) dataset seems to get assertion error in parse setup request [(#13111)](https://github.com/h2oai/h2o-3/issues/13111)
- Parse : Import file (move.com) => Parse => First row contains column names => column names not selected [(private-#540)](https://github.com/h2oai/private-h2o-3/issues/540) [(github)](https://github.com/h2oai/h2o-dev/commit/6f6d7023f9f2bafcb5461f46cf2825f233779f4a)
- The NY0 parse rule, in summary. Doesn't look like it's counting the 0's as NAs like h2o [(#13166)](https://github.com/h2oai/h2o-3/issues/13166)
- 0 / Y / N parsing [(#13245)](https://github.com/h2oai/h2o-3/issues/13245)
- NodePersistentStorage gets wiped out when laptop is restarted. [(private-#497)](https://github.com/h2oai/private-h2o-3/issues/497)
- Parse : Parsing random crap gives java.lang.ArrayIndexOutOfBoundsException: 13 [(#13420)](https://github.com/h2oai/h2o-3/issues/13420)
- Flow: converting a column to enum while parsing does not work [(#13564)](https://github.com/h2oai/h2o-3/issues/13564)
- Parse: Numbers completely parsed wrong [(#13568)](https://github.com/h2oai/h2o-3/issues/13568)
- NodePersistentStorage gets wiped out when hadoop cluster is restarted [(private-#488)](https://github.com/h2oai/private-h2o-3/issues/488)
- Parse: Fail gracefully when asked to parse a zip file with different files in it [(#13557)](https://github.com/h2oai/h2o-3/issues/13557)[(github)](https://github.com/h2oai/h2o-dev/commit/23a60d68e9d77fe07ae9d940b0ebb6636ef40ee3)
- Building a model and making a prediction accepts invalid frame types [(#13097)](https://github.com/h2oai/h2o-3/issues/13097)
- Flow : Import file 15M rows 2.2 Cols => Parse => Error fetching job on UI =>Console : ERROR: Job was not successful Exiting with nonzero exit status [(private-#596)](https://github.com/h2oai/private-h2o-3/issues/596)
- Flow : Build GLM Model => Family tweedy => class hex.glm.LSMSolver$ADMMSolver$NonSPDMatrixException', with msg 'Matrix is not SPD, can't solve without regularization [(#13223)](https://github.com/h2oai/h2o-3/issues/13223)
- Flow : Import File : File doesn't exist on all the hdfs nodes => Fails without valid message [(#13315)](https://github.com/h2oai/h2o-3/issues/13315)
- Check reproducibility on multi-node vs single-node [(#13538)](https://github.com/h2oai/h2o-3/issues/13538)
- Parse: After parsing Chicago crime dataset => Not able to build models or Get frames [(#15418)](https://github.com/h2oai/h2o-3/issues/15418)

##### Web UI

- Flow : Build Model => Parameters => shows meta text for some params [(#13552)](https://github.com/h2oai/h2o-3/issues/13552)
- Flow: K-Means - "None" option should not appear in "Init" parameters [(#13451)](https://github.com/h2oai/h2o-3/issues/13451)
- Flow: PCA - "None" option appears twice in "Transform" list [(private-#487)](https://github.com/h2oai/private-h2o-3/issues/487)
- GBM Model : Params in flow show two times [(#13435)](https://github.com/h2oai/h2o-3/issues/13435)
- Flow multinomial confusion matrix visualization [(private-#473)](https://github.com/h2oai/private-h2o-3/issues/473)
- Flow: It would be good if flow can report the actual distribution, instead of just reporting "Auto" in the model parameter listing [(#13554)](https://github.com/h2oai/h2o-3/issues/13554)
- Unimplemented algos should be taken out from drop down of build model [(#13497)](https://github.com/h2oai/h2o-3/issues/13497)
- [MapR] unable to give hdfs file name from Flow [(#13408)](https://github.com/h2oai/h2o-3/issues/13408)

---

### Selberg (0.2.0.1) - 3/6/15

Download at: <a href='http://h2o-release.s3.amazonaws.com/h2o-dev/rel-selberg/1/index.html'>http://h2o-release.s3.amazonaws.com/h2o-dev/rel-selberg/1/index.html</a>

#### New Features

##### Web UI

- Flow: Delete functionality to be available for import files, jobs, models, frames [(#13256)](https://github.com/h2oai/h2o-3/issues/13256)
- Implement "Download Flow" [(#13406)](https://github.com/h2oai/h2o-3/issues/13406)
- Flow: Implement "Run All Cells" [(#13126)](https://github.com/h2oai/h2o-3/issues/13126)

##### API

- Create python package [(#13192)](https://github.com/h2oai/h2o-3/issues/13192)
- as.h2o in Python [(private-#580)](https://github.com/h2oai/private-h2o-3/issues/580)

##### System

- Add a README.txt to the hadoop zip files [(github)](https://github.com/h2oai/h2o-dev/commit/5a06ba8f0cfead3e30737d336f3c389ca0775b58)
- Build a cdh5.2 version of h2o [(github)](https://github.com/h2oai/h2o-dev/commit/eb8855d103e4f3aaf9dfa8c07d40d6c848141245)

#### Enhancements

##### Web UI

- Flow: Job view should have info on start and end time [(#13281)](https://github.com/h2oai/h2o-3/issues/13281)
- Flow: Implement 'File > Open' [(#13407)](https://github.com/h2oai/h2o-3/issues/13407)
- Display IP address in ADMIN -> Cluster Status [(private-#505)](https://github.com/h2oai/private-h2o-3/issues/505)
- Flow: Display alternate UI for splitFrames() [(#13398)](https://github.com/h2oai/h2o-3/issues/13398)


##### Algorithms

- Added K-Means scoring [(github)](https://github.com/h2oai/h2o-dev/commit/220d2b40dc36dee6975a101e2eacb56a77861194)
- Flow: Implement model output for Deep Learning [(#13134)](https://github.com/h2oai/h2o-3/issues/13134)
- Flow: Implement model output for GLM [(#13136)](https://github.com/h2oai/h2o-3/issues/13136)
- Deep Learning model output [(private-#570, Flow)](https://github.com/h2oai/private-h2o-3/issues/570),[(private-#570, Python)](https://github.com/h2oai/private-h2o-3/issues/570),[(private-#570, R)](https://github.com/h2oai/private-h2o-3/issues/570)
- Run GLM Binomial from Flow (including LBFGS) [(private-#569)](https://github.com/h2oai/private-h2o-3/issues/569)
- Flow: Display confusion matrices for multinomial models [(#13396)](https://github.com/h2oai/h2o-3/issues/13396)
- During PCA, missing values in training data will be replaced with column mean [(github)](https://github.com/h2oai/h2o-dev/commit/166efad882162f7edc5cd8d4baa189476aa72d25)
- Update parameters for best model scan [(github)](https://github.com/h2oai/h2o-dev/commit/f183de392cb45adea7af43ffa53b095c3764602f)
- Change Quantiles to match h2o-1; both Quantiles and Rollups now have the same default percentiles [(github)](https://github.com/h2oai/h2o-dev/commit/51dc2c12a4281e3a2beeed8adfdfe4b14736fead)
- Massive cleanup and removal of old PCA, replacing with quadratically regularized PCA based on alternating minimization algorithm in GLRM [(github)](https://github.com/h2oai/h2o-dev/commit/02b7f168b2efa551a60c4bf2e95b8d506b613c2d)
- Add model run time to DL Model Output [(github)](https://github.com/h2oai/h2o-dev/commit/6730cc530b7b5376dfe6a2dd71817065e1edab7d)
- Don't gather Neurons/Weights/Biases statistics [(github)](https://github.com/h2oai/h2o-dev/commit/aa1360d1bcfad3628d23211284878d80aa5a3b21)
- Only store best model if `override_with_best_model` is enabled [(github)](https://github.com/h2oai/h2o-dev/commit/5bd1e2327a09b649f251b251ff72af9aa8f4824c)
- `beta_eps` added, passing tests changed [(github)](https://github.com/h2oai/h2o-dev/commit/5e5acb6bdb89ff966151b0bc1ae20e96577d0368)
- For GLM, default values for `max_iters` parameter were changed from 1000 to 50.
- For quantiles, probabilities are displayed.
- Run Deep Learning Multinomial from Flow [(private-#551)](https://github.com/h2oai/private-h2o-3/issues/551)



##### API

- Expose DL weights/biases to clients via REST call [(#13344)](https://github.com/h2oai/h2o-3/issues/13344)
- Flow: Implement notification bar/API [(#13357)](https://github.com/h2oai/h2o-3/issues/13357)
- Variable importance data in REST output for GLM [(#13357)](https://github.com/h2oai/h2o-3/issues/13357)
- Add extra DL parameters to R API (`average_activation, sparsity_beta, max_categorical_features, reproducible`) [(github)](https://github.com/h2oai/h2o-dev/commit/8c7b860e29f297ff42ad6f45a1f138a8c6bb6b29)
- Update GLRM API model output [(github)](https://github.com/h2oai/h2o-dev/commit/653a9906003c2bab5e65d576420c76093fc92d12)
- h2o.anomaly missing in R [(#13429)](https://github.com/h2oai/h2o-3/issues/13429)
- No method to get enum levels [(#13427)](https://github.com/h2oai/h2o-3/issues/13427)



##### System

- Improve memory footprint with latest version of h2o-dev [(github)](https://github.com/h2oai/h2o-dev/commit/c54efaf41bc13677d5acd53a0496cca2b192baef)
- For now, let model.delete() of DL delete its best models too. This allows R code to not leak when only calling h2o.rm() on the main model. [(github)](https://github.com/h2oai/h2o-dev/commit/08b151a2bcbef8d56063b576638a6c0250379bd0)
- Bind both TCP and UDP ports before clustering [(github)](https://github.com/h2oai/h2o-dev/commit/d83c35841800b2abcc9d479fc74583d6ccdc714c)
- Round summary row#. Helps with pctiles for very small row counts. Add a test to check for getting close to the 50% percentile on small rows. [(github)](https://github.com/h2oai/h2o-dev/commit/7f4f7b159de0041894166f62d21e694dbd9c4c5d)
- Increase Max Value size in DKV to 256MB [(github)](https://github.com/h2oai/h2o-dev/commit/336b06e2a129509d424156653a2e7e4d5e972ed8)
- Flow: make parseRaw() do both import and parse in sequence [(private-#489)](https://github.com/h2oai/private-h2o-3/issues/489)
- Remove notion of individual job/job tracking from Flow [(#13441)](https://github.com/h2oai/h2o-3/issues/13441)
- Capability to name prediction results Frame in flow [(#13249)](https://github.com/h2oai/h2o-3/issues/13249)



#### Bug Fixes

##### Algorithms

- GLM binomial prediction failing [(#13402)](https://github.com/h2oai/h2o-3/issues/13402)
- DL: Predict with auto encoder enabled gives Error processing error [(#13428)](https://github.com/h2oai/h2o-3/issues/13428)
- balance_classes in Deep Learning intermittent poor result [(#13432)](https://github.com/h2oai/h2o-3/issues/13432)
- Flow: Building GLM model fails [(#13197)](https://github.com/h2oai/h2o-3/issues/13197)
- summary returning incorrect 0.5 quantile for 5 row dataset [(#13110)](https://github.com/h2oai/h2o-3/issues/13110)
- GBM missing variable importance and balance-classes [(#13313)](https://github.com/h2oai/h2o-3/issues/13313)
- H2O Dev GBM first tree differs from H2O 1 [(#13413)](https://github.com/h2oai/h2o-3/issues/13413)
- get glm model from flow fails to find coefficient name field [(#13393)](https://github.com/h2oai/h2o-3/issues/13393)
- GBM/GLM build model fails on Hadoop after building 100% => Failed to find schema for version: 3 and type: GBMModel [(#13377)](https://github.com/h2oai/h2o-3/issues/13377)
- Parsing KDD wrong [(#13392)](https://github.com/h2oai/h2o-3/issues/13392)
- GLM AIOOBE [(#13213)](https://github.com/h2oai/h2o-3/issues/13213)
- Flow : Build GLM Model with family poisson => java.lang.ArrayIndexOutOfBoundsException: 1 at hex.glm.GLM$GLMLambdaTask.needLineSearch(GLM.java:359) [(#13222)](https://github.com/h2oai/h2o-3/issues/13222)
- Flow : GLM Model Error => Enum conversion only works on small integers [(#13365)](https://github.com/h2oai/h2o-3/issues/13365)
- GLM binary response, do_classfication=FALSE, family=binomial, prediction error [(#13339)](https://github.com/h2oai/h2o-3/issues/13339)
- Epsilon missing from GLM parameters [(#13354)](https://github.com/h2oai/h2o-3/issues/13354)
- GLM NPE [(#13394)](https://github.com/h2oai/h2o-3/issues/13394)
- Flow: GLM bug (or incorrect output) [(#13265)](https://github.com/h2oai/h2o-3/issues/13265)
- GLM binomial prediction failing [(#13402)](https://github.com/h2oai/h2o-3/issues/13402)
- GLM binomial on benign.csv gets assertion error in predict [(#13149)](https://github.com/h2oai/h2o-3/issues/13149)
- current summary default_pctiles doesn't have 0.001 and 0.999 like h2o1 [(#13109)](https://github.com/h2oai/h2o-3/issues/13109)
- Flow: Build GBM/DL Model: java.lang.IllegalArgumentException: Enum conversion only works on integer columns [(#13225)](https://github.com/h2oai/h2o-3/issues/13225) [(github)](https://github.com/h2oai/h2o-dev/commit/57d6d96e4fed0a993bc8017f6e5eb1f60e9ceaa4)
- ModelMetrics on cup98VAL_z dataset has response with many nulls [(#13226)](https://github.com/h2oai/h2o-3/issues/13226)
- GBM : Predict model category output/inspect parameters shows as Regression when model is built with do classification enabled [(#13436)](https://github.com/h2oai/h2o-3/issues/13436)
- Fix double-precision DRF bugs [(github)](https://github.com/h2oai/h2o-dev/commit/cf7910e7bde1d8e3c1d91fadfcf37c5a74882145)

##### System

- Null columnTypes for /smalldata/arcene/arcene_train.data [(#13405)](https://github.com/h2oai/h2o-3/issues/13405) [(github)](https://github.com/h2oai/h2o-dev/commit/8511114a6ef6444938fb75e9ac9d5d7b7fe088d5)
- Flow: Waiting for -1 responses after starting h2o on hadoop cluster of 5 nodes [(#13411)](https://github.com/h2oai/h2o-3/issues/13411)
- Parse: airlines_all.csv => Airtime type shows as ENUM instead of Integer [(#13418)](https://github.com/h2oai/h2o-3/issues/13418) [(github)](https://github.com/h2oai/h2o-dev/commit/f6051de374b46376bf178064719fdd9b03e84dfa)
- Flow: Typo - "Time" option displays twice in column header type menu in Parse [(#13438)](https://github.com/h2oai/h2o-3/issues/13438)
- Duplicate validation messages in k-means output [(#13309)](https://github.com/h2oai/h2o-3/issues/13309) [(github)](https://github.com/h2oai/h2o-dev/commit/7905ba668572cb0eb518d791dc3262a2e8ff2fe0)
- Fixes Parse so that it returns to supplying generic column names when no column names exist [(github)](https://github.com/h2oai/h2o-dev/commit/d404bff2ef41e9a6e2d559c53c42225f11a81bff)
- Flow: Import File: File doesn't exist on all the hdfs nodes => Fails without valid message [(#13315)](https://github.com/h2oai/h2o-3/issues/13315)
- Flow: Parse => 1m.svm hangs at 42% [(private-#514)](https://github.com/h2oai/private-h2o-3/issues/514)
- Prediction NFE [(#13312)](https://github.com/h2oai/h2o-3/issues/13312)
- NPE doing Frame to key before it's fully parsed [(#13093)](https://github.com/h2oai/h2o-3/issues/13093)
- `h2o_master_DEV_gradle_build_J8` #351 hangs for past 17 hrs [(#15458)](https://github.com/h2oai/h2o-3/issues/15458)
- Sparkling water - container exited due to unavailable port [(#13345)](https://github.com/h2oai/h2o-3/issues/13345)



##### API

- Flow: Splitframe => java.lang.ArrayIndexOutOfBoundsException [(#13409)](https://github.com/h2oai/h2o-3/issues/13409) [(github)](https://github.com/h2oai/h2o-dev/commit/f5cf2888230df8904f0d87b8d97c31cc9cf26f79)
- Incorrect dest.type, description in /CreateFrame jobs [(#13403)](https://github.com/h2oai/h2o-3/issues/13403)
- space in windows filename on python [(#13423)](https://github.com/h2oai/h2o-3/issues/13423) [(github)](https://github.com/h2oai/h2o-dev/commit/c3a7f2f95ee41f5eb9bd9f4efd5b870af6cbc314)
- Python end-to-end data science example 1 runs correctly [(#13193)](https://github.com/h2oai/h2o-3/issues/13193)
- 3/NodePersistentStorage.json/foo/id should throw 404 instead of 500 for 'not-found' [(private-#501)](https://github.com/h2oai/private-h2o-3/issues/501)
- POST /3/NodePersistentStorage.json should handle Content-Type:multipart/form-data [(private-#499)](https://github.com/h2oai/private-h2o-3/issues/499)
- by class water.KeySnapshot$GlobalUKeySetTask; class java.lang.AssertionError: --- Attempting to block on task (class water.TaskGetKey) with equal or lower priority. Can lead to deadlock! 122 <= 122 [(#13107)](https://github.com/h2oai/h2o-3/issues/13107)
- Sparkling water : val train:DataFrame = prostateRDD => Fails with ArrayIndexOutOfBoundsException [(#13391)](https://github.com/h2oai/h2o-3/issues/13391)
- Flow : getModels produces error: Error calling GET /3/Models.json [(#13267)](https://github.com/h2oai/h2o-3/issues/13267)
- Flow : Splitframe => java.lang.ArrayIndexOutOfBoundsException [(#13409)](https://github.com/h2oai/h2o-3/issues/13409)
- ddply 'Could not find the operator' [(private-#503)](https://github.com/h2oai/private-h2o-3/issues/503) [(github)](https://github.com/h2oai/h2o-dev/commit/5f5dca9b9fc7d7d4888af0ab7ddad962f0381993)
- h2o.table AIOOBE during NewChunk creation [(private-#504)](https://github.com/h2oai/private-h2o-3/issues/504) [(github)](https://github.com/h2oai/h2o-dev/commit/338d654bd2a80ddf0fba8f65272b3ba07237d2eb)
- Fix warning in h2o.ddply when supplying multiple grouping columns [(github)](https://github.com/h2oai/h2o-dev/commit/1a7adb0a1f1bffe7bf77e5332f6291d4325d6a7f)


---



### 0.1.26.1051 - 2/13/15

#### New Features

- Flow: Display alternate UI for splitFrames() [(#13398)](https://github.com/h2oai/h2o-3/issues/13398)


#### Enhancements

##### System

-  Embedded H2O config can now provide flat file (needed for Hadoop) [(github)](https://github.com/h2oai/h2o-dev/commit/62c344505b1c1c9154624fd9ca07d9b7217a9cfa)
- Don't logging GET of individual jobs to avoid filling up the logs [(github)](https://github.com/h2oai/h2o-dev/commit/9d4a8249ceda49fcc64b5111a62c7a86076d7ec9)

##### Algorithms

-  Increase GBM/DRF factor binning back to historical levels. Had been capped accidentally at nbins (typically 20), was intended to support a much higher cap. [(github)](https://github.com/h2oai/h2o-dev/commit/4dac6ba640818bf5d482e6352a5e6aa62214ca4b)
-  Tweaked rho heuristic in glm [(github)](https://github.com/h2oai/h2o-dev/commit/7aec116974eb14ad6c7d7002a23d952a11339b79)
-  Enable variable importances for autoencoders [(github)](https://github.com/h2oai/h2o-dev/commit/19751e56c11f4ab672d47aabde84cf73271925dd)
-  Removed `group_split` option from GBM
-  Flow: display varimp for GBM output [(#13397)](https://github.com/h2oai/h2o-3/issues/13397)
-  variable importance for GBM [(github)](https://github.com/h2oai/h2o-dev/commit/f5085c3964d87d5349f406d1cfcc81fa0b34a27f)
-  GLM in H2O-Dev may provide slightly different coefficient values when applying an L1 penalty in comparison with H2O1.

#### Bug Fixes

##### Algorithms

- Fixed bug in GLM exception handling causing GLM jobs to hang [(github)](https://github.com/h2oai/h2o-dev/commit/966a58f93d6cf746a2d6ec205d070247e4aeda01)
- Fixed a bug in kmeans input parameter schema where init was always being set to Furthest [(github)](https://github.com/h2oai/h2o-dev/commit/419754634ea30f6b9d9e24a2c62730a3a3b25042)
- Fixed mean computation in GLM [(github)](https://github.com/h2oai/h2o-dev/commit/74d9314a2b73812fa6dab03de9e8ea67c8a4693e)
- Fixed kmeans.R [(github)](https://github.com/h2oai/h2o-dev/commit/a532a0c850cd3c48b281bd34f83adac9108ac885)
- Flow: Building GBM model fails with Error executing javascript [(#13395)](https://github.com/h2oai/h2o-3/issues/13395)

##### System

- DataFrame propagates absolute path to parser [(github)](https://github.com/h2oai/h2o-dev/commit/0fad77b63512f2a20e20c93830e036a32a7643fe)
- Fix flow shutdown bug [(github)](https://github.com/h2oai/h2o-dev/commit/a26bd190dac59750131a2284bdf46e77ad12b67e)


---

### 0.1.26.1032 - 2/6/15

#### New Features

##### General Improvements

- better model output
- support for Python client
- support for Maven
- support for Sparkling Water
- support for REST API schema
- support for Hadoop CDH5 [(github)](https://github.com/h2oai/h2o-dev/commit/6a0feaebc9c7e253fe07b43dc383dfe4cbae2f29)



##### UI

- Display summary visualizations by default in column summary output cells [(#13341)](https://github.com/h2oai/h2o-3/issues/13341)
- Display AUC curve by default in binomial prediction output cells [(#13342)](https://github.com/h2oai/h2o-3/issues/13342)
- Flow: Implement About H2O/Flow with version information [(#13127)](https://github.com/h2oai/h2o-3/issues/13127)
- Add UI for CreateFrame [(#13235)](https://github.com/h2oai/h2o-3/issues/13235)
- Flow: Add ability to cancel running jobs [(#13372)](https://github.com/h2oai/h2o-3/issues/13372)
- Flow: warn when user navigates away while having unsaved content [(#13323)](https://github.com/h2oai/h2o-3/issues/13323)





##### Algorithms

- Implement splitFrame() in Flow [(#13356)](https://github.com/h2oai/h2o-3/issues/13356)
- Variable importance graph in Flow for GLM [(#13358)](https://github.com/h2oai/h2o-3/issues/13358)
- Flow: Implement model building form init and validation [(#13118)](https://github.com/h2oai/h2o-3/issues/13118)
- Added a shuffle-and-split-frame function; Use it to build a saner model on time-series data [(github)](https://github.com/h2oai/h2o-dev/commit/730c8d64316c913183a1271d1a2441f92fa11442)
- Added binomial model metrics [(github)](https://github.com/h2oai/h2o-dev/commit/2d124bea91474f3f55eb5e33f2494ae52ffba749)
- Run KMeans from R [(private-#554)](https://github.com/h2oai/private-h2o-3/issues/554)
- Be able to create a new GLM model from an existing one with updated coefficients [(private-#599)](https://github.com/h2oai/private-h2o-3/issues/599)
- Run KMeans from Python [(private-#553)](https://github.com/h2oai/private-h2o-3/issues/553)
- Run Deep Learning Binomial from Flow [(private-#576)](https://github.com/h2oai/private-h2o-3/issues/576)
- Run KMeans from Flow [(private-#555)](https://github.com/h2oai/private-h2o-3/issues/555)
- Run Deep Learning from Python [(private-#574)](https://github.com/h2oai/private-h2o-3/issues/574)
- Run Deep Learning from R [(private-#575)](https://github.com/h2oai/private-h2o-3/issues/575)
- Run Deep Learning Multinomial from Flow [(private-#551)](https://github.com/h2oai/private-h2o-3/issues/551)
- Run Deep Learning Regression from Flow [(private-#550)](https://github.com/h2oai/private-h2o-3/issues/550)


##### API

- Flow: added REST API documentation to the web ui [(#13075)](https://github.com/h2oai/h2o-3/issues/13075)
- Flow: Implement visualization API [(#13130)](https://github.com/h2oai/h2o-3/issues/13130)



##### System

- Dataset inspection from Flow [(private-#586)](https://github.com/h2oai/private-h2o-3/issues/586)
- Basic data munging (Rapids) from R [(private-#582)](https://github.com/h2oai/private-h2o-3/issues/582)
- Implement stack operator/stacking in Lightning [(private-#531)](https://github.com/h2oai/private-h2o-3/issues/531)





#### Enhancements


##### UI

- Added better message when h2o.init() not yet called (`No active connection to an H2O cluster. Try calling "h2o.init()"`) [(github)](https://github.com/h2oai/h2o-dev/commit/b6bbbcee5972624cecc56099c0f95e1b2dd67253)



##### Algorithms

- Updated column-based gradient task to use sparse interface [(github)](https://github.com/h2oai/h2o-dev/commit/de5685b7c8e109cc39b671ef0bfd016516145d30)
- Updated LBFGS (added progress monitor interface, updated some default params), added progress and job support to GLM lbfgs [(github)](https://github.com/h2oai/h2o-dev/commit/6b89bb9201a89df93c4131b7ba10a7d17b45d72e)
- Added pretty print [(github)](https://github.com/h2oai/h2o-dev/commit/ebc824f9b081b61337c88e52b682bf42d9825c97)
- Added AutoEncoder to R model categories [(github)](https://github.com/h2oai/h2o-dev/commit/7030e7f1fb5779c026e0eed48662571f03f13428)
- Added Coefficients table to GLM model [(github)](https://github.com/h2oai/h2o-dev/commit/a432337d9d8b6480efbdaf0a0ebdb2ca3ad3f91a)
- Updated glm lbfgs to allow for efficient lambda-search (l2 penalty only) [(github)](https://github.com/h2oai/h2o-dev/commit/302ee73916516f2a25f98d96d9dd8fbff324dc5d)
- Removed splitframe shuffle parameter [(github)](https://github.com/h2oai/h2o-dev/commit/27f030721ae71006da7f0cc66be28337973f78f8)
- Simplified model builders and added deeplearning model builder [(github)](https://github.com/h2oai/h2o-dev/commit/302c819ea3d7b623af1968a181614d51d7dc68ed)
- Add DL model outputs to Flow [(#13371)](https://github.com/h2oai/h2o-3/issues/13371)
- Flow: Deep Learning: Expert Mode [(#13231)](https://github.com/h2oai/h2o-3/issues/13231)
- Flow: Display multinomial and regression DL model outputs [(#13382)](https://github.com/h2oai/h2o-3/issues/13382)
- Display varimp details for DL models [(#13380)](https://github.com/h2oai/h2o-3/issues/13380)
- Make binomial response "0" and "1" by default [(github)](https://github.com/h2oai/h2o-dev/commit/f597d4958ff2200f68e2cead31f3a184bfcaa5f2)
- Add Coefficients table to GLM model [(github)](https://github.com/h2oai/h2o-dev/commit/a432337d9d8b6480efbdaf0a0ebdb2ca3ad3f91a)
- Removed splitframe shuffle parameter [(github)](https://github.com/h2oai/h2o-dev/commit/27f030721ae71006da7f0cc66be28337973f78f8)
-  Update R GBM demos to reflect new input parameter names [(github)](https://github.com/h2oai/h2o-dev/commit/8cb99b5bf5ba828d08deba4647309824829a27a5)
-  Rename GLM variable importance to normalized coefficient magnitudes [(github)](https://github.com/h2oai/h2o-dev/commit/8cb99b5bf5ba828d08deba4647309824829a27a5)




##### API

- Changed `key` to `destination_key` [(github)](https://github.com/h2oai/h2o-dev/commit/22067ae62a23af712d3081d981ae08756e6c071e)
- Cleaned up REST API schema interface [(github)](https://github.com/h2oai/h2o-dev/commit/ce581ec9fe670f43e8fb4aa955569cc9e92d013b)
- Changed method name, cleaned setup, added a pyunit runner [(github)](https://github.com/h2oai/h2o-dev/commit/26ea2c52440dd6ad8009c72bac8057d1edd9da0a)





##### System

- Allow changing column types during parse-setup [(#13375)](https://github.com/h2oai/h2o-3/issues/13375)
- Display %NAs in model builder column lists [(#13374)](https://github.com/h2oai/h2o-3/issues/13374)
- Figure out how to add H2O to PyPl [(#13191)](https://github.com/h2oai/h2o-3/issues/13191)




#### Bug Fixes


##### UI

- Flow: Parse => 1m.svm hangs at 42% [(private-#514)](https://github.com/h2oai/private-h2o-3/issues/514)
- cup98 Dataset has columns that prevent validation/prediction [(#13349)](https://github.com/h2oai/h2o-3/issues/13349)
- Flow: predict step failed to function [(#13234)](https://github.com/h2oai/h2o-3/issues/13234)
- Flow: Arrays of numbers (ex. hidden in deeplearning)require brackets [(#13307)](https://github.com/h2oai/h2o-3/issues/13307)
