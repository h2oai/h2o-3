package hex.genmodel.attributes;


import java.util.Arrays;
import java.util.Objects;

/**
 * A two-dimensional table capable of containing generic values in each cell.
 * Useful for description of various models.
 */
public class Table {
    private String _tableHeader;
    private String _tableDescription;
    private String[] _rowHeaders;
    private String[] _colHeaders;
    private ColumnType[] _colTypes;
    private Object[][] _cellValues;
    private String _colHeaderForRowHeaders;

    /**
     * @param tableHeader
     * @param tableDescription
     * @param rowHeaders
     * @param columnHeaders
     * @param columnTypes
     * @param colHeaderForRowHeaders
     */
    public Table(String tableHeader, String tableDescription, String[] rowHeaders, String[] columnHeaders,
                 ColumnType[] columnTypes, String colHeaderForRowHeaders, Object[][] cellValues) {
        Objects.requireNonNull(columnHeaders);
        Objects.requireNonNull(rowHeaders);
        Objects.requireNonNull(cellValues);

        if (tableHeader == null)
            _tableHeader = "";
        if (tableDescription == null)
            _tableDescription = "";
        _colHeaderForRowHeaders = colHeaderForRowHeaders;

        // Fill row headers
        for (int r = 0; r < rowHeaders.length; ++r) {
            if (rowHeaders[r] == null)
                rowHeaders[r] = "";
        }

        // Fill column headers
        for (int c = 0; c < columnHeaders.length; ++c) {
            if (columnHeaders[c] == null)
                columnHeaders[c] = "";
        }

        final int rowDim = rowHeaders.length;
        final int colDim = columnHeaders.length;

        if (columnTypes == null) {
            columnTypes = new ColumnType[colDim];
            Arrays.fill(_colTypes, ColumnType.STRING);
        }

        _tableHeader = tableHeader;
        _tableDescription = tableDescription;
        _rowHeaders = rowHeaders;
        _colHeaders = columnHeaders;
        _colTypes = columnTypes;
        _cellValues = cellValues;
    }

    public enum ColumnType {
        LONG,
        DOUBLE,
        STRING;

        public static ColumnType extractType(final String type) {
            if (type == null) return ColumnType.STRING;

            String formattedType = type.trim().toUpperCase();
            try {
                return ColumnType.valueOf(formattedType);
            } catch (IllegalArgumentException e) {
                return ColumnType.STRING;
            }
        }
    }

    public String getTableHeader() {
        return _tableHeader;
    }

    public String getTableDescription() {
        return _tableDescription;
    }

    public String[] getRowHeaders() {
        return _rowHeaders;
    }

    public String[] getColHeaders() {
        return _colHeaders;
    }

    public ColumnType[] getColTypes() {
        return _colTypes;
    }

    public String[] getColTypesString() {
        String[] colTypesString = new String[_colTypes.length];

        for (int i = 0; i < colTypesString.length; i++) {
            colTypesString[i] = _colTypes[i].toString().toLowerCase();
        }

        return colTypesString;
    }

    public Object[][] getCellValues() {
        return _cellValues;
    }

    public String getColHeaderForRowHeaders() {
        return _colHeaderForRowHeaders;
    }

    public int columns() {
        return _cellValues.length;
    }

    public int rows() {
        return _cellValues[0] != null ? _cellValues[0].length : 0;
    }
    
    public Object getCell(final int column, final int row){
        return _cellValues[column][row];
    }

    public int findColumnIndex(final String columnName) {

        for (int i = 0; i < _colHeaders.length; i++) {
            if (_colHeaders[i].equals(columnName)) return i;
        }
        
        return -1;
    }
}
