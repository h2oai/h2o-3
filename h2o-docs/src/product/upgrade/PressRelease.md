#H2O 3.0 is here!

The new version of H2O offers a single integrated and tested platform for enterprise and open-source use, enhanced usability through a new web user interface (UI) with embeddable workflows, elegant APIs, and direct integration for Python and Sparkling Water. 

H2O is designed to be scalable to meet the needs of both enterprise and open-source users and easy to use on either a single laptop or a cluster, with simple installations for [R](http://h2o-release.s3.amazonaws.com/h2o/{{branch_name}}/{{build_number}}/index.html#R), [Python](http://h2o-release.s3.amazonaws.com/h2o/{{branch_name}}/{{build_number}}/index.html#Python), [Hadoop](http://h2o-release.s3.amazonaws.com/h2o/{{branch_name}}/{{build_number}}/index.html#Hadoop), and [Maven](http://h2o-release.s3.amazonaws.com/h2o/{{branch_name}}/{{build_number}}/index.html#Maven). 

H2O's [APIs](http://h2o-release.s3.amazonaws.com/h2o/{{branch_name}}/{{build_number}}/docs-website/h2o-docs/index.html#route-reference) enable developers to innovate more rapidly and deploy smarter business applications. Prediction APIs allow developers to train and test models on large datasets from within their preferred application development environment, such as R or Python. H2O's REST APIs are thoroughly documented and provide dynamic metadata and JSON [schema](http://h2o-release.s3.amazonaws.com/h2o/{{branch_name}}/{{build_number}}/docs-website/h2o-docs/index.html#schema-reference). 

The REST API is the basis of the beautiful user experience of H2O Flow, the R package, and the Python module. Regardless of environment, H2O can export trained models as Java objects (POJO) that can easily be integrated into applications and real-time systems like Spark Streaming and Apache Storm™. H2O 3.0 seamlessly embeds machine learning algorithms into the framework of other applications. [Sparkling Water](https://github.com/h2oai/sparkling-water/blob/master/DEVEL.md) is a powerful example of bringing H2O algorithms to the developer community of Apache Spark™. 

H2O Flow seamlessly blends a modern web interface with command-line computing, allowing users to interactively import files, build models, and iteratively improve them. Using the point-and-click UI implemented in each H2O operation, users can render all data and models as beautiful graphical and tabular output. Each of these clickable actions are translated into individual workflow scripts that can be saved for later use. 

There is a growing list of algorithms that are available out of the box, including Gradient Boosting Machine, Deep Learning, Generalized Linear Model, K-Means, Distributed Random Forests, and Naïve Bayes. Other algorithms and associated capabilities, such as PCA and grid search, are currently in development, so look for these features in a future version of H2O. 

There are some helpful resources available to assist users in upgrading to H2O 3.0: 

- <a href="https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/product/upgrade/Migration.md" target="_blank">Migration Guide</a>: This document provides a comprehensive guide to assist users in upgrading to H2O 3.0. It gives an overview of the changes to the algorithms and the web UI introduced in this version and describes the benefits of upgrading for users of R, APIs, and Java. 

- <a href="https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/product/upgrade/H2ODevPortingRScripts.md" target="_blank">Porting R Scripts</a>: This document is designed to assist users who have created R scripts using previous versions of H2O. Due to the many improvements in R, scripts created using previous versions of H2O need some revision to work with H2O 3.0. This document provides a side-by-side comparison of the changes in R for each algorithm, as well as overall structural enhancements R users should be aware of, and provides a link to a tool that assists users in upgrading their scripts. 

- <a href="https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/product/flow/RecentChanges.md" target="_blank">Recent Changes</a>: This document describes the most recent changes in the latest build of H2O. It lists new features, enhancements (including changed parameter default values), and bug fixes for each release, organized by sub-categories such as Python, R, and Web UI. 

- <a href="https://github.com/h2oai/h2o-3/blob/jessica-dev-docs/h2o-docs/src/product/upgrade/H2OvsH2O-Dev.md" target="_blank">H2O Classic vs H2O 3.0</a>: This document presents a side-by-side comparison of H2O 3.0 and the previous version of H2O. It compares and contrasts the features, capabilities, and supported algorithms between the versions. If you'd like to learn more about the benefits of upgrading, this is a great source of information. 

- <a href="https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/product/flow/images/H2O-Algorithms-Road-Map.pdf" target="_blank">Algorithms Roadmap</a>: This document outlines our currently implemented features and describes which features are planned for future software versions. If you'd like to know what's up next for H2O, this is the place to go. 





