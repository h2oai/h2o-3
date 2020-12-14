package water.api;

import water.util.ArrayUtils;

import java.io.OutputStream;

public class DelegatingStreamWriter implements StreamWriter {

    StreamWriter _streamWriter;
    StreamWriteOption[] _options;

    private DelegatingStreamWriter(StreamWriter streamWriter, StreamWriteOption[] options) {
        _streamWriter = streamWriter;
        _options = options;
    }

    @Override
    public void writeTo(OutputStream os, StreamWriteOption... options) {
        _streamWriter.writeTo(os, ArrayUtils.append(_options, options));
    }

    public static StreamWriter wrapWithOptions(StreamWriter streamWriter, StreamWriteOption[] options) {
        if (options == null || options.length == 0)
            return streamWriter;
        else
            return new DelegatingStreamWriter(streamWriter, options);
    }
}
