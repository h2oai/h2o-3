package water.udf.specialized;

import com.google.common.collect.Sets;
import water.Key;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.udf.*;
import water.udf.fp.Function;
import water.udf.fp.Functions;
import water.util.FrameUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static water.udf.specialized.Integers.*;

/**
 * Specialized factory for enums (aka Cats)
 */
public class Enums extends DataColumns.BaseFactory<Integer, EnumColumn> {
  final String[] domain;
  
  /**
   * deserialization :(
   */
  public Enums() {
    super(Vec.T_CAT, "Cats");
    domain = null;
  }

  public Enums(String[] domain) {
    super(Vec.T_CAT, "Cats");
    this.domain = domain;
  }

  public static Enums enums(String[] domain) {
    if (domain == null) throw new IllegalArgumentException("Domain missing in Enums factory creation");
    return new Enums(domain);
  }

  public static ColumnFactory<Integer, EnumColumn> enumsAlt(String[] domain) {
    return enums(domain);
  }

  @Override
  public DataChunk<Integer> apply(final Chunk c) {
    return new IntegerChunk(c);
  }

  public EnumColumn newColumn(long length, final Function<Long, Integer> f) throws IOException {
    return new SingleColumnFrame.EnumFrame(length, f, domain).newColumn();
  }

  public EnumColumn newColumn(List<Integer> source) throws IOException {
    return new SingleColumnFrame.EnumFrame(source.size(), Functions.onList(source), domain).newColumn();
  }
  
  @Override
  public EnumColumn newColumn(final Vec vec) {
    if (vec.get_type() != Vec.T_CAT)
      throw new IllegalArgumentException("Expected type T_CAT, got " + vec.get_type_str());
    vec.setDomain(domain);
    return new EnumColumn(vec);
  }

  static Frame oneHotEncoding(String name, Vec vec) throws IOException {
    if (vec.domain() == null) throw new IllegalArgumentException("Could not create frame " + name + ", vec has no domain");
    EnumColumn enumColumn = new EnumColumn(vec);
    UnfoldingFrame<Integer, DataColumn<Integer>> plain = enumColumn.oneHotEncodedFrame(name);
    return plain.materialize();
  }

  public static Frame oneHotEncoding(Key<Frame> destKey, Frame dataset, String[] skipCols) throws IOException {
    return oneHotEncoding(dataset, skipCols, new Frame(destKey));
  }  
  
  public static Frame oneHotEncoding(Frame source, String[] skipCols) throws IOException {
    return oneHotEncoding(FrameUtils.newFrameKey(), source, skipCols);
  }

  static Frame oneHotEncoding(Frame source, String[] skipCols, Frame target) throws IOException {
    Set<String> skipit = (skipCols == null) ? Collections.<String>emptySet() : Sets.newHashSet(skipCols);

    for(String colName : source.names()) {
      final Vec vec = source.vec(colName);
      if (skipit.contains(colName) || !vec.isCategorical() || vec.domain() == null) {
        target.add(colName, vec);
      } else {
        target.add(oneHotEncoding(colName, vec));
      }
    }
    return target;
  }

}
