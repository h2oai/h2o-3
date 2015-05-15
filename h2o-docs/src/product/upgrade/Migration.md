#H2O Migration Guide

We're excited about the upcoming release of the latest and greatest version of H2O, and we hope you are too! H2O 3.0 has lots of improvements, including: 

- Powerful Python APIs
- Flow, a brand-new intuitive web UI
- The ability to share, annotate, and modify workflows
- Versioned REST APIs with full metadata
- Spark integration using Sparkling Water
- Improved algorithm accuracy and speed

and much more! Overall, H2O has been retooled for better accuracy and performance and to provide additional functionality. If you're a current user of H2O, we strongly encourage you to upgrade to the latest version to take advantage of the latest features and capabilities. 

Please be aware that H2O 3.0 will supersede all previous versions of H2O as the primary version as of May 15th, 2015. Support for previous versions will be offered for a limited time, but there will no longer be any significant updates to the previous version of H2O. 

The following information and links will inform you about what's new and different and help you prepare to upgrade to H2O 3.0. 

Overall, H2O 3.0 is more stable, elegant, and simplified, with additional capabilities not available in previous versions of H2O. 

##Web UI Changes

Our web UI has been completely overhauled with a much more intuitive interface that is similar to IPython Notebook. Each point-and-click action is translated immediately into an individual workflow script that can be saved for later interactive and offline use.  As a result, you can now revise and rerun your workflows easily, and can even add comments and rich media. 

For more information, refer to our [Getting Started with Flow](https://github.com/h2oai/h2o-dev/blob/master/h2o-docs/src/product/flow/README.md) guide, which comprehensively documents how to use Flow. You can also view this brief [video](https://www.youtube.com/watch?v=wzeuFfbW7WE), which provides an overview of Flow in action. 

##API Users

H2O's new Python API allows Pythonistas to use H2O in their favorite environment. Using the Python command line or an integrated development environment like IPython Notebook H2O users can control clusters and manage massive datasets quickly. 

H2O's REST API is the basis for the web UI (Flow), as well as the R and Python APIs, and is versioned for stability. It is also easier to understand and use, with full metadata available dynamically from the server, allowing for easier integration by developers. 

##Java Users

Generated Java REST classes ease REST API use by external programs running in a Java Virtual Machine (JVM).

As in previous versions of H2O, users can export trained models as Java objects for easy integration into JVM applications. H2O is currently the only ML tool that provides this capability, making it the data science tool of choice for enterprise developers. 


##R Users

If you use H2O primarily in R, be aware that as a result of the improvements to the R package for H2O scripts created using previous versions (Nunes 2.8.6.2 or prior) will require minor revisions to work with H2O 3.0. 

To assist our R users in upgrading to H2O 3.0 a "shim" tool has been developed. The [shim](https://github.com/h2oai/h2o-dev/blob/9795c401b7be339be56b1b366ffe816133cccb9d/h2o-r/h2o-package/R/shim.R) reviews your script, identifies deprecated or revised parameters and arguments, and suggests replacements. 

There is also an [R Porting Guide](https://github.com/h2oai/h2o-dev/blob/master/h2o-docs/src/product/upgrade/H2ODevPortingRScripts.md) available that provides a side-by-side comparison of the algorithms in the previous version of H2O with H2O 3.0. It outlines the new, revised, and deprecated parameters for each algorithm, as well as the changes to the output. 

##Algorithm Changes

Most of the algorithms available in previous versions of H2O have been improved in terms of speed and accuracy. Currently available model types include Gradient Boosting Machine, Deep Learning, Generalized Linear Model, K-means, Distributed Random Forest, and Naive Bayes. 

There are a few algorithms that are still being refined to provide these same benefits and will be available in a future version of H2O. 

Currently, the following algorithms and associated capabilities are still in development: 

- Cross-validation 
- Grid search
- Principal Component Analysis (PCA) 
- Cox Proportional Hazards (Cox PH)

>Anomaly Detection: Has this been integrated with Deep Learning?  

Check back for updates, as these algorithms will be re-introduced in an improved form in a future version of H2O. 

**Note**: The SpeeDRF model has been removed, as it was originally intended as an optimization for small data only. This optimization will be added to the Distributed Random Forest model automatically for small data in a future version of H2O. 


##Github Users

All users who pull directly from the H2O classic repo on Github should be aware that this repo will be renamed. There are two ways update to the new H2O classic repository: 

**The simple way**

This is the easiest way to update your local repo and is recommended for most users. 

0. Enter `git remote -v` to view a list of your repositories. 
0. Copy the address your H2O classic repo (refer to the text in brackets below - your address will vary depending on your connection method):

  ```
  H2O_User-MBP:h2o H2O_User$ git remote -v
  origin	https://{H2O_User@github.com}/h2oai/h2o.git (fetch)
  origin	https://{H2O_User@github.com}/h2oai/h2o.git (push)
  ```
0. Enter `git remote set-url origin {H2O_User@github.com}:h2oai/h2o-classic.git`, where `{H2O_User@github.com}` represents the address copied in the previous step. 

**The more complicated way**

This method involves editing the Github config file and should only be attempted by users who are confident enough with their knowledge of Github to do so. 

0. Enter `vim .git/config`. 
0. Look for the `[remote "origin"]` section:

   ```
   [remote "origin"]
        url = https://H2O_User@github.com/h2oai/h2o.git
        fetch = +refs/heads/*:refs/remotes/origin/*
    ```
0. In the `url =` line, change `h2o.git` to `h2o-classic.git`. 
0. Save the changes.  

