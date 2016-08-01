# -*- encoding: utf-8 -*-
from setuptools import setup, find_packages
from codecs import open
from os import path
import h2o

here = path.abspath(path.dirname(__file__))

# Get the long description from the relevant file
with open(path.join(here, 'DESCRIPTION.rst'), encoding='utf-8') as f:
    long_description = f.read()

packages = find_packages(exclude=["tests*"])
print("Found packages: %r" % packages)

setup(
    name='h2o',

    # Versions should comply with PEP440.  For a discussion on single-sourcing
    # the version across setup.py and the project code, see
    # https://packaging.python.org/en/latest/single_source_version.html
    version=h2o.__version__,

    description='H2O, Fast Scalable Machine Learning, for python ',
    long_description=long_description,

    # The project's main homepage.
    url='https://github.com/h2oai/h2o-3.git',

    # Author details
    author='H2O.ai',
    author_email='support@h2o.ai',

    # Choose your license
    license='Apache v2',

    # See https://pypi.python.org/pypi?%3Aaction=list_classifiers
    classifiers=[
        # How mature is this project? Common values are
        #   3 - Alpha
        #   4 - Beta
        #   5 - Production/Stable
        "Development Status :: 5 - Production/Stable",

        # Indicate who your project is intended for
        "Intended Audience :: Education",
        "Intended Audience :: Developers",
        "Intended Audience :: Science/Research",
        "Intended Audience :: Customer Service",
        "Intended Audience :: Financial and Insurance Industry",
        "Intended Audience :: Healthcare Industry",
        "Intended Audience :: Telecommunications Industry",
        "Topic :: Scientific/Engineering :: Artificial Intelligence",
        "Topic :: Scientific/Engineering :: Information Analysis",

        # Pick your license as you wish (should match "license" above)
        "License :: OSI Approved :: Apache Software License",

        # Specify the Python versions you support here. In particular, ensure
        # that you indicate whether you support Python 2, Python 3 or both.
        "Programming Language :: Python :: 2.7",
        "Programming Language :: Python :: 3.5",
    ],

    keywords='machine learning, data mining, statistical analysis, modeling, big data, distributed, parallel',

    packages=packages,
    package_data={"h2o": [
        "LICENSE",
        "h2o_data/*.*",
    ]},

    # run-time dependencies
    install_requires=["requests", "tabulate", "future", "colorama"],

    # Additional data files to include into the distribution
    data_files=[
        ("h2o_jar", ["../build/h2o.jar"]),
    ],
)
