# H2O-3 Extensions for Integration with H2O.ai Cloud

A python library containing API extensions for interaction with various cloud components.
The library currently supports integration with:
- [MLOps](https://docs.h2o.ai/mlops/)

## Install
Install prerequisites to your Python environment (Jupyter kernel):
```
pip install h2o
pip install https://s3.amazonaws.com/artifacts.h2o.ai/releases/ai/h2o/mlops/rel-0.58.0/5/h2o_mlops_client-0.58.0%2B42d986c.rel0.58.0.5-py2.py3-none-any.whl
```

Install the library:
```
# TODO UPDATE url:
pip install https://h2o-release.s3.amazonaws.com/h2o/cloud-extensions/h2o_cloud_extensions-3.39.0.99999-py2.py3-none-any.whl
``` 
## Configure
Open a Python environment and import h2o with cloud extensions and create or connect to h2o cluster
```
import h2o
import h2o_cloud_extensions as hce
h2o.init()
```

Set a H2O Cloud instance you will be connecting to:
```
hce.settings.connection.client_id = 'hac-platform-public'
hce.settings.connection.token_endpoint_url = 'https://auth.internal.dedicated.h2o.ai/auth/realms/hac/protocol/openid-connect/token'
```

Get authenticated against H2O.ai cloud (https://internal.dedicated.h2o.ai/auth/get-platform-token) and set platform token:
```
hce.settings.connection.refresh_token = "TOKEN_THAT_YOU_RECIEVED_AFTER_AUTHENTICATION"
```

### MLOps
Set MLOps instance and the project that you will utilize for publishing your models.
```
hce.settings.mlops.api_url = 'https://mlops-api.internal.dedicated.h2o.ai'
hce.settings.mlops.project_name = 'My-project-for-h2o-3-models'
```

If the project with a given name does not exist, it will be automatically created. In that case, it would be
convenient to set also a project description.
```
hce.settings.mlops.project_description = 'H2O-3 is so cool. This will be fun!'
```

## MLOps Integration
H2O-3 cloud extension library is able to publish and deploy MOJO models trained via a single H2O algorithm (estimator)
Grid Search and AutoML.

### Model Trained via a Single Algorithm (Estimator)
Train a model:
```
from h2o.estimators import H2OGradientBoostingEstimator

prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv")
prostate["CAPSULE"] = prostate["CAPSULE"].asfactor()
predictors = ["ID","AGE","RACE","DPROS","DCAPS","PSA","VOL","GLEASON"]
response = "CAPSULE"

pros_gbm = H2OGradientBoostingEstimator(nfolds=5,
                                        seed=1111,
                                        keep_cross_validation_predictions = True)
                                        
pros_gbm.train(x=predictors, y=response, training_frame=prostate)
```

#### Model Publishing
To manually publish a trained model, call:
```
pros_gbm.publish()
```

The above method call could be performed automatically straight after the model is trained if the below is set:
```
hce.settings.mlops.estimator.automatic_publishing = True
```

To check whether the model was published or not, call:
```
pros_gbm.is_published()
```

#### Model Deployment
To manually deploy a trained model, call:
```
pros_gbm.deploy(environment = "DEV")
```

The environment does not have to be specified if the below is set:
```
hce.settings.mlops.deployment_environment = "DEV"
```

A model could be automatically deployed to an environment specified with the above settings straight
after publishing the model. To achieve that, set the below:
```
hce.settings.mlops.estimator.automatic_deployment = True
```

To check whether the model was deployed to a given environment, call:
```
pros_gbm.is_deployed(environment = "DEV")
```

### Model Trained via Grid Search
Train models:
```
from h2o.estimators import H2OGradientBoostingEstimator
from h2o.grid.grid_search import H2OGridSearch

prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv")
prostate["CAPSULE"] = prostate["CAPSULE"].asfactor()
predictors = ["ID","AGE","RACE","DPROS","DCAPS","PSA","VOL","GLEASON"]
response = "CAPSULE"
gbm_params = {'learn_rate': [i * 0.01 for i in range(1, 11)],
                'max_depth': list(range(2, 11)),
                'sample_rate': [i * 0.1 for i in range(5, 11)],
                'col_sample_rate': [i * 0.1 for i in range(1, 11)]}

search_criteria = {'strategy': 'RandomDiscrete', 'max_models': 5, 'seed': 2}

gbm_grid = H2OGridSearch(model=H2OGradientBoostingEstimator,
                          hyper_params=gbm_params,
                          search_criteria=search_criteria)
                          
gbm_grid.train(x=predictors, y=response, training_frame=prostate)
```

#### Model Publishing
To manually publish all trained models, call:
```
gbm_grid.publish()
```

The above method call could be performed automatically straight after all models are trained if the below is set:
```
hce.settings.mlops.grid_search.automatic_publishing = True
```

To check whether all models were published, call:
```
gbm_grid.is_published()
```

#### Model Deployment
To manually deploy all published models, call:
```
gbm_grid.deploy(environment = "DEV")
```

The environment does not have to be specified if you specify the below:
```
hce.settings.mlops.deployment_environment = "DEV"
```

Models could be automatically deployed to an environment specified with the above settings straight
after publishing all models. To achieve that, set the below:
```
hce.settings.mlops.grid_search.automatic_deployment = True
```

To check whether all models were deployed to a given environment, call:
```
gbm_grid.is_deployed(environment = "DEV")
```

### Model Trained via AutoML
Run AutoML to train models:
```
from h2o.automl import H2OAutoML

train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/higgs/higgs_train_10k.csv")
x = train.columns
y = "response"
x.remove(y)
train[y] = train[y].asfactor()

aml = H2OAutoML(max_models=5, seed=1)

aml.train(x=x, y=y, training_frame=train)
```

#### Model Publishing
To manually publish all trained models, call:
```
aml.publish(strategy="all")
```

The parameter `stragegy` is set to `best` by default, which publishes only the best performing model from leaderboard.
The other option is to set the parameter to `"all"`, which publishes all leaderboard models. The default parameter value
could be changed by the below command:
```
hce.settings.mlops.automl.publishing_strategy = "all"
```

The call of `aml.publish()` could be performed automatically straight after model training is finished if the below is set:
```
hce.settings.mlops.automl.automatic_publishing = True
```

To check whether all models were published (Change to `stragegy="best"` if you're interested only in the best model),
call:
```
aml.is_published(strategy="all")
```

#### Model Deployment
To manually deploy all published models, call:
```
aml.deploy(environment = "DEV")
```

The environment does not have to be specified if you specify the below:
```
hce.settings.mlops.deployment_environment = "DEV"
```

Models could be automatically deployed to an environment specified with the above settings straight
after publishing all models. To achieve that, set the below:
```
hce.settings.mlops.grid_search.automatic_deployment = True
```

To check whether all models published models were deployed to a given environment, call:
```
aml.is_deployed(environment = "DEV")
```
