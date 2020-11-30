package hex.genmodel;

import java.io.BufferedReader;
import java.io.IOException;

class NestedMojoReaderBackend implements MojoReaderBackend {

    private MojoReaderBackend _reader;
    private String _zipDirectory;

    NestedMojoReaderBackend(MojoReaderBackend parent, String zipDirectory) {
        _reader = parent;
        _zipDirectory = zipDirectory;
    }

    @Override
    public BufferedReader getTextFile(String filename) throws IOException {
        return _reader.getTextFile(_zipDirectory + filename);
    }

    @Override
    public byte[] getBinaryFile(String filename) throws IOException {
        return _reader.getBinaryFile(_zipDirectory + filename);
    }

    @Override
    public boolean exists(String filename) {
        return _reader.exists(_zipDirectory + filename);
    }
}
