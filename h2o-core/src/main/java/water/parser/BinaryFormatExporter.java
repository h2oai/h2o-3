package water.parser;

import water.H2O;
import water.fvec.Frame;
import water.util.ExportFileFormat;

public interface BinaryFormatExporter {

    H2O.H2OCountedCompleter export(Frame frame, String path, boolean force, String compression, boolean writeChecksum, boolean tzAdjustFromLocal);

    boolean supports(ExportFileFormat format);
}
