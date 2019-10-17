# H2O Custom Functions

## Motivation
Provide a way to represent custom functions to allow user to define their
own custom loss functions and evaluation metrics.

## Backend

### Main idea
>**Object stored in K/V can contain code which can be loaded dynamically
at runtime**

The H2O K/V store can hold any values including unstructured byte array.
The byte array can represent a jar file which contains any resources.
That means it can include not only class files but also code in languages which
supports JSR 223 (scripting extension of JVM).

Such code can be loaded with help of a dedicated classloader and executed
in any H2O context (e.g., in the context of MRTask or Rapids).

### Design

#### What is function?
In this content, a custom function is a class which implements `water.udf.CFunc` interface.
The interface is used only as a marker, actual functions implements an ancestor of the `CFunc` interface.

#### Storage of functions in K/V
The K/V holds a binary content of a jar file which includes definition of
function(s). The jar file can include multiple functions, it can even
mix functions from different languages - e.g., Java and Python.

#### Referencing a function stored in K/V
The functions are referenced by combination of `language`, `K/V key` and `function name`:
  - The language is used to select function loader which can instantiate a function stored in the jar file.
  - The key is used to reference a jar file stored in K/V store
  - The function name is used to select a function stored in the jar file.

#### Access to functions stored in K/V
The access to jar file stored in K/V is provided via a dedicated classloader implemented
in the `water.udf.DkvClassLoader` which inherits from standard `URLClassLoader`.
Current implementation dumps content of K/V referenced by a given key into a file and
use `URLClassLoader#addURL` method to append another URL to load from.

#### Creation of functions
Given the access a jar file stored in K/V, the next step is to load and
instantiate a function. This is driven by `water.udf.CFuncLoaderService` which
finds for given language (e.g., `Java`) instance of `water.udf.CFuncLoader`.
The loader provides an interface to instantiate a given method:

```java
public abstract <F> F load(String jfuncName, Class<? extends F> targetKlazz, ClassLoader classLoader);
```

The loaders `water.udf.CFuncLoader` are registered via Java Service Provider Interface.

### Custom metrics
The custom metrics is a function which implements `water.udf.CMetricFunc` interface.
The interface follows design of `hex.MetricBuilder` and contains three methods to support
distributed invocation:
  - `map` : the method which maps a row into array of doubles. The method is designed to be called as
  part of `water.MRTask#map` call and it corresponds to  `hex.MetricBuilder#map` call.
  - `reduce` : the method combines 2 row results. It is called as part of `water.MRTask#map` and `water.MRTask#reduce` calls.
  - `metric` : the method computes the final metric value from given array of doubles. The method
  is called in the context of `water.MRtask#postGlobal` and corresponds to `hex.MetricBuilder#postGlobal` call.

### Custom distributions

The custom distribution is a function which implements `water.udf.CDistributionFunc` interface.
The interface follows the design of `hex.Distribution` and contains four methods to support
distributed invocation:
  - `link` : the method returns type of link function transformation of the probability of response variable to a continuous scale that is unbounded.
  The method is designed to be called where `hex.Distribution#link` and `hex.Distribution#linkInv` methods are used.
  It can return `identity` by default (Identity Link Function).
  - `init` : the method combines weight, actual response and offset to compute numerator and denominator of the initial value.
  It can return `[ weight * (response - offset), weight]` by default.
  The method is designed to be called where `hex.Distribution#initFNum` and `hex.Distribution#initFDenom` methods are used.
  - `gamma` : the method combines weight, actual response, residual and predicted response to compute numerator 
  and denominator of size of step in terminal node estimate - gamma (The Elements of Statistical Learning II:, page 387.  Step 2b iii.).
  The method is designed to be called where `hex.Distribution#gammaNum` and `hex.Distribution#gammaDenom` methods are used.
  - `gradient` : the method computes (Negative half) Gradient of deviance function at predicted value for actual response
   in one GBM learning step (The Elements of Statistical Learning II, page 387, Steps 2a, 2b). 
   The method is designed to be called where `hex.Distribution#negHalfGradient` method is used.
  
### Other design alternatives
  - Translation into Rapids
    - Advantages:
      - Rapids is a common backend representation for client code
    - Problems:
      - limited expressiveness of Rapids (cannot specify custom row/reduce transformations easily)
      - missing transpiler from Python into Rapids (we have limited transpiler for Lambdas)

## Python Client
The idea is to pass definition of custom metric or custom distribution directly from Python in the form of Python code.
That needs:
  - an API which represents a custom metric function at caller side
  - backend support to interpret Python code

### Public API

#### Custom Metric Function
The custom metric function is defined in Python as a class which provides
3 methods following the semantics of Java API above:
  - `map`
  - `reduce`
  - `metric`

For example, custom RMSE model metric:

```python
class CustomRmseFunc:
    def map(self, pred, act, weight, offset, model):
        idx = int(act[0])
        err = 1 - pred[idx + 1] if idx + 1 < len(pred) else 1
        return [err * err, 1]

    def reduce(self, l, r):
        return [l[0] + r[0], l[1] + r[1]]

    def metric(self, l):
        # Use Java API directly
        import java.lang.Math as math
        return math.sqrt(l[0] / l[1])
```

> Note: please mention, that code above is also referencing a java class `java.lang.Math`

##### Publishing custom metric function in cluster
The client local custom function represented as a class can be uploaded into running
H2O cluster by calling method `h2o.upload_custom_metric(klazz, func_name, func_file)`:
  - `klazz` represent custom function as described above
  - `func_name` assigns a name with uploaded custom functions, the name corresponds to name of key in K/V
  - `func_file` name of file to store function in uploaded jar. The source code of given class is saved into a file,
  the file is zipped, and uploaded as zip-archive, and saved into K/V store.

The call returns a reference to uploaded custom metric function. The internal form of reference
is constructed based on passed parameters and follows the following structure: `<language>:<func_name>:<func_file>.<klazz-name>Wrapper`.

> Note: The parameters `func_name` and `func_file` need to be unique for each uploaded custom metric!

For example:
```python
custom_mm_func = h2o.upload_custom_metric(CustomRmseFunc, func_name="rmse", func_file="mm_rmse.py")
```

returns a function reference which has the following value:

```
> print(custom_mm_func)
python:rmse=mm_rmse.CustomRmseFuncWrapper
```

##### Using custom model metric functions
An algorithm model builder interface can expose parameter `custom_metric_func`
which accepts a reference to uploaded custom metric function:

```python
model = H2OGradientBoostingEstimator(ntrees=3, max_depth=5,
                  score_each_iteration=True,
                  custom_metric_func=custom_mm_func)
model.train(y="AGE", x=ftrain.names, training_frame=ftrain, validation_frame=fvalid)
```

> Note: Currently, only GBM and RandomForest expose the parameter.

The computed custom model metric is part of model metric object and available
via methods `custom_metric_name()` and `custom_metric_value()`.

#### Custom Distribution Function

The custom distribution function is defined in Python as a class which provides
four methods following the semantics of Java API above:
  - `link`
  - `init`
  - `gamma`
  - `gradient`
  
For example, custom Gaussian distribution:

```python
class CustomDistributionGaussian:

    def link(self):
        return "identity"

    def init(self, w, o, y):
        return [w * (y - o), w]
        
    def gamma(self, w, y, z, f):
        return [w * z, w]
    
    def gradient(self, y, f):
        return y - f
```

Or custom Multinomial distribution:
   
   ```python
class CustomDistributionMultinomial:
   
    def link(self):
        return "log"

    def init(self, w, o, y):
        return [w * (y - o), w]
        
    def gradient(self, y, f, l):
        return 1 - f if y == l else 0 - f

    def gamma(self, w, y, z, f):
        import java.lang.Math as math
        absz = math.abs(z)
        return [w * z, w * (absz * (1 - absz))]
   ```

##### Publishing custom distribution function in cluster
The client local custom function represented as a class can be uploaded into running
H2O cluster by calling method `h2o.upload_custom_distribution(klazz, func_name, func_file)`:
  - `klazz` represent custom function as described above
  - `func_name` assigns a name with uploaded custom functions, the name corresponds to name of key in K/V
  - `func_file` name of file to store function in uploaded jar. The source code of given class is saved into a file,
  the file is zipped, and uploaded as zip-archive, and saved into K/V store.

The call returns a reference to uploaded custom distribution function. The internal form of reference
is constructed based on passed parameters and follows the following structure: `<language>:<func_name>:<func_file>.<klazz-name>Wrapper`.

> Note: The parameters `func_name` and `func_file` need to be unique for each uploaded custom distribution!

For example:
```python
from h2o.utils.distributions import CustomDistributionGaussian
custom_dist_func = h2o.upload_custom_distribution(CustomDistributionGaussian, func_name="gaussian", func_file="dist_gaussian.py")
```

returns a function reference which has the following value:

```
> print(custom_dist_func)
python:gaussian=dist_gaussian.CustomDistributionGaussianWrapper
```

##### Using custom distribution functions
An algorithm model builder interface can expose parameter `custom_distribution_func`
which accepts a reference to uploaded custom distribution function:

```python
model = H2OGradientBoostingEstimator(ntrees=3, max_depth=5,
                  score_each_iteration=True,
                  custom_distribution_func=custom_dist_func)
model.train(y="AGE", x=ftrain.names, training_frame=ftrain, validation_frame=fvalid)
```

> Note: Currently, only GBM expose the parameter.
