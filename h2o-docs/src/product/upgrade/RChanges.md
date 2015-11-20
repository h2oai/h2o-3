#R Interface Improvements for H2O

Recent improvements in the R wrapper for H2O may cause previously written R scripts to be inoperable. This document describes these changes and provides guidelines on updating scripts for compatibility. 

##H2O Connection Object

The H2O connection object (`conn`) has been removed from nearly all calls.
The `conn` object is still used in the `h2o.clusterIsUp` command. 

Any `conn` references for commands other than `h2o.clusterIsUp` must be removed from scripts to ensure compatibility. 

##Changes to `apply`

The data shape returned by `apply` is now identical to the default behavior in R. Any column-wide changes produce column-wide results. 

For example, in previous versions, if `apply` on `MARGIN` was equal to `2`, then 200 rows would be returned in one column. Now, 200 columns are produced in one row. 

To revert to the previous behavior, use the transpose function using the R command `t`. 

##Temp Management

For users who regularly remove the temporary data frames and keys manually, the temp management rules have been improved in the following ways:

- For a data frame created in R: 

  - If no name is specified, a temporary name is assigned, which is deleted when the cluster is stopped or after a R GC cycle

  - If a name is specified, that name is used until it is manually deleted

- Parsed input data and models are given names and not automatically deleted when the cluster is stopped; a temporary column holds the parsed data until it is deleted during the R GC cycle 

- If your cluster is running low on memory, run an R GC cycle to delete temporary data frames and keys


##S4 to S3

The internal H2O object, which was previously an S4 object, is now an S3 object. You must use S3 operations to access objects (instead of S4). The risk of overloading depends on whether the package overloads the existing package type. 

##`frame_id` to `id`

The `frame_id` property has been renamed to `id`. This property is used in the `h2o.getFrame` command. 