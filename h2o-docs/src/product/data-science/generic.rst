Generic model
--------

Introduction
~~~~~~~~~~~~
Generic model provides means to use external, pre-trained models in H2O. Mainly for the purpose of scoring. Depending on each external model, metrics and other model information might be obtained as well. Currently, only selected H2O MOJOs are supported.

Importing a generic model is available from Python, R and Flow.

Supported MOJOs
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Only a subset of H2O MOJO models is supported in this version. 

-  GBM (Gradient Boosting Machines)
-  DRF (Distributd Random Forest)
-  IRF (Isolation Random Forest)
-  GLM (Generalized Linear Model)

Importing a Generic model
~~~~~~~~~~~~~~~~~~~~~~~~~

As the model is generic, there are no hyperparameters and there is no real training phase. H2O imports the model and
embraces it for the purpose of scoring. Information output about the model may be limited.

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


In Flow, click on 'Models' in the top menu and select 'Generic'. It is however required to import or upload the generic model (most likely a MOJO) beforehand. In order to to do, go to 'Data' in the top menu and select 'import files' or 'upload files'.

Advanced Generic model initialization
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

It is also possible to construct Generic model from already uploaded MOJO bytes. There is no need to import the MOJO
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
