import java.io.IOException;

public class Test {

    public static void main(String args[]) throws IOException {

        String inputNffPath = "/home/nikhil/repos/backup/test_datatable_nff/weather/";
        NffParser nff = new NffParser(inputNffPath);

        // always call Initialize() before any ops
        nff.initialize();

        // Start reading rows
        System.out.println("Output rows##########################");
        String row;
        for (int numRowsRead = 0; numRowsRead <= nff.numRows-1; numRowsRead++) {
            row = nff.getNext();
            System.out.println(row);
        }
        System.out.println("Output rows##########################");

        // Check if there are more rows - todo - check if rowsRead < nff.totalRows

        System.out.println("total number of rows:"+ nff.numRows);
        System.out.println("Number of columns:"+nff.numColumns);
        System.out.println("Header:"+nff.getHeader());
        System.out.println("Column names:" + nff.getColumnNames().length);

        //Always call Close() when done explicitly
        nff.close();


        NffParser nff1 = new NffParser(inputNffPath);
        nff1.initialize();

        System.out.println("Output rows again ********************");
        int countRows = 0;
        while(nff1.hasNext()){
            System.out.println(nff1.getNext());
            countRows++;
        }
        System.out.println("Output rows again ********************");

        System.out.println(countRows);
        System.out.println(nff1.numRows);
        System.out.println("Number of columns:"+nff.numColumns);
        nff1.close();
    }
}
