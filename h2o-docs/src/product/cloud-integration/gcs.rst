Use H2O with Google Cloud Storage
=================================

To use the Google Cloud Storage solution, you will have to ensure your GCS credentials are provided to H2O. This will allow you to access your data on GCS when importing data frames with path prefixes ``gs://...``.

Under the hood, **https://github.com/google/google-auth-library-java** is used to authenticate requests. It supports a wide range of authentication types.

The following are searched (in order) to find the Application Default Credentials:

  - Credentials file pointed to by the ``GOOGLE_APPLICATION_CREDENTIALS`` environment variable
  - Credentials provided by the Google Cloud SDK ``gcloud auth application-default login`` command
  - Google App Engine built-in credentials
  - Google Cloud Shell built-in credentials
  - Google Compute Engine built-in credentials