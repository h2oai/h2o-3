Launching iPython Examples
=========================

## Prerequisites:

- Python 2.7

---

Install iPython Notebook
-------------------------

1. Download pip, a Python package manager (if it's not already installed):

    `$ sudo easy_install pip`

2. Install iPython using pip install:

    `$ sudo pip install "ipython[notebook]"`

---

Install dependencies
--------------------

This module uses requests and tabulate modules, both of which are available on pypi, the Python package index.

    $ sudo pip install requests
    $ sudo pip install tabulate
  
---

Install and Launch H2O
----------------------

To use H2O in Python, follow the instructions on the **Install in Python** tab after selecting the H2O version on the [H2O Downloads page](http://h2o.ai/download). 

Launch H2O outside of the iPython notebook. You can do this in the top directory of your H2O build download. The version of H2O running must match the version of the H2O Python module for Python to connect to H2O. 
To access the H2O Web UI, go to [https://localhost:54321](https://localhost:54321) in your web browser.

---

Open Demos Notebook
-------------------

Open the prostate_gbm.ipynb file. The notebook contains a demo that starts H2O, imports a prostate dataset into H2O, builds a GBM model, and predicts on the training set with the recently built model. Use Shift+Return to execute each cell and proceed to the next cell in the notebook .

    $ ipython notebook prostate_gbm.ipynb

All demos are available here:

 * [iPython Demos](https://github.com/h2oai/h2o-3/tree/master/h2o-py/demos)

---


Running Python Examples
-----------------------

To set up your Python environment to run these examples, download and install H2O from Python using the instructions above. 


### Available Demos

- [Predict Airline Delays](https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/airlines_demo_small.ipynb) - Uses historical airlines flight data to build multiple classification models to label any flight as either delayed or not delayed.
- [Chicago Crime Rate](https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/H2O_chicago_crimes.ipynb) - Uses weather and city statistics to compare arrest rates with the total crimes for each category. 
- [NYC Citibike Demand with Weather](https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/citi_bike_large.ipynb) - Takes monthly bike ride data (~10 million rows) for the past two years to predict bike demand at each bike share station. Weather data is also incorporated to better predict bike usage.
- [NYC Citibike Demand with Weather - smaller dataset](https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/citi_bike_small.ipynb) - Takes monthly bike ride data (~1 million rows) for the past two years to predict bike demand at each bike share station. Weather data is also incorporated to better predict bike usage.
- [Confusion Matrix & ROC](https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/cm_roc.ipynb) - Creates a GBM and GLM model using the airlines dataset, including confusion matrices, ROCs, and scoring histories. 
- [Imputation](https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/imputation.ipynb) - Substitutes values for missing data (imputes) the airlines dataset. 
- [Not Equal Factor](https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/not_equal_factor.ipynb) - Try to slice the airlines dataset using != `factor_level`. 
- [Airline Confusion Matrices](https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/confusion_matrices_binomial.ipynb) - Uses the airlines dataset to generate confusion matrices for algorithm performance analysis.
- [Deep Learning for Prostate Cancer Analysis](https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/deeplearning.ipynb) - Uses the prostate dataset to build a Deep Learning model. 
- [Airlines Prep](https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/prep_airlines.ipynb) - Condition the airline dataset by filtering out NAs if the departure delay in the input dataset is unknown. Anything longer than `minutesOfDelayWeTolerate` is treated as delayed. 
- [GBM model using prostate dataset](https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/prostate_gbm.ipynb) - Creates a GBM model using the prostate dataset.
- [Balance Classes](https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/rf_balance_classes.ipynb) - Imports the airlines dataset, parses it, displays a summary, and runs GLM with a binomial link function. 
- [Clustering with KMeans](https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/kmeans_aic_bic_diagnostics.ipynb) - Demonstrates kmeans clusters and different diagnostics for selecting the number of clusters.  Link to data is provided in the notebook.
- [EEG Eye State](https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/H2O_tutorial_eeg_eyestate.ipynb) - Uses EEG data collected from an Emotiv Neuroheadset and classifies eye state (open vs closed) with a GBM.  
- [Tree fetch demo](https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/tree_demo.ipynb) - Trains a basic GBM model based on Airlines dataset & fetches the tree behind the model. Exploration of the tree fetched is explained.



### Corresponding Datasets


#### Airlines Datasets 

- [AirlinesTest](https://github.com/h2oai/h2o-2/raw/master/smalldata/airlines/AirlinesTest.csv.zip) and [AirlinesTrain](https://github.com/h2oai/h2o-2/raw/master/smalldata/airlines/AirlinesTrain.csv.zip) - Used in [Confusion Matrix & ROC](https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/cm_roc.ipynb), [Airline Confusion Matrices](https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/confusion_matrices_binomial.ipynb), [Balance Classes](https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/rf_balance_classes.ipynb) and [Balance Classes](https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/rf_balance_classes.ipynb)

- [Allyears2k_headers](https://github.com/h2oai/h2o-2/raw/master/smalldata/airlines/allyears2k_headers.zip) - Used in [Predict Airline Delays](https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/airlines_demo_small.ipynb), [Imputation](https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/imputation.ipynb), [Not Equal Factor](https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/not_equal_factor.ipynb), and [Airlines Prep](https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/prep_airlines.ipynb)

#### Chicago Crime

- [chicagoAllWeather](https://github.com/h2oai/sparkling-water/raw/master/examples/smalldata/chicagoAllWeather.csv), [chicagoCensus](https://github.com/h2oai/sparkling-water/raw/master/examples/smalldata/chicagoCensus.csv), and [chicagoCrimes10k](https://github.com/h2oai/sparkling-water/raw/master/examples/smalldata/chicagoCrimes10k.csv) - Used in [Chicago Crime Rate](https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/H2O_chicago_crimes.ipynb)

#### Citibike Data
 - Used in [NYC Citibike Demand with Weather](https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/citi_bike_large.ipynb) 
  
  	* [2013-07 - 157MB](https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/citibike-nyc/2013-07.csv)
  	* [2013-08 - 186MB](https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/citibike-nyc/2013-08.csv)
  	* [2013-09 - 193MB](https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/citibike-nyc/2013-09.csv)
  	* [2013-10 - 193MB](https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/citibike-nyc/2013-10.csv)
  	* [2013-11 - 126MB](https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/citibike-nyc/2013-11.csv)
  	* [2013-12 - 83MB](https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/citibike-nyc/2013-12.csv)
  	* [2014-01 - 56MB](https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/citibike-nyc/2014-01.csv)
  	* [2014-02 - 42MB](https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/citibike-nyc/2014-02.csv)
  	* [2014-03 - 82MB](https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/citibike-nyc/2014-03.csv)
  	* [2014-04 - 125MB](https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/citibike-nyc/2014-04.csv)
  	* [2014-05 - 161MB](https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/citibike-nyc/2014-05.csv)
  	* [2014-06 - 175MB](https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/citibike-nyc/2014-06.csv)
  	* [2014-07 - 180MB](https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/citibike-nyc/2014-07.csv)
  	* [2014-08 - 180MB](https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/citibike-nyc/2014-08.csv)
  	
 -  [2013-10 - 193MB](https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/citibike-nyc/2013-10.csv) - Used in [NYC Citibike Demand with Weather - smaller dataset](https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/citi_bike_small.ipynb)

-  **NYC Weather Data** - Used in [NYC Citibike Demand with Weather](https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/citi_bike_large.ipynb) and [NYC Citibike Demand with Weather - smaller dataset](https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/citi_bike_small.ipynb)
  
    * [NYC Hourly Weather - 2013](https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/citibike-nyc/31081_New_York_City__Hourly_2013.csv)    
    * [NYC Hourly Weather - 2014](https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/citibike-nyc/31081_New_York_City__Hourly_2014.csv)


#### Prostate Data

- [Prostate Dataset](https://github.com/h2oai/sparkling-water/raw/master/examples/smalldata/prostate.csv) - Used in [Deep Learning for Prostate Cancer Analysis](https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/deeplearning.ipynb) and [GBM model using prostate dataset](https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/prostate_gbm.ipynb)
