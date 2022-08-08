import h2o
from h2o.exceptions import H2OValueError


def set_s3_credentials(secret_key_id, secret_access_key, session_token = None):
    """Creates a new Amazon S3 client internally with specified credentials.
    There are no validations done to the credentials. Incorrect credentials are thus revealed with first S3 import call.

    :param secret_key_id: Amazon S3 Secret Key ID (provided by Amazon)
    :param secret_access_key: Amazon S3 Secret Access Key (provided by Amazon)
    :param session_token: Amazon Session Token (optional, only when using AWS Temporary Credentials)
    """
    if secret_key_id is None:
        raise H2OValueError("Secret key ID must be specified")

    if secret_access_key is None:
        raise H2OValueError("Secret access key must be specified")
    
    if not secret_key_id:
        raise H2OValueError("Secret key ID must not be empty")
    
    if not secret_access_key:
        raise H2OValueError("Secret access key must not be empty")

    params = {"secret_key_id": secret_key_id,
              "secret_access_key": secret_access_key,
              "session_token": session_token
              }
    h2o.api("POST /3/PersistS3", data=params)
    print("Credentials successfully set.")


def remove_s3_credentials():
    """Removes S3 credentials passed to H2O using method set_s3_credentials. Doesn't affect credentials
    stored in instance variables or any other credentials source.
    """
    params = {"secret_key_id": "delete",
              "secret_access_key": "delete"}
    h2o.api("DELETE /3/PersistS3", data=params)
    print("Credentials successfully removed.")
