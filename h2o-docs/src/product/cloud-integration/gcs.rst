Using H2O-3 with Google Cloud Storage
=====================================

To use the Google Cloud Storage solution, you must provide your GCS credentials to H2O-3. This lets you access your data on GCS when importing data frames with path prefixes ``gs://...``.

H2O-3 uses `google-auth-library-java <https://github.com/google/google-auth-library-java>`__ to authenticate requests. It supports a wide range of authentication types.

The following are searched (in order) to find the Application Default Credentials:

- Credentials file pointed to by the ``GOOGLE_APPLICATION_CREDENTIALS`` environment variable.
- Credentials provided by the Google Cloud SDK ``gcloud auth application-default login`` command.
- Google App Engine built-in credentials.
- Google Cloud Shell built-in credentials.
- Google Compute Engine built-in credentials.
