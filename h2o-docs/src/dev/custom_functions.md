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
  - `perRow` : the method which maps a row into array of doubles. The method is designed to be called as
  part of `water.MRTask#map` call and it corresponds to  `hex.MetricBuilder#perRow` call.
  - `combine` : the method combines 2 row results. It is called as part of `water.MRTask#map` and `water.MRTask#reduce` calls.
  - `metric` : the method computes the final metric value from given array of doubles. The method
  is called in the context of `water.MRtask#postGlobal` and corresponds to `hex.MetricBuilder#postGlobal` call.

### Other design alternatives
  - Translation into Rapids
    - Advantages:
      - Rapids is a common backend representation for client code
    - Problems:
      - limited expressiveness of Rapids (cannot specify custom row/reduce transformations easily)
      - missing transpiler from Python into Rapids (we have limited transpiler for Lambdas)

## Python Client
TBD

### Public API
TBD

# todo
  - tests in python
  - dkv should use proper url classloader
  - remove uneccessary parts
  - remove Jython factory
  - jython run tests via testMultiNode


### Example: user defined metrics
TBD

### Example: library of metrics
TBD

