H2O-3 MLFlow Flavor
===================

A tiny library containing a `MLFlow <https://mlflow.org/>`_ flavor for working with H2O-3 MOJO and POJO models.

# Logging Models to MLFlow Registry
===================================

The model that was trained with H2O-3 runtime can be exported to MLFlow registry with `log_model` function.:

```python

import mlflow

import h2o*mlflow*flavor

mlflow.set*tracking*uri("http://127.0.0.1:8080")

h2o_model = ... training phase ...

with mlflow.start*run(run*name="myrun") as run:

	h2o\_mlflow\_flavor.log\_model(h2o\_model=h2o\_model, 

								artifact\_path="folder",

								model\_type="MOJO",

								extra\_prediction\_args=["\-\-predictCalibrated"])

```

Compared to `log_model` functions of the other flavors being a part of MLFlow, this function has two extra arguments:
	
*  **model_type** - It indicates whether the model should be exported as 

					[MOJO](https://docs.h2o.ai/h2o/latest\-stable/h2o\-docs/mojo\-quickstart.html#what\-is\-a\-mojo)

					or [POJO](https://docs.h2o.ai/h2o/latest\-stable/h2o\-docs/pojo\-quickstart.html#what\-is\-a\-pojo).

					The default value is `MOJO`.

*  **extra*prediction*args** A list of extra arguments for java scoring process. Possible values:

	\* `\-\-setConvertInvalidNum` \- The scoring process will convert invalid numbers to NA.

	\* `\-\-predictContributions` \- The scoring process will Return also Shapley values a long with the predictions.

								 Model must support that Shapley values, otherwise scoring process will throw an error. 

	\* `\-\-predictCalibrated` \- The scoring process will also return calibrated prediction values.
   
The `save*model` function that persists h2o binary model to MOJO or POJO has the same signature as the `log*model` function.

# Extracting Information about Model
====================================

The flavor offers several functions to extract information about the model.

* `get*metrics(h2o*model, metric_type=None)` - Extracts metrics from the trained H2O binary model. It returns dictionary and 

takes following parameters:

	\* `h2o\_model` \- An H2O binary model.

	\* `metric\_type` \- The type of metrics. Possible values are "training", "validation", "cross\_validation".

					  If parameter is not specified, metrics for all types are returned.

* `get*params(h2o*model)` - Extracts training parameters for the H2O binary model. It returns dictionary and expects one

parameter:

	\* `h2o\_model` \- An H2O binary model.

* `get*input*example(h2o*model, number*of*records=5, relevant*columns_only=True)` - Creates an example Pandas dataset 

from the training dataset of H2O binary model. It takes following parameters:

	\* `h2o\_model` \- An H2O binary model.

	\* `number\_of\_records` \- A number of records that will be extracted from the training dataset.

	\* `relevant\_columns\_only` \- A flag indicating whether the output dataset should contain only columns required by 

	the model. Defaults to `True`.
  
The functions can be utilized as follows:

```python

import mlflow

import h2o*mlflow*flavor

mlflow.set*tracking*uri("http://127.0.0.1:8080")

h2o_model = ... training phase ...

with mlflow.start*run(run*name="myrun") as run:

	mlflow.log\_params(h2o\_mlflow\_flavor.get\_params(h2o\_model))

	mlflow.log\_metrics(h2o\_mlflow\_flavor.get\_metrics(h2o\_model))

	input\_example = h2o\_mlflow\_flavor.get\_input\_example(h2o\_model)

	h2o\_mlflow\_flavor.log\_model(h2o\_model=h2o\_model,

								input\_example=input\_example,

								artifact\_path="folder",

								model\_type="MOJO",

								extra\_prediction\_args=["\-\-predictCalibrated"])

```

# Model Scoring
===============

After a model obtained from the model registry, the model doesn't require h2o runtime for ability to score. The only thing

that model requires is a `h2o-gemodel.jar` which was persisted with the model during saving procedure. 

The model could be loaded by the function `load*model(model*uri, dst_path=None)`. It returns an objecting making

predictions on Pandas dataframe and takes the following parameters:

* `model_uri` - An unique identifier of the model within MLFlow registry.

* `dst_path` - (Optional) A local filesystem path for downloading the persisted form of the model. 

The object for scoring could be obtained also via the `pyfunc` flavor as follows:

```python

import mlflow

mlflow.set*tracking*uri("http://127.0.0.1:8080")

logged_model = 'runs:/9a42265cf0ef484c905b02afb8fe6246/iris'

loaded*model = mlflow.pyfunc.load*model(logged_model)

import pandas as pd

data = pd.read_csv("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")

loaded_model.predict(data)

```

