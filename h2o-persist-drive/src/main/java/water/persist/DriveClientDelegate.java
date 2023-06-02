package water.persist;

/**
 * This is the main interface for talking to the underlying python implementation.
 * It is Pythonic to keep things nice on the Python side.
 */
public interface DriveClientDelegate {

    boolean supports_presigned_urls(DriveClientDelegate self);

    void download_file(DriveClientDelegate self, String path, String file);

    String generate_presigned_url(DriveClientDelegate self, String path);

    String[] calc_typeahead_matches(DriveClientDelegate self, String partial_path, int limit);

}
