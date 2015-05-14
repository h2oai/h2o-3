#H2O Migration Guide

We're excited about the upcoming release of the latest and greatest version of H2O, and we hope you are too! H2O 3.0 has lots of improvements, including: 

- Python support
- A simplified R library
- A brand-new intuitive web UI, Flow
- The ability to share, annotate, and modify workflows
- Versioned APIs with full metadata
- Sparkling Water support
- Improved algorithm accuracy and speed

and much more! Overall, H2O has been retooled for better accuracy and performance and to provide additional functionalities. If you're a current user of H2O, we strongly encourage you to upgrade to the latest version to take advantage of the latest features and capabilities. 

Please be aware that H2O 3.0 will supersede all previous versions of H2O as the primary supported version as of May 15th, 2015. Support for previous versions will be offered for a limited time, but there will no longer be any significant updates to the previous version of H2O. 

The following information and links will inform you about what's new and different and help you prepare to upgrade to H2O 3.0. 

##API Users

H2O's new Python API allows Python users to use H2O in their favorite environment. Using the Python command line or an integrated development environment like iPython Notebook, H2O users can control clusters and manage massive datasets quickly. 

H2O's REST API is the basis for the web UI (Flow), as well as the R and Python APIs, and is versioned for stability. It is also easier to understand and use, with full metadata available dynamically from the server, allowing for easier integration by developers. 

##Java Users

There are some significant improvements for Java users as well: 

- Generated Java REST classes enable REST API use by external programs running in a Java Virtual Machine (JVM) 
- Export trained models as a Java object for easy integration into JVM applications

##Github Users

All users who pull directly from the H2O repo on github should be aware that the H2O-Dev repo will be renamed. To update to the new repository: 

>To be added (once new repo has been set up)

##R Users

If you use H2O primarily in R, be aware that as a result of the improvements to the R package for H2O, scripts created using previous versions (Nunes 2.8.6.2 or prior) will not work with H2O 3.0. 

However, to assist our R users in upgrading to H2O 3.0, a "shim" tool has been developed. The [shim](https://github.com/h2oai/h2o-dev/blob/9795c401b7be339be56b1b366ffe816133cccb9d/h2o-r/h2o-package/R/shim.R) reviews your script, identifies deprecated or revised parameters and arguments, and suggests replacements. 

There is also an [R Porting Guide](https://github.com/h2oai/h2o-dev/blob/master/h2o-docs/src/product/upgrade/H2ODevPortingRScripts.md) available that provides a side-by-side comparison of the algorithm in the previous version of H2O with H2O 3.0. It outlines the new, revised, and deprecated commands and parameters for each algorithm, as well as the changes to the output. 

##Algorithm Changes

In general, most of the algorithms available in previous versions of H2O have been improved in terms of speed and accuracy. Currently available model types include Gradient Boosting Machine, Deep Learning, Generalized Linear Model, K-means, Distributed Random Forest, and Naive Bayes. 

However, there are a few algorithms that are still being refined to provide these same benefits and will be available in a future version of H2O. 

Currently, the following algorithms and associated capabilities are still in development: 

- Cross-validation 
- Grid search
- Principal Component Analysis (PCA) 
- Cox Proportional Hazards (Cox PH)

>Anomaly Detection: Has this been integrated with Deep Learning?  

Stay tuned, as these features will be back soon and better than before! 

**Note**: Random Forest - Big Data has been deprecated, as the Distributed Random Forest algorithm in H2O 3.0 detects the data type (classification or regression) based on the data, so now you no longer have select BigData or SpeeDRF. 

##Web UI Changes

Our web UI has been completely overhauled into a much more intuitive format that is similar to iPython Notebooks. As a result, you can now revise, download, and rerun your workflows easily. Commands are entered in cells, which can be rearranged as needed, then saved and exported to share with other Flow users. You can even add comments and rich media to your workflow. 

For more information, refer to our [Getting Started with Flow](https://github.com/h2oai/h2o-dev/blob/master/h2o-docs/src/product/flow/README.md) guide, which comprehensively documents how to use Flow. You can also view this brief [video](https://www.youtube.com/watch?v=wzeuFfbW7WE), which provides a visual overview of Flow in action. 

Overall, H2O 3.0 is more stable, elegant, and simplified, with additional capabilities not available in previous versions of H2O. 

