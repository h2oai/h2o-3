package hex.mojopipeline;

import ai.h2o.mojos.runtime.frame.*;
import ai.h2o.mojos.runtime.lic.LicenseException;
import ai.h2o.mojos.runtime.readers.MojoPipelineReaderBackendFactory;
import ai.h2o.mojos.runtime.readers.MojoReaderBackend;
import ai.h2o.mojos.runtime.frame.MojoColumn.Type;
import water.*;
import water.fvec.*;
import water.parser.BufferedString;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Date;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;

public class MojoPipeline extends Iced<MojoPipeline> {

  private ByteVec _mojoData;
  private transient ai.h2o.mojos.runtime.MojoPipeline _mojoPipeline;

  public MojoPipeline(ByteVec mojoData) {
    _mojoData = mojoData;
    _mojoPipeline = readPipeline(_mojoData);
  }

  public Frame transform(Frame f, boolean allowTimestamps) {
    Frame adaptedFrame = adaptFrame(f, allowTimestamps);
    byte[] types = outputTypes();
    return new MojoPipelineTransformer(_mojoData._key).doAll(types, adaptedFrame)
            .outputFrame(null, _mojoPipeline.getOutputMeta().getColumnNames(), null);
  }

  private byte[] outputTypes() {
    MojoFrameMeta outputMeta = _mojoPipeline.getOutputMeta();
    for (Type type : outputMeta.getColumnTypes()) {
      if (! type.isnumeric && type != Type.Bool) {
        throw new UnsupportedOperationException("Output type `" + type.name() + "` is not supported.");
      }
    }
    byte[] types = new byte[outputMeta.size()];
    Arrays.fill(types, Vec.T_NUM);
    return types;
  }

  private Frame adaptFrame(Frame f, boolean allowTimestamps) {
    return adaptFrame(f, _mojoPipeline.getInputMeta(), allowTimestamps);
  }

  private static Frame adaptFrame(Frame f, MojoFrameMeta inputMeta, boolean allowTimestamps) {
    String[] colNames = inputMeta.getColumnNames();
    Frame adaptedFrame = new Frame();
    for (String name : colNames) {
      Vec v = f.vec(name);
      if (v == null) {
        throw new IllegalArgumentException("Input frame is missing a column: " + name);
      }
      if (v.get_type() == Vec.T_BAD || v.get_type() == Vec.T_UUID) {
        throw new UnsupportedOperationException("Columns of type " + v.get_type_str() + " are currently not supported.");
      }
      if (! allowTimestamps && v.get_type() == Vec.T_TIME && inputMeta.getColumnType(name) == Type.Str) {
        throw new IllegalArgumentException("MOJO Pipelines currently do not support datetime columns represented as timestamps. " +
                "Please parse your dataset again and make sure column '" + name + "' is parsed as String instead of Timestamp. " +
                "You can also enable implicit timestamp conversion in your client. Please refer to documentation of the transform function.");
      }
      adaptedFrame.add(name, v);
    }
    return adaptedFrame;
  }

  private static ai.h2o.mojos.runtime.MojoPipeline readPipeline(ByteVec mojoData) {
    try {
      try (InputStream input = mojoData.openStream(null);
           MojoReaderBackend reader = MojoPipelineReaderBackendFactory.createReaderBackend(input)) {
        return ai.h2o.mojos.runtime.MojoPipeline.loadFrom(reader);
      }
    } catch (IOException | LicenseException e) {
      throw new RuntimeException(e);
    }
  }

  private static class MojoPipelineTransformer extends MRTask<MojoPipelineTransformer> {

    private final Key<Vec> _mojoDataKey;
    private transient ai.h2o.mojos.runtime.MojoPipeline _pipeline;

    private MojoPipelineTransformer(Key<Vec> mojoDataKey) {
      _mojoDataKey = mojoDataKey;
    }

    @Override
    protected void setupLocal() {
      ByteVec mojoData = DKV.getGet(_mojoDataKey);
      _pipeline = readPipeline(mojoData);
    }

    @Override
    public void map(Chunk[] cs, NewChunk[] ncs) {
      assert cs.length == _pipeline.getInputMeta().size();
      MojoFrameBuilder frameBuilder = _pipeline.getInputFrameBuilder();
      MojoRowBuilder rowBuilder = frameBuilder.getMojoRowBuilder();

      MojoChunkConverter[] conv = new MojoChunkConverter[cs.length];
      for (int col = 0; col < cs.length; col++) {
        final Type type = _pipeline.getInputMeta().getColumnType(col);
        conv[col] = makeConverter(cs[col], col, type);
      }

      // Convert chunks to a MojoFrame
      for (int i = 0; i < cs[0]._len; i++) {
        for (int col = 0; col < cs.length; col++) {
          Chunk c = cs[col];
          if (! c.isNA(i)) {
            conv[col].convertValue(i, rowBuilder);
          }
        }
        frameBuilder.addRow(rowBuilder);
      }
      MojoFrame input = frameBuilder.toMojoFrame();

      // Transform whole chunk at once
      MojoFrame transformed = _pipeline.transform(input);

      // Write to NewChunks
      for (int col = 0; col < ncs.length; col++) {
        NewChunk nc = ncs[col];
        MojoColumn column = transformed.getColumn(col);
        assert column.size() == cs[0].len();
        switch (column.getType()) {
          case Bool:
            for (byte d : (byte[]) column.getData()) {
              nc.addNum(d, 0);
            }
            break;
          case Int32:
            for (int d : (int[]) column.getData()) {
              nc.addNum(d, 0);
            }
            break;
          case Int64:
            for (long d : (long[]) column.getData()) {
              nc.addNum(d, 0);
            }
            break;
          case Float32:
            for (float d : (float[]) column.getData()) {
              nc.addNum(d);
            }
            break;
          case Float64:
            for (double d : (double[]) column.getData()) {
              nc.addNum(d);
            }
            break;
          default:
            throw new UnsupportedOperationException("Output type " + column.getType() + " is currently not supported for MOJO2. See https://0xdata.atlassian.net/browse/PUBDEV-7741");
        }
      }
    }
  }

  private static MojoChunkConverter makeConverter(Chunk c, int col, Type type) {
    switch (c.vec().get_type()) {
      case Vec.T_NUM:
        if (type == Type.Str)
          return new MojoChunkConverter(c, col) {
            @Override
            void convertValue(int i, MojoRowBuilder target) {
              // This is best effort - we might convert the double to an incorrect format (example: 1000 vs 1e3)
              final double val = _c.atd(i);
              target.setString(_col, String.valueOf(val));
            }
          };
        else
          return new MojoChunkConverter(c, col) {
            @Override
            void convertValue(int i, MojoRowBuilder target) {
              target.setDouble(_col, _c.atd(i));
            }
          };
      case Vec.T_CAT:
        return new MojoChunkConverter(c, col) {
          @Override
          void convertValue(int i, MojoRowBuilder target) {
            target.setValue(_col, _c.vec().domain()[(int) _c.at8(i)]);
          }
        };
      case Vec.T_STR:
        if (type == Type.Str)
          return new MojoChunkConverter(c, col) {
            @Override
            void convertValue(int i, MojoRowBuilder target) {
              target.setString(_col, _c.atStr(new BufferedString(), i).toString());
            }
          };
        else
          return new MojoChunkConverter(c, col) {
            @Override
            void convertValue(int i, MojoRowBuilder target) {
              target.setValue(_col, _c.atStr(new BufferedString(), i).toString());
            }
          };
      case Vec.T_TIME:
        if (type == Type.Time64)
          return new MojoChunkConverter(c, col) {
            @Override
            void convertValue(int i, MojoRowBuilder target) {
              final long timestamp = _c.at8(i);
              target.setTimestamp(_col, new Timestamp(timestamp));
            }
          };
        else {
          final DateFormat dateFormatter = dateFormatter();
          return new MojoChunkConverter(c, col) {
            @Override
            void convertValue(int i, MojoRowBuilder target) {
              final long timestamp = _c.at8(i);
              target.setValue(_col, dateFormatter.format(new Date(timestamp))); // Not ideal, would be better to pass directly
            }
          };
        }
      default:
        throw new IllegalStateException("Unexpected column type: " + c.vec().get_type_str());
    }
  }

  private static DateFormat dateFormatter() {
    return new SimpleDateFormat("MM/dd/yyyy'T'hh:mm:ss.sss");
  }

  private static abstract class MojoChunkConverter {
    final int _col;
    final Chunk _c;

    private MojoChunkConverter(Chunk c, int col) { _c = c; _col = col; }

    abstract void convertValue(int i, MojoRowBuilder target);
  }

}
