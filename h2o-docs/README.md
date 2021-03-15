# h2o-docs

## src/product  

This folder includes the main product documentation. The documentation is built using [Sphinx](http://www.sphinx-doc.org/) with the [Read The Docs theme](https://sphinx-rtd-theme.readthedocs.io/en/stable/).

Note that there are also some directories under the **/product** folder from the original h2o-3 pre-sphinx docs build system.

### Requirements

- Python 3.5+
- [Sphinx](http://www.sphinx-doc.org/) 
- [Read The Docs theme](https://sphinx-rtd-theme.readthedocs.io/en/stable/)
- Additional extensions
  - [recommonmark](https://recommonmark.readthedocs.io/en/latest/)
  - [sphinx-prompt](https://pypi.org/project/sphinx-prompt/) version 1.1.0
  - [sphinx-tabs](https://pypi.org/project/sphinx-tabs/1.1.12/) version 1.1.12
  - [sphinx-substitutions-extension](https://pypi.org/project/Sphinx-Substitution-Extensions/2019.6.15.0/) version 2019.6.15.0

Run the following to install Sphinx and the RTD theme. 

```
python3 -m pip install sphinx==2.1.1
python3 -m pip install sphinx_rtd_theme==0.2.4
```

Run the following to install the additional required extensions:

```
python3 -m pip install recommonmark
python3 -m pip install sphinx_prompt==1.1.0
python3 -m pip install sphinx-tabs==1.1.12
python3 -m pip install sphinx_substitution_extensions==2019.6.15.0
```

The makefile for building the docs is in the **/src/product** folder. Run the following to build the H2O-3 User Guide.

```
cd src/product
make html
```

The output will be available in:

> src/product/_build/html/index.html

## src/booklets/v2_2015

This folder contains latex source code for H2O-3 booklets. The booklets can be built from the **/h2o-3** folder.

```
cd ..
./gradlew booklets
```

The output PDFs are available in:

> src/booklets/v2_2015/source

## src/api

This folder provides an overview of the H2O-3 REST API and includes an example. The REST API docs are available on the [docs site](https://docs.h2o.ai).

## src/cheatsheets

This folder includes parity information between R and Python functions and a conversion table for H2ODataFrame to Pandas DataFrame. 

## src/dev

This folder contains information about the H2OApp and about custom functions.

## src/front

This folder is deprecated and is no longer maintained. It will be removed at a later date.
