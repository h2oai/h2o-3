Generic Models
--------------

Introduction
~~~~~~~~~~~~

A Generic model provides a means to use external, pre-trained models in H2O - mainly for the purpose of scoring. Depending on each external model, metrics and other model information might be obtained as well. Currently, only selected H2O MOJOs are supported. (See the :ref:`mojo_quickstart` section for information about creating MOJOs.)

Supported MOJOs
~~~~~~~~~~~~~~~

Only a subset of H2O MOJO models is supported in this version. 

-  GBM (Gradient Boosting Machines)
-  DRF (Distributd Random Forest)
-  IRF (Isolation Random Forest)
-  GLM (Generalized Linear Model)

Importing a Generic Model
~~~~~~~~~~~~~~~~~~~~~~~~~

Importing a Generic model is available from Python, R, and Flow. As the model is generic, there are no hyperparameters and there is no real training phase for Generic models. H2O imports the model and embraces it for the purpose of scoring. Information output about the model may be limited.

Importing in R or Python
''''''''''''''''''''''''

.. example-code::
   .. code-block:: r

    data <- h2o.importFile(path = 'training_dataset.csv')
    cols <- c("Some column", "Another column")
    model.original <- h2o.glm(x=cols, y = "response", training_frame = data)    

    path <- "/path/to/model/directory"
    mojo.destination <- h2o.download_mojo(model = model.original, path = path)
    model.generic <- h2o.genericModel(mojo.destination)

    new_observations <- h2o.importFile(path = 'new_observations.csv')
    h2o.predict(model.generic, new_observations)

   .. code-block:: python

    data = h2o.import_file(path='training_dataset.csv')
    glm = H2OGeneralizedLinearEstimator()
    glm.train(x = ["Some column", "Another column"], y = "response", training_frame=data)

    path = '/path/to/model/directory/model.zip'
    drf.download_mojo(path)

    model = H2OGenericEstimator.from_file(path)
    new_observations = h2o.import_file(path='new_observations.csv')
    predictions = model.predict(new_observations)

Retrieving a Generic Model in Flow
''''''''''''''''''''''''''''''''''

To retrieve a Generic model in Flow:

1. Import or upload the Generic model (most likely a MOJO). To do this, click on **Data** in the top menu and select either **Import Files** or **Upload File**.
2. Retrieve the Generic model by clicking **Models** in the top menu and selecting **Generic**.

Advanced Generic Model Initialization
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

It is also possible to construct a Generic model from already uploaded MOJO bytes. In this case, there is no need to import the MOJO
every time a Generic model is created.

.. example-code::
   .. code-block:: r

    data <- h2o.importFile(path = 'training_dataset.csv')
    cols <- c("Some column", "Another column")
    model.original <- h2o.glm(x=cols, y = "response", training_frame = data)    

    path <- "/path/to/model/directory"
    mojo.destination <- h2o.download_mojo(model = model.original, path = path)
    
    # Only import or upload MOJO model data, do not initialize the generic model yet
    imported_mojo_key <- h2o.importFile(mojo.destination, parse = FALSE)
    # Build the generic model later, when needed 
    model.generic <- h2o.generic(model_key = imported_mojo_key)

    new_observations <- h2o.importFile(path = 'new_observations.csv')
    h2o.predict(model.generic, new_observations)

   .. code-block:: python

    data = h2o.import_file(path='training_dataset.csv')
    glm = H2OGeneralizedLinearEstimator()
    glm.train(x = ["Some column", "Another column"], y = "response", training_frame=data)

    path = '/path/to/model/directory/model.zip'
    drf.download_mojo(path)
    
    imported_mojo_key = h2o.lazy_import(file)
    model = H2OGenericEstimator(model_key = get_frame(model_key[0]))
    new_observations = h2o.import_file(path='new_observations.csv')
    predictions = model.predict(new_observations)
