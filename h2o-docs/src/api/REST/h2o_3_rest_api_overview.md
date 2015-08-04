# H2O 3 REST API Overview
The H2O REST API allows you to access all the facilities of H2O from an external program or script, via JSON over HTTP.  

It is used by the Flow UI as well as both the R and Python bindings: everything that you can do with those clients can be done by using the REST API, including data import, model building and generating predictions.

You can test and play with the REST API with your browser or using browser tools such as PostMan in Chrome, using curl, or using the language of your choice.  Generated payload POJOs for Java are available as part of the release in a separate bindings Jar file, and are simple to generate for other langauges if desired.

## Reference Documentation
Reference documentation for the REST API is available within the help pane in Flow, as well as on the H2O.ai website, [http://docs.h2o.ai/](http://docs.h2o.ai/).  The reference documentation is all generated from the H2O server via the Metadata facilities described below so that it is always up to date.

## Versioning and Stability
Both the endpoints and the payloads for the REST API are versioned for stability; the current stable version for them is 3.  Versions will be supported for some time after a new major version is released to allow you time to upgrade your clients.

In general you will want to write to a specific version, such as 3, and upgrade shortly after a new major version is released.  Once we release a new major version of the REST API most new features will be added only to the new version.

### Non-breaking changes

We continue to add features to the APIs, but we only allow *non-breaking changes* in a published API such as version 3.  Breaking changes force a new major version number.

A non-breaking change is one which will not change the behavior of a well-written client.  One example is adding a model parameter with a default value which maintains the old behavior if the parameter is omitted.  Another is adding additional output fields to a response.  We test backward compatibility by running a full set of tests against each new release (including nightlies) using old releases of the Flow, R and Python clients.

### The EXPERIMENTAL version

Features which are under development and are not yet stable use version 99, indicating that they may change between releases.  Once those features become stable we change the version from 99 to the current stable version.

For request URLs you may use EXPERIMENTAL as the version number to make it clear in your client code that you are making requests to a moving target:

`GET http://127.0.0.1:54321/EXPERIMENTAL/Sample`

## URLs

Your H2O cluster is typically referenced by the host name and HTTP port of the first server in the cluster.  By default this is *http://localhost:54321* (or *https://localhost:54321*, if you have an enterprise license).  Append the endpoint request URI to this to form your request URL.

H2O REST API URIs begin with a version followed by a resource type, such as */3/Frames* or */3/Models* or */3/Cloud*.  Typically a GET to this kind of resource collection URI will return all the instances of the resource type.

All endpoints that deal with a resource type will begin with the same prefix.  As an example, *GET /3/Frames* returns the list of all *Frames*, while *GET /3/Frames/my_frame* returns the *Frame*  named *my_frame*.

## HTTP Verbs
As is standard for REST APIs, the HTTP verbs GET, HEAD, POST and DELETE are used to interact with the resources in the server.

**GET** requests fetch data and do not cause side effects.  All parameters for the request are contained within the URL, either within the path (e.g., /3/Frames/*my_frame_name*/*a_column_name*) or as query parameters (e.g., /3/Frames/my_frame_name*?row_offset=10000&row_count=1000*)

**HEAD** requests return just the HTTP status for accessing the resource.

**POST** requests create a new object within the H2O cluster.  Examples are importing or parsing a file into a Frame or training a new Model.  Some parameters may be given in the URL, but most are given using a request *schema*.  The fields of the request schema are sent in the POST body using *x-www-form-urlencoded* format, like an HTML form.

A future version of H2O will move to using *application/json*.

**DELETE** requests delete an object, generally from the distributed object store.

**PUT**, intended for requiests which modify objects, is not yet used.

## HTTP Status Codes
H2O uses standard HTTP status codes for all it's responses.  See [Wikipedia](https://en.wikipedia.org/wiki/List_of_HTTP_status_codes) for a reference on their meanings.

The status codes currently used by H2O are:

* **200 OK** (all is well)
* **400 Bad Request** (the request URL is bad)
* **404 Not Found** (a specified object was not found)
* **412 Precondition Failed** (bad parameters or other problem handling the request)
* **500 Internal Server Error** (unanticipated failure occurred in the server)

## Formats
The payloads for each endpoint are implemented as versioned *schema*s.  These schemas are self-describing to make it simpler and more robust to consume them, especially if you persist them for later.

### Schemas

Schemas specify all the relevant properties of each field of an input or response including name, type, default value, help string, direction (*in, out* or *inout*), whether or not input fields are required and how important they are to specify, allowed values for enumerated fields, and so on.  Schemas fields can be simple values or nested schemas, or arrays or dictionaries (maps) of these.

This example shows the *model_id* field returned by a model builder call:

            "parameters": [
                {
                    "__meta": {
                        "schema_name": "ModelParameterSchemaV3",
                        "schema_type": "Iced",
                        "schema_version": 3
                    },
                    "actual_value": {
                        "URL": "/3/Models/prostate_glm",
                        "__meta": {
                            "schema_name": "ModelKeyV3",
                            "schema_type": "Key<Model>",
                            "schema_version": 3
                        },
                        "name": "prostate_glm",
                        "type": "Key<Model>"
                    },
                    "default_value": null,
                    "help": "Destination id for this model; auto-generated if not specified",
                    "label": "model_id",
                    "level": "critical",
                    "name": "model_id",
                    "required": false,
                    "type": "Key<Model>",
                    "values": []
                },
                ...
            ],
            ...
### POST bodies
The fields of the request schema are sent in the POST body using *x-www-form-urlencoded* format, like an HTML form.  A future version of H2O will move to using *application/json*.  In the meantime, complex fields such as arrays are POSTed in the same format they would be in the JSON, for example an array of ints might be posted in a field as [1, 10, 100].  Note the array of strings for the *ignored_columns* parameter in this GLM model builder POST body:

    model_id=prostate_glm&training_frame=prostate.hex&nfolds=0&response_column=CAPSULE&ignored_columns=%5B%22%22%5D&ignore_const_cols=true&family=binomial&solver=AUTO&alpha=&lambda=&lambda_search=false&standardize=true&non_negative=false&score_each_iteration=false&max_iterations=-1&link=family_default&intercept=true&objective_epsilon=0.00001&beta_epsilon=0.0001&gradient_epsilon=0.0001&prior=-1&max_active_predictors=-1

The value is ["ID"], urlencoded as %5B%22ID%22%5D.

### Metadata
The formats of all payloads (*schemas*) are available dynamically from the server using the */Metadata/schemas* endpoints. You can fetch additional metadata for model builder (model algorithm) parameters from the */ModelBulders* endpoints.  This metadata allows you to write a client which automatically adapts to new fields.  

As an example, Flow has no hardwired knowledge of any of the model algos.  It discovers the list of algos and all their parameter information dynamically.  This means that if you extend H2O with new algorithms or new fields for the built-in algorithms Flow will Just Work (tm).

Similarly, all the endpoints (URL patterns) are described dynamically by the */Metadata/endpoints* endpoints.

## Error Condition Payloads
All errors return one of the non-2xx HTTP status codes mentioned above, and return standardized error payloads.  These contain an end-user-directed message, a developer-oriented message, the HTTP status, an optional dictionary of revelant values, and exception information if applicable.

Here is the result of requesting a Frame which is not present in the server: 

`GET http://127.0.0.1:54321/3/Frames/missing_frame`

            {
                "__meta": {
                    "schema_version": 3,
                    "schema_name": "H2OErrorV3",
                    "schema_type": "H2OError"
                },
                "timestamp": 1438634936808,
                "error_url": "/3/Frames/missing_frame",
                "msg": "Object 'missing_frame' not found for argument: key",
                "dev_msg": "Object 'missing_frame' not found for argument: key",
                "http_status": 404,
                "values": {
                    "argument": "key",
                    "name": "missing_frame"
                },
                "exception_type": "water.exceptions.H2OKeyNotFoundArgumentException",
                "exception_msg": "Object 'missing_frame' not found for argument: key",
                "stacktrace": [
                    "water.api.FramesHandler.getFromDKV(FramesHandler.java:154)",
                    "water.api.FramesHandler.doFetch(FramesHandler.java:239)",
                    "water.api.FramesHandler.fetch(FramesHandler.java:225)",
                    ...

## Control query parameters
H2O also supports "meta" query parameters to control the result payload.  Currently the only one of these is *exclude_fields*, but more will be supported in subsequent releases.

### exclude_fields
The result payload of some calls can get quite large.  For example, a Frame or a Model built with a Frame that has 5,000 categorical columns may have a very large list of *domains*, or categorical levels.  

If you don't require that the server return certain fields you can use the *exclude_fields* query parameter to ask that they be excluded.  This reduces the size of the result, sometimes considerably, which speeds up JSON parsing in the client and can reduce the chance that limited memory clients such as web browsers run out of memory processing the result.

The *exclude_fields* parameter takes a comma-separated list of field names.  Nested field names are separated by slashes.

As an example, one call of Flow to /Frames/{frame_id} uses:

    exclude_fields=frames/vec_ids,frames/columns/data,frames/columns/domain,frames/columns/histogram_bins,frames/columns/percentiles

## Example Endpoints
This section lists a few endpoints to give you an idea of the functions that are available through the REST API.  The reference documentation contains the full list.

Remember, Flow and the R and Python bindings access H2O only through the REST API, so if you find functionality in those clients you'll find it in th REST API as well.  The only caveat is data munging (e.g., slicing, creating new columns, etc).  That functionality is available through the /99/Rapids endpoint, which is under rapid change.  Contact us if you need to access those functions through the REST API.

### Loading and parsing data files
    GET /3/ImportFiles
    Import raw data files into a single-column H2O Frame.

    POST /3/ParseSetup
    Guess the parameters for parsing raw byte-oriented data into an H2O Frame.

    POST /3/Parse
    Parse a raw byte-oriented Frame into a useful columnar data Frame.

### Frames

    GET /3/Frames
    Return all Frames in the H2O distributed K/V store.
    
    GET /3/Frames/(?.*)
    Return the specified Frame.

    GET /3/Frames/(?.*)/summary
    Return a Frame, including the histograms, after forcing computation of rollups.

    GET /3/Frames/(?.*)/columns/(?.*)/summary
    Return the summary metrics for a column, e.g. mins, maxes, mean, sigma, percentiles, etc.

    DELETE /3/Frames/(?.*)
    Delete the specified Frame from the H2O distributed K/V store.

    DELETE /3/Frames
    Delete all Frames from the H2O distributed K/V store.

### Building models

    GET /3/ModelBuilders
    Return the Model Builder metadata for all available algorithms.

    GET /3/ModelBuilders/(?.*)
    Return the Model Builder metadata for the specified algorithm.

    POST /3/ModelBuilders/deeplearning/parameters
    Validate a set of Deep Learning model builder parameters.

    POST /3/ModelBuilders/deeplearning
    Train a Deep Learning model on the specified Frame.

    POST /3/ModelBuilders/glm/parameters
    Validate a set of GLM model builder parameters.

    POST /3/ModelBuilders/glm
    Train a GLM model on the specified Frame.

    ...
### Accessing and using models

    GET /3/Models
    Return all Models from the H2O distributed K/V store.

    GET /3/Models/(?.*?)(\.java)?
    Return the specified Model from the H2O distributed K/V store, optionally with the list of compatible Frames.  Using the .java extension will return the Java POJO.

    POST /3/Predictions/models/(?.*)/frames/(?.*)
    Score (generate predictions) for the specified Frame with the specified Model. Both the Frame of predictions and the metrics will be returned.

    DELETE /3/Models/(?.*)
    Delete the specified Model from the H2O distributed K/V store.

    DELETE /3/Models
    Delete all Models from the H2O distributed K/V store.

### Administrative and utility

    GET /3/About
    Return information about this H2O cluster.

    GET /3/Cloud
    Determine the status of the nodes in the H2O cloud.

    HEAD /3/Cloud
    Determine the status of the nodes in the H2O cloud.


### Job management and polling

    GET /3/Jobs
    Get a list of all the H2O Jobs (long-running actions).
    
    GET /3/Jobs/(?.*)
    Get the status of the given H2O Job (long-running action).

    POST /3/Jobs/(?.*)/cancel
    Cancel a running job.

## Example Requests
f00

