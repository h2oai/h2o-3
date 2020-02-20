Which H2O package should I install?

The H2O distribution zip file contains two Python installation artifacts (wheel files): h2o and h2o_client. You can install the full-featured "h2o" package that can be used in a standalone setup (as well as cluster deployment), or you can choose client-only version of the package - "h2o_client".

- h2o: Universal deployment package - can be used in standalone mode (eg. H2O started on users laptop) or it can be used to connect to an H2O cluster. This is what most users will choose to install.
- h2o_client: A variant of the h2o package that doesn't come with the H2O java code and cannot be used in standalone deployments. This version is suited especially for enterprise deployments where users are connecting to H2O clusters, and starting a standalone H2O instance on an edge node needs to be prevented.

Both packages provide identical APIs and sets of features.
