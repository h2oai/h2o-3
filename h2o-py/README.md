# Using H2O from Python

## Prerequisites:

  - Python 2.7 or 3.5
  - Numpy 1.9.2 or greater

This module depends on **requests**, **tabulate**, and **scikit-learn** modules, all of which are available on pypi:

    $ pip install requests
    $ pip install tabulate
    $ pip install scikit-learn

## Downloading and Installing

You can always download the latest stable version of the **h2o** Python package from the following page: [http://h2o-release.s3.amazonaws.com/h2o/latest_stable.html](http://h2o-release.s3.amazonaws.com/h2o/latest_stable.html) 

Review the installation instructions on the download page. 

Alternatively, you can build the h2o Python package from source (see below).

## Building it yourself

The Python package is built as part of the normal build process.

Clone the [h2o-3 repository](https://github.com/h2oai/h2o-3) on GitHub. 

In the top-level h2o-3 directory, use `$ ./gradlew build`.

To build the Python component by itself, first type `$ cd h2o-py`, and then type `$ ../gradlew build`.

## Documentation/References

- [Python Module Documentation](http://docs.h2o.ai/h2o/latest-stable/h2o-py/docs/intro.html)
- [Python Booklet](<http://docs.h2o.ai/h2o/latest-stable/h2o-docs/booklets/PythonBooklet.pdf>)
- [Python FAQ](http://docs.h2o.ai/h2o/latest-stable/h2o-docs/faq.html#python>)
- [YouTube video - Quick Start with Python](https://www.youtube.com/watch?list=PLNtMya54qvOHbBdA1x8FNRSpMBEHmhxr0&v=K8J3dPBEz1s>)

