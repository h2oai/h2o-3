.. _save_and_load_model:

Saving, Loading, Downloading, and Uploading Models
===================================================

This section describes how to save, load, download, and upload binary and :ref:`MOJO models <about-pojo-mojo>` using R, Python, and Flow. 

Binary Models
-------------

When saving an H2O binary model with ``h2o.saveModel`` (R), ``h2o.save_model`` (Python), or in Flow, you will only be able to load and use that saved binary model with the same version of H2O that you used to train your model. H2O binary models are not compatible across H2O versions. If you update your H2O version, then you will need to retrain your model. For production, you can save your model as a :ref:`POJO/MOJO <about-pojo-mojo>`. These artifacts are not tied to a particular version of H2O because they are just plain Java code and do not require an H2O cluster to be running.

In R and Python
~~~~~~~~~~~~~~~

In R and Python, you can save a model locally or to HDFS using the ``h2o.saveModel`` (R) or ``h2o.save_model`` (Python) function . This function accepts the model object and the file path. If no path is specified, then the model will be saved to the current working directory. After the model is saved, you can load it using the ``h2o.loadModel`` (R) or ``h2o.load_model`` (Python) function. You can also upload a model from a local path to your H2O cluster.

**Notes**: 

- When saving a file, the owner of the file saved is the user by which H2O cluster or Python/R session was executed. 
- When downloading a file, the owner of the file saved is the user by which the Python/R session was executed. 

.. tabs::
   .. code-tab:: r R

        # build the model
        model <- h2o.deeplearning(params)

        # save the model
        model_path <- h2o.saveModel(object = model, path = getwd(), force = TRUE)
        print(model_path)
        /tmp/mymodel/DeepLearning_model_R_1441838096933

        # load the model
        saved_model <- h2o.loadModel(model_path)

        # download the model built above to your local machine
        my_local_model <- h2o.download_model(model, path = "/Users/UserName/Desktop")

        # upload the model that you just downloded above 
        # to the H2O cluster
        uploaded_model <- h2o.upload_model(my_local_model)

   .. code-tab:: python

    	# build the model
    	model = H2ODeepLearningEstimator(params)
    	model.train(params)

    	# save the model
    	model_path = h2o.save_model(model=model, path="/tmp/mymodel", force=True)
    	print model_path
    	/tmp/mymodel/DeepLearning_model_python_1441838096933

    	# load the model
    	saved_model = h2o.load_model(model_path)

        # download the model built above to your local machine
        my_local_model = h2o.download_model(model, path="/Users/UserName/Desktop")

        # upload the model that you just downloded above 
        # to the H2O cluster
        uploaded_model = h2o.upload_model(my_local_model)
 

**Note**: When saving to HDFS, you must prepend the save directory with ``hdfs://``. For example:

.. tabs::
   .. code-tab:: r R

        # build the model
        model <- h2o.glm(model params)

        # save the model to HDFS
        hdfs_name_node <- "node-1"
        hdfs_tmp_dir <- "/tmp/runit"
        model_path <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_tmp_dir)
        h2o.saveModel(model, path = model_path, name = "mymodel")

   .. code-tab:: python

    	# build the model
    	h2o_glm = H2OGeneralizedLinearEstimator(model params)
    	h2o_glm.train(training params)

    	# save the model to HDFS
    	hdfs_name_node = "node-1"
    	hdfs_model_path = sprintf("hdfs://%s%s", hdfs_name_node, hdfs_tmp_dir)
    	new_model_path = h2o.save_model(h2o_glm, "hdfs://" + hdfs_name_node + "/" + hdfs_model_path)

In Flow
~~~~~~~

The steps for saving and loading models in Flow are described in the **Using Flow - H2O's Web UI** section. Specifically, refer to :ref:`Exporting and Importing Models <export-import-models-flow>` for information about exporting and importing binary models in Flow. 

MOJO Models
-----------

Introduction
~~~~~~~~~~~~

The MOJO import functionality provides a means to use external, pre-trained models in H2O - mainly for the purpose of scoring. Depending on each external model, metrics and other model information might be obtained as well. Currently, only selected H2O MOJOs are supported. (See the :ref:`mojo_quickstart` section for information about creating MOJOs.)

Supported MOJOs
~~~~~~~~~~~~~~~

.. |yes| image:: /images/checkmark.png
   :scale: 3%
   :align: middle

.. |no| image:: /images/xmark.png
  :scale: 3%
  :align: middle

+---------------+------------+------------+
| Algorithm     | Exportable | Importable |
+===============+============+============+
| AutoML**      | |yes|      | |yes|      |
+---------------+------------+------------+
| GAM           | |yes|      | |yes|      |
+---------------+------------+------------+
| GBM           | |yes|      | |yes|      |
+---------------+------------+------------+
| GLM           | |yes|      | |yes|      |
+---------------+------------+------------+
| MAXR          | |no|       | |no|       |
+---------------+------------+------------+
| XGBoost       | |yes|      | |yes|      |
+---------------+------------+------------+
| DRF           | |yes|      | |yes|      |
+---------------+------------+------------+
| Deep Learning | |yes|      | |yes|      |
+---------------+------------+------------+
| Stacked       | |yes|      | |yes|      |
| Ensemble      |            |            |
+---------------+------------+------------+
| CoxPH         | |yes|      | |yes|      |
+---------------+------------+------------+
| RuleFit       | |yes|      | |yes|      |
+---------------+------------+------------+
| Naive Bayes   | |no|       | |no|       |
| Classifier    |            |            |
+---------------+------------+------------+
| SVM           | |no|       | |no|       |
+---------------+------------+------------+
| K-Means       | |yes|      | |no|       |
+---------------+------------+------------+
| Isolation     | |yes|      | |yes|      |
| Forest        |            |            |
+---------------+------------+------------+
| Extended      | |yes|      | |yes|      |
| Isolation     |            |            |
| Forest        |            |            |
+---------------+------------+------------+
| Aggregator    | |no|       | |no|       |
+---------------+------------+------------+
| GLRM          | |yes|      | |no|       |
+---------------+------------+------------+
| PCA           | |yes|      | |no|       |
+---------------+------------+------------+

**Note**: AutoML will always produce a model which has a MOJO. Though it depends on the run, you are most likely to get a Stacked Ensemble. While all models are importable, only individual models are exportable.


Saving and Importing MOJOs
~~~~~~~~~~~~~~~~~~~~~~~~~~

Importing a MOJO can be done from Python, R, and Flow. H2O imports the model and embraces it for the purpose of scoring. Information output about the model may be limited.

**Note**: Your model will not produce MOJOs if you build it using `interactions <data-science/algo-params/interactions.html>`__. 

Saving and Importing in R or Python
'''''''''''''''''''''''''''''''''''

.. tabs::
   .. code-tab:: r R

        data <- h2o.importFile(path = 'training_dataset.csv')
        cols <- c("Some column", "Another column")
        original_model <- h2o.glm(x = cols, y = "response", training_frame = data)    

        path <- "/path/to/model/directory"
        mojo_destination <- h2o.save_mojo(original_model, path = path)
        imported_model <- h2o.import_mojo(mojo_destination)

        new_observations <- h2o.importFile(path = 'new_observations.csv')
        h2o.predict(imported_model, new_observations)

   .. code-tab:: python

        data = h2o.import_file(path='training_dataset.csv')
        original_model = H2OGeneralizedLinearEstimator()
        original_model.train(x = ["Some column", "Another column"], y = "response", training_frame=data)

        path = '/path/to/model/directory/model.zip'
        original_model.save_mojo(path)

        imported_model = h2o.import_mojo(path)
        new_observations = h2o.import_file(path='new_observations.csv')
        predictions = imported_model.predict(new_observations)


Importing a MOJO Model in Flow
''''''''''''''''''''''''''''''

To import a MOJO model in Flow:

1. Import or upload the MOJO as a Generic model into H2O. To do this, click on **Data** in the top menu and select either **Import Files** or **Upload File**.
2. Retrieve the imported MOJO by clicking **Models** in the top menu and selecting **Import MOJO Model**.

Downloading and Uploading MOJOs
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Alternatively, the ``download_mojo()`` and ``h2o.upload_mojo()`` R and Python functions can be used when downloading/uploading MOJOs from a client computer standing outside of an H2O cluster.

Downloading and Uploading in R and Python
'''''''''''''''''''''''''''''''''''''''''

.. tabs::
   .. code-tab:: r R

    # train a GBM model
    library(h2o)
    h2o.init()
    fr <- as.h2o(iris)
    my_model <- h2o.gbm(x = 1:4, y = 5, training_frame = fr)

    # save to the current working directory
    my_mojo <- h2o.download_mojo(my_model, "/Users/UserName/Desktop")

    # upload the MOJO
    mojo_model <- h2o.upload_mojo(my_mojo)

   .. code-tab:: python

    import h2o
    h2o.init()

    # Train a GBM Model
    from h2o.estimators import H2OGradientBoostingEstimator
    df = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
    model = H2OGradientBoostingEstimator()
    model.train(x=list(range(4)), y = "class", training_frame=df)

    # Download the MOJO
    my_mojo = model.download_mojo(path="/Users/UserName/Desktop")

    # Upload the MOJO
    mojo_model = h2o.upload_mojo(my_mojo)


Advanced MOJO Model Initialization
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

It is also possible to import a MOJO from already uploaded MOJO bytes using Generic model. Generic model is the underlying mechanism behind MOJO import. In this case, there is no need to re-upload the MOJO every time a new MOJO imported model is created. The upload can occur only once.

Defining a Generic Model
''''''''''''''''''''''''

The following options can be specified when using a Generic model:

- `model_id <algo-params/model_id.html>`__: Specify a custom name for the model to use as a reference.

- **model_key**: Specify a key for the self-contained model archive.

- **path**: Specify a path to the file with the self-contained model archive.

Examples
''''''''

.. tabs::
   .. code-tab:: r R

        data <- h2o.importFile(path = 'training_dataset.csv')
        cols <- c("Some column", "Another column")
        original_model <- h2o.glm(x = cols, y = "response", training_frame = data)    

        path <- "/path/to/model/directory"
        mojo_destination <- h2o.download_mojo(model = original_model, path = path)
        
        # Only import or upload MOJO model data, do not initialize the generic model yet
        imported_mojo_key <- h2o.importFile(mojo_destination, parse = FALSE)
        # Build the generic model later, when needed 
        generic_model <- h2o.generic(model_key = imported_mojo_key)

        new_observations <- h2o.importFile(path = 'new_observations.csv')
        h2o.predict(generic_model, new_observations)

   .. code-tab:: python

        data = h2o.import_file(path='training_dataset.csv')
        original_model = H2OGeneralizedLinearEstimator()
        original_model.train(x = ["Some column", "Another column"], y = "response", training_frame=data)

        path = '/path/to/model/directory/model.zip'
        original_model.download_mojo(path)
        
        imported_mojo_key = h2o.lazy_import(file)
        generic_model = H2OGenericEstimator(model_key = get_frame(model_key[0]))
        new_observations = h2o.import_file(path='new_observations.csv')
        predictions = generic_model.predict(new_observations)

