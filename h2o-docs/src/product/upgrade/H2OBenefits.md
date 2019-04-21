# Benefits of H2O
 
 H2O is a machine learning application designed to help you analyze your data. By helping you create insightful models from your data, H2O makes it easier to observe trends and spot anomalies. 

The main benefits to using H2O are: 
 
 - **Scalability**: Run on a cloud computing solution like EC2/AWS or a single laptop
 - **Easy setup**: Download the zip file, run a few lines in a command-line interface (such as Terminal for OS X or Command Prompt for Windows), and launch H2O using your browser
 - **Multiple client support**: Use H2O's built-in web-based UI, or use H2O within R or Python
 - **Fast performance**: Create models in minutes using H2O's unique in-memory capabilities
  
 
## What H2O Provides

### Better Predictions

- Powerful, ready-to-use algorithms that derive insights from all your data

### Speed

- In-memory parallel processing for real-time responsiveness, increasing efficiency, and running models without sampling

### Ease of Use

- Flow, an intuitive web UI that is designed to simplify a data scientist's workflow, allows you to modify, save, export, and share your workflow with others

### Extensibility

- Seamless Hadoop integration with distributed data ingestion from HDFS and S3
- Models are built using Java and can be exported as Plain Old Java Objects (POJO) for integration in your custom application

### Scalability

- Easy to iterate, develop, and train models on large data without extra modeling time

### Real-time Scoring

- Predict and score more accurately and 10x faster than the next best technology on the market


### Modeling with State of the Art Machine Learning Algorithms
Model | Description
--------------|------------
Generalized Linear Models (GLM) | A flexible generalization of ordinary linear regression for response variables that have error distribution models other than a normal distribution. GLM unifies various other statistical models, including linear, logistic, Poisson, and more.
Decision Trees | A decision support tool that uses a tree-like graph or model of decisions and their possible consequences.
Gradient Boosting (GBM) | A method to produce a prediction model in the form of an ensemble of weak prediction models. It builds the model in a stage-wise fashion and is generalized by allowing an arbitrary differentiable loss function. It is one of the most powerful methods available today.
K-Means | A method to uncover groups or clusters of data points often used for segmentation. It clusters observations into k certain points with the nearest mean.
Anomaly Detection | Identify the outliers in your data by invoking a powerful pattern recognition model.
Deep Learning | Model high-level abstractions in data by using non-linear transformations in a layer-by-layer method. Deep learning is an example of unsupervised learning and can make use of unlabeled data that other algorithms cannot.
Naïve Bayes | A probabilistic classifier that assumes the value of a particular feature is unrelated to the presence or absence of any other feature, given the class variable. It is often used in text categorization.
Stacked Ensembles | A supervised ensemble machine learning algorithm that finds the optimal combination of a collection of prediction algorithms using a process called stacking.
XGBoost | An optimized distributed gradient boosting library designed to be highly efficient, flexible, and portable. This algorithm provides parallel tree boosting (also known as GBDT, GBM) that solves many data science problems in a fast and accurate way.
Word2vec | An algorithm that takes a text corpus as an input and produces the word vectors as output. The algorithm first creates a vocabulary from the training text data and then learns vector representations of the words.

### Scoring Models with Confidence
Score Tool | Description	
-----|------------
Predict | Generate outcomes of a data set with any model. Predict with GLM, GBM, Decision Trees or Deep Learning models.
Confusion Matrix | Visualize the performance of an algorithm in a table to understand how a model performs.
AUC | A graphical plot to visualize the performance of a model by its sensitivity, true positive, false positive to select the best model.
*HitRatio | A classification matrix to visualize the ratio of the number of correctly classified and incorrectly classified cases.
*Multi-Model Scoring | Compare and contrast multiple models on a data set to find the best performer to deploy into production.

--- 

### Use Cases

- Fraud detection 
- Churn identification to prevent turnover
- Predictive modeling for better marketing
- Profiling and behavior analysis
- Ad placement optimization to identify key metrics
- One-to-one marketing for improved campaign analysis
- Evaluation of ad campaign effectiveness
- Customer classification to predict purchase behavior or renewal rates

### Customer Examples

- Cisco saw a 15x increase in speed after implementing H2O into their Propensity to Buy (P2B) modeling factory.
- Paypal uses H2O's Deep Learning algorithm for fraud detection and prevention.
- ShareThis uses H2O for AdTech ROI maximization to optimize their advertising campaign placement. 
- MarketShare uses H2O for marketing optimization to improve efficiency in cross-channel attribution and forecasting. 

 
## Required Resources
 
### Hardware and Software

- Java is required to run H2O. GNU compiler for Java and Open JDK are not supported. 
- The amount of memory required depends on the size of your data. We recommend having four times as much memory as your largest dataset. 
- To view a one-page document that outlines the system configurations we recommend, click [here](http://h2o.ai/product/recommended-systems-for-h2o/). 

### Data

H2O works with tabular data, which can be imported as a single file or as a directory of files. The following formats are supported: 
 
CSV (delimited) files
ORC
SVMLight
ARFF
XLS
XLSX
Avro (without multifile parsing or column type modification)
Parquet

>Note that ORC is available only if H2O is running as a Hadoop job.
 
 The data does not need to be perfect, as some munging can be performed within H2O (such as excluding columns with a specified percentage of missing values). However, the more precise your dataset is, the more accurate your models will be. 
 
### Support Team

H2O is designed to be easy to both set up and use, but we recommend assembling a team that includes: 

- A data scientist to create the models
- An IT specialist to help with deployment, especially for a large multi-node environment
- A UX developer, if you want to create a front-end interface for your application that uses H2O

Our H2O team will work with you to help you get started with H2O. 
