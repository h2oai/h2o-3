MOJO Models
-----------

Introduction
~~~~~~~~~~~~

The MOJO import functionality provides a means to use external, pre-trained models in H2O - mainly for the purpose of scoring. Depending on each external model, metrics and other model information might be obtained as well. Currently, only selected H2O MOJOs are supported. (See the :ref:`mojo_quickstart` section for information about creating MOJOs.)

Supported MOJOs
~~~~~~~~~~~~~~~

Only a subset of H2O MOJO models is supported in this version. 

-  GBM (Gradient Boosting Machines)
-  DRF (Distributed Random Forest)
-  IRF (Isolation Random Forest)
-  GLM (Generalized Linear Model)

Importing a MOJO
~~~~~~~~~~~~~~~~~~~~~~~~~

Importing a MOJO can be done from Python, R, and Flow. H2O imports the model and embraces it for the purpose of scoring. Information output about the model may be limited.

Importing in R or Python
''''''''''''''''''''''''

.. example-code::
   .. code-block:: r

    data <- h2o.importFile(path = 'training_dataset.csv')
    cols <- c("Some column", "Another column")
    original_model <- h2o.glm(x=cols, y = "response", training_frame = data)    

    path <- "/path/to/model/directory"
    mojo_destination <- h2o.download_mojo(model = original_model, path = path)
    imported_model <- h2o.import_mojo(mojo_destination)

    new_observations <- h2o.importFile(path = 'new_observations.csv')
    h2o.predict(imported_model, new_observations)

   .. code-block:: python

    data = h2o.import_file(path='training_dataset.csv')
    original_model = H2OGeneralizedLinearEstimator()
    original_model.train(x = ["Some column", "Another column"], y = "response", training_frame=data)

    path = '/path/to/model/directory/model.zip'
    original_model.download_mojo(path)

    imported_model = h2o.import_mojo(path)
    new_observations = h2o.import_file(path='new_observations.csv')
    predictions = imported_model.predict(new_observations)

Importing a MOJO Model in Flow
''''''''''''''''''''''''''''''

To import a MOJO model in Flow:

1. Import or upload the MOJO as a Generic model into H2O. To do this, click on **Data** in the top menu and select either **Import Files** or **Upload File**.
2. Retrieve the imported MOJO by clicking **Models** in the top menu and selecting **Generic Model**.

Advanced Generic Model Initialization
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

It is also possible to import a MOJO from already uploaded MOJO bytes using Generic model. Generic model is the underlying mechanism behind MOJO import. In this case, there is no need to re-upload the MOJO every 
time a new MOJO imported model is created. The upload may occur only once.

.. example-code::
   .. code-block:: r

    data <- h2o.importFile(path = 'training_dataset.csv')
    cols <- c("Some column", "Another column")
    original_model <- h2o.glm(x=cols, y = "response", training_frame = data)    

    path <- "/path/to/model/directory"
    mojo_destination <- h2o.download_mojo(model = original_model, path = path)
    
    # Only import or upload MOJO model data, do not initialize the generic model yet
    imported_mojo_key <- h2o.importFile(mojo_destination, parse = FALSE)
    # Build the generic model later, when needed 
    generic_model <- h2o.generic(model_key = imported_mojo_key)

    new_observations <- h2o.importFile(path = 'new_observations.csv')
    h2o.predict(generic_model, new_observations)

   .. code-block:: python

    data = h2o.import_file(path='training_dataset.csv')
    original_model = H2OGeneralizedLinearEstimator()
    original_model.train(x = ["Some column", "Another column"], y = "response", training_frame=data)

    path = '/path/to/model/directory/model.zip'
    original_model.download_mojo(path)
    
    imported_mojo_key = h2o.lazy_import(file)
    generic_model = H2OGenericEstimator(model_key = get_frame(model_key[0]))
    new_observations = h2o.import_file(path='new_observations.csv')
    predictions = generic_model.predict(new_observations)
