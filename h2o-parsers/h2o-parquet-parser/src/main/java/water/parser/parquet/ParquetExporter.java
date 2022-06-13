package water.parser.parquet;

import water.H2O;
import water.fvec.Frame;
import water.parser.BinaryFormatExporter;
import water.util.ExportFileFormat;

public class ParquetExporter implements BinaryFormatExporter {

    @Override
    public H2O.H2OCountedCompleter export(Frame frame, String path, boolean force, String compression) {
        return new ExportParquetDriver(frame, path, force, compression);
    }

    @Override
    public boolean supports(ExportFileFormat format) {
        if (ExportFileFormat.parquet.equals(format)) {
            return true;
        }
        return false;
    }

    private class ExportParquetDriver extends H2O.H2OCountedCompleter<ExportParquetDriver> {

        Frame _frame;
        String _path;
        boolean _force;
        String _compression;

        public ExportParquetDriver(Frame frame, String path, boolean force, String compression) {
            _frame = frame;
            _path = path;
            _force = force;
            _compression = compression;
        }

        @Override
        public void compute2() {
            // multipart export
            FrameParquetExporter parquetExporter = new FrameParquetExporter();
            parquetExporter.export(this, _path, _frame, _force, _compression);
        }
    }
}
