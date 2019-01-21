import h2o

def set_s3_credentials(secret_key_id, secret_access_key):
    """Creates a new Amazon S3 client internally with specified credentials.
    There are no validations done to the credentials. Incorrect credentials are thus revealed with first S3 import call.
    
    secretKeyId Amazon S3 Secret Key ID (provided by Amazon)
    secretAccessKey Amazon S3 Secret Access Key (provided by Amazon)
    """
    params = {"secret_key_id": secret_key_id,
              "secret_access_key": secret_access_key
              }
    
    h2o.api(endpoint="GET /3/Persist", data=params)
