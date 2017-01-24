# h2o-docs

---
### src/front  

The front page that appears at:

> docs-website/index.html

---

### src/product  

The main product documentation, built by [Sphinx](http://www.sphinx-doc.org/).

To install Sphinx:

```
pip install recommonmark
pip install sphinx_rtd_theme
pip install sphinxcontrib-osexample
pip install sphinx
```
To build the main product documentation:

```
cd src/product
make html
```
The output goes to:

> docs-website/h2o-docs/index.html

There are also some directories under product from the original h2o-3 pre-sphinx docs build system.


---
### src/booklets/v2_2015

The various booklet latex source code, built by the gradle 'booklets' target.  The output goes to:

> docs-website/h2o-docs/booklets/

---
