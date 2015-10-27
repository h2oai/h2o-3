#Upgrading to H2O 3.0

##Why Upgrade? 

H2O 3.0 represents our latest iteration of H2O. It includes many improvements, such as a simplified architecture, faster and more accurate algorithms, and an interactive web UI. 

As of May 15th, 2015, this version will supersede the previous version of H2O. Support for previous versions of H2O will be provided for a limited time, but there will no longer be significant updates to the previous version of H2O. 

For a comparison of H2O and H2O 3.0, please refer to <a href="https://github.com/h2oai/h2o-dev/blob/jessica-dev-docs/h2o-docs/src/product/upgrade/H2OvsH2O-Dev.md" target="_blank">this document</a>. 

###Python Support

Python is only supported on the latest version of H2O. For more information, refer to the <a href="https://github.com/h2oai/h2o-dev/blob/master/h2o-py/README.rst" target="_blank">Python installation instructions</a>.

###Sparkling Water Support

Sparkling Water is only supported with H2O 3.0. For more information, refer to the <a href="https://github.com/h2oai/sparkling-water/blob/master/README.md" target="_blank">Sparkling Water repo</a>.

##Supported Algorithms

H2O 3.0 will soon provide feature parity with previous versions of H2O. Currently, the following algorithms are supported: 

###Supervised 

- **Generalized Linear Model (GLM)**: Binomial classification, regression (including logistic regression)
- **Distributed Random Forest (DRF)**: Binomial classification, multinomial classification, regression
- **Gradient Boosting Machine (GBM)**: Binomial classification, multinomial classification, regression
- **Deep Learning (DL)**: Binomial classification, multinomial classification, regression

###Unsupervised

- K-means
- Principal Component Analysis
- Autoencoder 


###Still In Testing

- Naive Bayes
- GLRM 

##How to Update R Scripts

Due to the numerous enhancements to the H2O package for R to make it more consistent and simplified, some parameters have been renamed or deprecated. 

To assist R users in updating their existing scripts for compatibility with H2O 3.0, a "shim" has been developed. When you run the shim on your script, any deprecated or renamed parameters are identified and a suggested replacement is provided. You can access the shim <a href="https://github.com/h2oai/h2o-dev/blob/9795c401b7be339be56b1b366ffe816133cccb9d/h2o-r/h2o-package/R/shim.R" target="_blank">here</a>.

  >**Note**: As of Slater v.3.2.0.10, this shim will no longer be available. 

Additionally, there is a <a href="https://github.com/h2oai/h2o-dev/blob/master/h2o-docs/src/product/upgrade/H2ODevPortingRScripts.md" target="_blank">document</a> available that provides a side-by-side comparison of the differences between versions. 


