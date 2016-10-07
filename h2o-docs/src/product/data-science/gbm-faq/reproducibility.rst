Reproducibility
^^^^^^^^^^^^^^^

- **Given the same training set and the same GBM parameters, will GBM produce a different model with two different validation data sets, or the same model?**

 The same model will be generated, unless early stopping is turned on (by default it is turned off), which could lead to slightly different models. Using different validation sets would only affect the model being built if early stopping is turned on - early stopping uses the validation set to determine when to stop building more trees. 

- **How deterministic is GBM?**

 As long as you set the seed, GBM is deterministic up to floating point rounding errors (out-of-order atomic addition of multiple threads during histogram building). This means that if you set a seed, your results will be reproducible (even if, for example, you change the number of nodes in your cluster, change the way you ingest data, or change the number of files your data lives in, among many other examples). 
