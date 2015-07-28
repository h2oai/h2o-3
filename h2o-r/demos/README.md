# Running R Examples 

## Setting up Environment

To set up your R environment to run these examples the user will need to download and install the `h2o` package in R. The instructions for doing so are on the [top level](../README.md).


## R Examples

### Available Demos

  * [`Predict Airlines Delays`](large/airlines_delay.R) - Uses historical airlines flight data to build multiple classification models to label any flight as either delayed or not delayed.

  * [`Predict Airlines Delays with Weather`](large/airlines_delay_w_weather.R) - Uses historical airlines flight and weather data and joins the two datasets into one large table, then builds multiple classification models to label any flight as either delayed or not delayed.
  
  * [`NYC Citibike Demand with Weather`](large/citibike_nyc.R) - Uses monthly bike ride data for the past two years to predict bike demand at each bike share station. Weather data is also incorporated to better predict bike usage.
  
  
### Corresponding Datasets

  *  **Airlines Data** for [`Predict Airlines Delays`](large/airlines_delay.R) and [`Predict Airlines Delays with Weather`](large/airlines_delay_w_weather.R) - Any of the following datasets will work for the demo; choose an appropriate dataset size based on speed and scale.
  
  	* [2 Thousand Rows - 4.3MB](https://s3.amazonaws.com/h2o-airlines-unpacked/allyears2k.csv)
  	* [5.8 Million Rows - 580MB](https://s3.amazonaws.com/h2o-airlines-unpacked/airlines_all.05p.csv)
  	* [152 Million Rows - 14.5GB](https://s3.amazonaws.com/h2o-airlines-unpacked/allyears.1987.2013.csv)
  	
  *  **Chicago Weather Data** for [`Predict Airlines Delays with Weather`](large/airlines_delay_w_weather.R)
  
  	* [2005-2008 Weather Data Near Chicago Airport](https://s3.amazonaws.com/h2o-public-test-data/smalldata/chicago/Chicago_Ohare_International_Airport.csv)
  
  *  **Citibike Data** for [`NYC Citibike Demand with Weather`](large/citibike_nyc.R) - Choose the amount of bike ride data you want for your analysis; the range is from a single month to all available months.
  
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
  	
  
  *  **NYC Weather Data** for [`NYC Citibike Demand with Weather`](large/citibike_nyc.R)
  
    * [NYC Hourly Weather - 2013](https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/citibike-nyc/31081_New_York_City__Hourly_2013.csv)    
    * [NYC Hourly Weather - 2014](https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/citibike-nyc/31081_New_York_City__Hourly_2014.csv)
    * [NYC Hourly Weather - 2015](https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/citibike-nyc/31081_New_York_City__Hourly_2015.csv)


###  Running Examples from Command Line

0. If necessary, edit the working directory for each script.
0. Download the appropriate dataset and edit the dataset path in the R script if it is not located in your working directory.
0. To run a R demo script, run `R -f` followed by the file. For example to run the airlines demo:

```
R -f airlines_delay.R
```

###  Running Examples from R
0. If necessary, edit the working directory for each script.
0. Download the appropriate dataset and edit the dataset path in the R script if it is not located in your working directory.
0. To run a R demo script, open up the R script and execute the notebook line by line using Control+Return/Enter.