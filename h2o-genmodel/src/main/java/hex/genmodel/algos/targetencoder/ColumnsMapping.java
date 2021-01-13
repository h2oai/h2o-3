package hex.genmodel.algos.targetencoder;

public class ColumnsMapping {
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

class ColumnsToSingleMapping extends ColumnsMapping {

    private String[] _toDomain;
    private long[] _toDomainAsNum;

    public ColumnsToSingleMapping(String[] from, String to, String[] toDomain) {
        super(from, new String[]{to});
        _toDomain = toDomain;
        _toDomainAsNum = stringArrayToLong(toDomain);
    }

    public String toSingle() {
        return to()[0];
    }

    public String[] toDomain() {
        return _toDomain;
    }
    
    public long[] toDomainAsNum() {
        return _toDomainAsNum;
    }
    
    private static long[] stringArrayToLong(String[] arr) {
        if (arr == null) return null;
        try {
            long[] res = new long[arr.length];
            for (int i=0; i < arr.length; i++) {
                res[i] = Long.parseLong(arr[i]);
            }
            return res;
        } catch (NumberFormatException nfe) {
            return null;
        }
    }
}

