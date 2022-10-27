H2O-3 Download Page
===================

This directory contains the template and the metadata files used to generate the H2O-3 download page.

## Files

* `index.html`: The main template file.
* `buildinfo.json`: Metadata describing the build (version, branch name, ..). This is also a template (see strings prefixed with "SUBST_").

## Local Development

In order to make changes and develop the download page locally, you need to have a valid `buildinfo.json` file. The repository
version is not a valid JSON file as-is - the placeholders need to be substituted for valid values. One way to get a valid
`buildinfo.json` file is to download the latest from the actual website, make the substitutions manually or use provided
convenience script (this is the easiest way).

Script `local_dev.py` will start a local webserver on port 8080 and make necessary substitutions in `buildinfo.json`
template dynamically.

Run it using Python 3.x:

```
python3 local_dev.py
```

Hit `Ctrl-C` to terminate the script.
