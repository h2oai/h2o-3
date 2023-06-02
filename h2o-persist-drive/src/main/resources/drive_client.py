import boto3
import itertools


def decode_path(path, fail_on_bucket_only=True):
    if not path.startswith("drive://"):
        raise ValueError("Path is not a Drive path (path='%s')" % path)
    parts = path.split("/", maxsplit=3)
    if len(parts) != 4:
        if fail_on_bucket_only:
            raise ValueError("Path needs to include both bucket name and object key (path='%s')" % path)
        else:
            return parts[2], None
    return parts[2], parts[3]


class DriveClient:
    """
    Example implementation of S3-like persistence client
    """

    def __init__(self):
        pass

    def supports_presigned_urls(self):
        return True

    def download_file(self, path, file):
        s3 = boto3.client('s3')
        bucket, objectKey = decode_path(path)
        s3.download_file(self, bucket, objectKey, file)

    def generate_presigned_url(self, path):
        s3 = boto3.client('s3')
        bucket, objectKey = decode_path(path)
        response = s3.generate_presigned_url('get_object',
                                             Params={'Bucket': bucket,
                                                     'Key': objectKey},
                                             ExpiresIn=3600)
        return response

    def calc_typeahead_matches(self, partial_path, limit):
        bucket, objectKeyPrefix = decode_path(partial_path, fail_on_bucket_only=False)
        if objectKeyPrefix is None:
            return []
        s3 = boto3.client('s3')
        contents = s3.list_objects(Bucket=bucket, Prefix=objectKeyPrefix)['Contents']
        keys = map(lambda it: "drive://" + bucket + "/" + it["Key"], contents)
        return list(itertools.islice(keys, limit))
