Hierarchical Generalized Linear Model (HGLM) 
============================================

Hierarchical linear models (HLM) is used in situations where measurements are taken with clusters of data and there are effects of the cluster that can affect the coefficient values of GLM. For instance, if we measure the students' performances from multiple schools along with other predictors like family annual incomes, students' health, school type (public, private, religious, etc.), and etc., we suspect that students from the same school will have similar performances than students from different schools. Therefore, we can denote a coefficient for predictor :math:`m \text{ as } \beta_{mj}` where :math:`j` denotes the school index in our example. :math:`\beta_{0j}` denotes the intercept associated with school :math:`j`.


Defining an HGLM model
----------------------
Parameters are optional unless specified as *required*.

Algorithm-specific parameters
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

- **gen_syn_data**
- **group_column**
- **random_intercept**
- **em_epsilon**
- **method**
- **random_columns**
- **tau_e_var_init**
- **tau_u_var_init**
- **initial_t_matrox**
- **initial_random_effects**
- **initial_fixed_effects**

GLM-family parameters
~~~~~~~~~~~~~~~~~~~~~

- family
- rand_family
- plug_values


Common parameters
~~~~~~~~~~~~~~~~~

- max_iterations
- standardize
- missing_values_handling
- seed
- score_values_handling
- score_each_iteration
- custom_metric_func
- max_runtime_secs
- weights_column
- offset_column
- ignore_const_cols
- ignored_columns
- response_column
- validation_frame
- training_frame
- model_id