Contributing to H2O
============================

H2O is an open source project released under the Apache Software Licence v2.  Open Source projects live by their user and developer communities.  We welcome and encourage your contributions of any kind!

There are many different kinds of people who make use of H2O for their daily work: 

* Data Scientists who use R, Python, Scala, Java or the Flow web interface;
* Application Software Developers who build software to drive H2O from those languages or the REST API;
* Machine Learning and data munging developers, who want to extend the internal capabilities of H2O.

No matter what your skill set or level of engagement is with H2O you can help others by improving the ecosystem of documentation, bug report and feature request tickets, and code.

## Bug Reports and Feature Requests

The single most important contribution that you can make is to report bugs and make feature requests.  The development work on H2O is largely driven by these, so please make your voice heard!  

Bug reports are most helpful if you send us a script which reproduces the problem.

If you're a customer with an Enterprise Support contract you should send these to support@h2o.ai.

If you're an Open Source community member you should send these to one of:

* The h2ostream mailing list, at: [https://groups.google.com/forum/#!forum/h2ostream](https://groups.google.com/forum/#!forum/h2ostream)
* Gitter chat, at [https://gitter.im/h2oai/h2o-3](https://gitter.im/h2oai/h2o-3)

### How to File Bugs and Feature Requests

You can file a bug report or feature request directly on the [GitHub issues](https://github.com/h2oai/h2o-3/issues) page.

Once inside the Github issues page, click the **New issue** button.

 ![create](h2o-docs/src/product/images/issue_create.png)

A form will display allowing you to enter information about the bug or feature request.

## Help and Documentation

You can help others directly and help improve the resources that others read to learn and use H2O by contributing to the formal documentation or the forums.

There are several places that users find information about using H2O:

* Formal documentation, at: [http://docs.h2o.ai/](http://docs.h2o.ai/)
* The h2ostream mailing list, at: [https://groups.google.com/forum/#!forum/h2ostream](https://groups.google.com/forum/#!forum/h2ostream)
* Gitter chat, at [https://gitter.im/h2oai/h2o-3](https://gitter.im/h2oai/h2o-3)
* General community sites like Stack Overflow: [http://stackoverflow.com/search?q=h2o](http://stackoverflow.com/search?q=h2o)
* Individuals' blogs

### Formal Documentation

All of the documentation comes directly from the source tree in GitHub.  To contribute improvements to the formal documentation you may either:

* Send the suggestions or changes to support@h2o.ai, h2ostream or Gitter, or
* Use Git to make the changes yourself and submit them via a pull request (see below for details)

### Forums

Answering questions for other users on h2ostream, Gitter, Stack Overflow and other forums builds the community knowledge base and is a very valuable contribution to H2O.

### Blogs

Some of the most interesting written materials on the use of H2O for real world problems has been published by community members to their personal blogs.  If you've written something about H2O that you think should be more widely known contact us on h2ostream or Gitter and we will help you get the word out.

## Tests and Demos

The H2O code base contains tests and demos written in R, Python, Java, Scala and Flow.  These get run as part of every build of the software, either by `gradlew build` on the development machine, or by Jenkins.  Standalone demos are conformed into xUnit tests as part of the build process.  All tests must succeed before we release a stable build.

If you are able to you should clone the H2O git repository, add your test case(s) there, and submit a pull request (see below).  If not, please send your code to h2ostream, Gitter or support@h2o.ai; see above for the links.

Test directories include:

* user-level tests in R: `h2o-r/tests/`
* user-level tests in Python: `h2o-py/tests/`
* REST API tests in Python: `py/testdir_multi_jvm/`
* platform tests in Java: `h2o-core/src/test/java/`
* algorithm tests in Java: `h2o-algos/src/test/java/`
* Flow tests in saved notebooks: `h2o-docs/src/product/flow/packs`

For Scala tests see the Sparkling Water GitHub repo.

## Contribute Code!

You can contribute R, Python, Java or Scala code for H2O, either for bug fixes or new features.  If you have your own idea about what to work on a good place to begin is to discuss it with us on [Gitter](https://gitter.im/h2oai/h2o-3) so that we can help point you in the right direction.

For ideas about what to work on see the H2O-3 [Github issues](https://github.com/h2oai/h2o-3/issues).

To contribute code, fork the H2O-3 GitHub repo, create a branch for your work and when you're done, create a pull request.  Once a PR has been created, it will trigger the H2O-3 Jenkins test system and should start automatically running tests (this will show up in the comment history on the PR).  Make sure all the tests pass.  A few notes:

* If there's not already a GitHub issue associated with this task, please create one.
* If there is a GitHub issue associated with your changes, choose a branch name that includes that  number.  e.g. `gh-1234_new_pca`
* New code must come with unit tests.  Here are some examples of [runits](https://github.com/h2oai/h2o-3/tree/master/h2o-r/tests), [pyunits](https://github.com/h2oai/h2o-3/tree/master/h2o-py/tests) and [junits](https://github.com/h2oai/h2o-3/tree/master/h2o-algos/src/test/java/hex) to help get you started.
* Use the GitHub number in the PR title.  e.g. "GH-1234: Added new `pca_method` option in the PCA algorithm".
* Write a summary of all changes & additions to the code in the PR description and add a link to the GitHub issue.
