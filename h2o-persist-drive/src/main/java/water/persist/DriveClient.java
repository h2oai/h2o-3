package water.persist;

/**
 * This class is here to wrap around the actual Polyglot proxy object and delegate
 * all calls directly to it. The only reason this class exists is that we want to
 * keep things nice on the python side and have `self` reference and also respect
 * naming conventions in both worlds.
 */
public final class DriveClient {
    private final DriveClientDelegate _delegate;

    DriveClient(DriveClientDelegate delegate) {
        _delegate = delegate;
    }

    /**
     * Does the underlying implementation generate a presigned-URL to access
     * the data?
     * @return true, if pre-signed URLs can be generated
     */
    boolean supportsPresignedUrls() {
        return _delegate.supports_presigned_urls(_delegate);
    }

    /**
     * Download dataset located at given path to a local file.
     * @param path dataset location
     * @param file local file path
     */
    void downloadFile(String path, String file) {
        _delegate.download_file(_delegate, path, file);
    }

    /**
     * For a given path generate a pre-signed URL to access it by
     * HTTP.
     * @param path path specification
     * @return HTTP/HTTPS URL
     */
    String generatePresignedUrls(String path) {
        return _delegate.generate_presigned_url(_delegate, path);
    }

    /**
     * For auto-complete suggestions in Flow: take a given path and suggest
     * a list of path that could complete the path.
     * @param path Path prefix or a path to a directory.
     * @param limit maximum number of returned suggestions
     * @return array of suggested paths
     */
    String[] calcTypeaheadMatches(String path, int limit) {
        return _delegate.calc_typeahead_matches(_delegate, path, limit);
    }

}
