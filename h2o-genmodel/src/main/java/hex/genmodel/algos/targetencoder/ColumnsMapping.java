package hex.genmodel.algos.targetencoder;

import java.io.Serializable;

public class ColumnsMapping implements Serializable {
    private String[] _from;
    private String[] _to;

    public ColumnsMapping(String[] from, String[] to) {
        _from = from;
        _to = to;
    }

    public String[] from() {
        return _from;
    }

    public String[] to() {
        return _to;
    }
}
