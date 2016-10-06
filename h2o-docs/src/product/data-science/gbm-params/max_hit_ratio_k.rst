``max_hit_ratio_k``
-------------------

- Available in: GBM, DRF, Deep Learning
- Hyperparameter: no

Description
~~~~~~~~~~~
Hit ratios can be used to evaluate the performance of a model. The hit ratio is the percentage of instances where the actual class of an observation is in the top *k* classes predicted by the model. The ``max_hit_ratio_k`` option specifies the maximum number (top *k*) of predictions to use for hit ratio computation. 

Note that this option is available for multiclass problems only and is set to 0 (disabled) by default.

