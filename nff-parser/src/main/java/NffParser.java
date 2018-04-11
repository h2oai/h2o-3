
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class NffParser {

    // Method to read signed and unsigned integer values fo various sizes and decode them
    public static long get_i8(byte[] bytes){
        long value = 0;
        byte m = bytes[bytes.length-1];
        int msb = (m & 0xff) >> 7;
        // if msb = 1 then it is a negative number
        // if msb = 0 then it is a positive number
        if(msb==1){
            // Decode the original value from the 2's complement as it is a negative int
            for(int i = 0 ; i < bytes.length ; i++){
                bytes[i] = (byte)~bytes[i]; // flip all the bits 0->1 & 1->0
                if(i==0) value += (bytes[i] & 0x000000FF); // get the complemented decimal value back
                else value += (bytes[i] & 0x000000FF) << 8*i;
            }
            // get the original decimal value = (value + 1) * -1
            value = (value + 1) * -1;
        }else{
            // Decode the positive int value
            for(int i = 0 ; i < bytes.length ; i++){
                if(i==0) value += (bytes[i] & 0x000000FF); // get the decimal value back
                else value += (bytes[i] & 0x000000FF) << 8*i;
            }
        }
        return value;
    }

    public static int get_i(byte[] bytes){
        int value = 0;
        byte m = bytes[bytes.length-1];
        int msb = (m & 0xff) >> 7;
        // if msb = 1 then it is a negative number
        // if msb = 0 then it is a positive number
        if(msb==1){
            // Decode the original value from the 2's complement as it is a negative int
            for(int i = 0 ; i < bytes.length ; i++){
                bytes[i] = (byte)~bytes[i]; // flip all the bits 0->1 & 1->0
                if(i==0) value += (bytes[i] & 0x000000FF); // get the complemented decimal value back
                else value += (bytes[i] & 0x000000FF) << 8*i;
            }
            // get the original decimal value = (value + 1) * -1
            value = (value + 1) * -1;
        }else{
            // Decode the positive int value
            for(int i = 0 ; i < bytes.length ; i++){
                if(i==0) value += (bytes[i] & 0x000000FF); // get the decimal value back
                else value += (bytes[i] & 0x000000FF) << 8*i;
            }
        }
        return value;
    }

    final static class ReturnValuesS4{
        private final int count;
        private final String fieldRead;

        public int getCount() {
            return count;
        }

        public String getFieldRead() {
            return fieldRead;
        }

        public ReturnValuesS4(int count, String fieldRead) {
            this.count = count;
            this.fieldRead = fieldRead;
        }
    }

    private static ReturnValuesS4 readTest(int s4_count, BufferedInputStream v1, String dirPath, String k, int fieldLength) throws IOException {

        //all strings read
        boolean allRead = false;
        //state variables for reading s4 type
        long s4_current_offset, s4_previous_offset, s4_length;
        int count=0;
        byte[] data = new byte[0], offset_bytes = new byte[fieldLength];
        BufferedInputStream v = new BufferedInputStream(new FileInputStream(dirPath + k),16384);
        BufferedInputStream dataBuffer = new BufferedInputStream(new FileInputStream(dirPath + k + "_str"),16384); // todo - fix buffer size
        v.read(offset_bytes);
        s4_previous_offset = get_i(offset_bytes);

        // if its the first byte, read twice - which would yield the first column
        if(s4_previous_offset==-1){
            v.read(offset_bytes);
            s4_current_offset = get_i(offset_bytes);
            s4_length = s4_current_offset-1;
            data = new byte[(int)s4_length];
            dataBuffer.read(data); // read the actual string
            s4_previous_offset = s4_current_offset;
            count++;
            if(s4_count==0) {
                return new ReturnValuesS4(count, new String(data));
            }
        }

        for (; count!=s4_count+1 ;count++){
            v.read(offset_bytes);
            s4_current_offset = get_i(offset_bytes);

            if (s4_previous_offset!=-1){
                s4_length = s4_current_offset-s4_previous_offset;
            }else{
                s4_length = s4_current_offset-1;
            }
//            System.out.println(k);
//            System.out.println(s4_length);
//            System.out.println(s4_current_offset);
            data = new byte[(int)s4_length];
            dataBuffer.read(data); // read the actual string
            s4_previous_offset = s4_current_offset;
        }
        v.close();
        dataBuffer.close();
        // todo - remove count from return value - not needed
        return new ReturnValuesS4(count, new String(data));
    }

    public static void main(String args[]) throws FileNotFoundException,IOException {

        /*
         Steps to decode datatable NFF format:
         1) Read the .meta_nff file
            a) First row is just a marker - ignore this
            b) Second row has the number of rows in the data
            c) Third row has the headers - filename,stype,meta,colname
            d) Each consecutive row now represents the nff files corresponding to each column

         2) Read the row meta data for each column (stored in a separate file)

         3) To generate one row in the orignal data - will need one value from each of the column file
            This would entail having n=number of column open file pointers at any given point.
            String columns are represented by two files ( need to be read with the seek of the offset)

         4) The open file pointers would be used to read one field from each of the n files and populate one row at a time
            Loop through all the rows in each of the n files to generated back all the rows.
            Keep flushing the rows read to disk at regular intervals  ( might need optimizations)
         */

        // Read the .meta_nff file into a map
        BufferedReader br = null;
        TreeMap<String, List<String>> metaNff = new TreeMap<>();
        String dirPath = "/home/nikhil/repos/backup/test_datatable_nff/weather/";
        String seperator = ",";
        // TODO - currently opens the n files simultaneously- each having data of a column
        TreeMap<String, BufferedInputStream> filePointers = new TreeMap<>();
        try {

            br = new BufferedReader(new FileReader(dirPath + "_meta.nff"));
            String[] row;
            String line = br.readLine(); // first row
            String numRows = line = br.readLine(); // second row
            String header = line = br.readLine(); // file header
            Integer numCols;

            line = br.readLine(); // fourth row onwards
            while (line != null) {
//                System.out.println(line);
                row = line.split(",");
                metaNff.put(row[0], new ArrayList<>(Arrays.asList(row)).subList(1, row.length));
                line = br.readLine();
            }
            numCols = metaNff.keySet().size();
            System.out.println("Number of columns:"+ numCols);
//            metaNff.forEach((k, v) -> System.out.println(k + v));

            int bufferSize = 16384;
            metaNff.forEach((k, v) -> {
                try {
//                    System.out.println("File name:"+k);
//                    System.out.println("Column name:"+v.get(2));
                    filePointers.put(k, new BufferedInputStream(new FileInputStream(dirPath + k), bufferSize));
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            });

//            filePointers.forEach((k, v) -> System.out.println(k + v));

            // start creating rows of data
            String outputPath = "out.csv";
            BufferedWriter output = new BufferedWriter(new FileWriter(outputPath));
            StringBuilder outputRow = new StringBuilder();

            //TODO - take care of big-endian < - > little-endian conversions as bytes need to be read and decoded properly
            // Can use something like - (ByteOrder.nativeOrder().equals(ByteOrder.BIG_ENDIAN)
            int totalRows = Integer.parseInt(numRows.split("=")[1].trim());
            System.out.println("Number of rows:"+totalRows);

            // Map for count of bytes read for s4,s8 and sx
            HashMap<String,Integer> s4CountMap = new HashMap<>();
            HashMap<String,Integer> s8CountMap = new HashMap<>();

            // count of number of string read
            int s4_count , s8_count = 0;
            for (int numRowsRead = 0; numRowsRead <= totalRows-1; numRowsRead++) {
            //Loop over all rows and create the data file file
//                 System.out.println(outputRow);
            for ( String k : filePointers.keySet()) {
                BufferedInputStream v = filePointers.get(k);
                try {
                    // Read the byte stream of the line
                    String colType = metaNff.get(k).get(0);
//                    System.out.println("TYPE:"+colType);
                    // C types - http://en.cppreference.com/w/cpp/types/integer
                    switch (colType) {
                        case "i1":
                            byte[] bytes = new byte[1];
                            v.read(bytes);
                            outputRow.append(get_i(bytes)); // read 1 byte
                            break;
                        case "i2": // todo - check if it can be implemented using ByteBuffer
                            bytes = new byte[2];
                            v.read(bytes);
                            outputRow.append(get_i(bytes));
                            break;
                        case "i4":
                            bytes = new byte[4];
                            v.read(bytes);
                            outputRow.append(get_i(bytes));
                            break;
                        case "i8":
                            bytes = new byte[8];
                            v.read(bytes);
                            outputRow.append(get_i8(bytes));
                            break;
                        case "b1":
                            int value = v.read();
//                            if (ss == 1) outputRow.append(true);
//                            else if (ss == 0) outputRow.append(false);
//                            else outputRow.append("null");
                            outputRow.append(Integer.toString(value));
                            break;
                        case "r4": // todo - can't generate test data from datatable - not sure if implemented
                            bytes = new byte[4];
                            v.read(bytes);
                            float value_r4 = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getFloat();
                            outputRow.append(value_r4);
                            break;
                        case "r8":
                            bytes = new byte[8];
                            v.read(bytes);
                            double value_r8 = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getDouble();
//                            System.out.println("ppp:"+value_r8);
//                            System.out.println("Result: " + Arrays.toString(bytes));
                            outputRow.append(value_r8);
                            break;
                        case "d2": // todo - not implemented as it is not used in datatable as of now
                            bytes = new byte[2];
                            v.read(bytes);
                            break;
                        case "d4": // todo - not implemented as it is not used in datatable as of now
                            bytes = new byte[4];
                            v.read(bytes);
                            break;
                        case "d8": // todo - not implemented as it is not used in datatable as of now
                            bytes = new byte[8];
                            v.read(bytes);
                            break;
                        case "s4":
                            if (!s4CountMap.containsKey(k)){
                                s4_count=0;
                                s4CountMap.put(k,0);
                            }else{
                                s4_count = s4CountMap.get(k);
                            }
                            ReturnValuesS4 returnS4 = readTest(s4_count, v, dirPath, k, 4);
                            // update the offset of bytes read for this column in the map
                            s4_count++;
                            s4CountMap.put(k,s4_count);
                            outputRow.append(returnS4.getFieldRead());
                            break;
                        case "s8": // todo - not sure if implemented in datatable - but the below should work
                            if (!s8CountMap.containsKey(k)){
                                s8_count=0;
                                s8CountMap.put(k,0);
                            }else{
                                s8_count = s8CountMap.get(k);
                            }
                            ReturnValuesS4 returnS8 = readTest(s8_count, v, dirPath, k, 8);
                            s8_count++;
                            s8CountMap.put(k,s8_count);
                            outputRow.append(returnS8.getFieldRead());
                            break;
                        case "sx": // todo - implement this
                            break;
                        case "e1": // todo - not implemented as it is not used in datatable as of now
                            bytes = new byte[1];
                            v.read(bytes);
                            break;
                        case "e2": // todo - not implemented as it is not used in datatable as of now
                            bytes = new byte[2];
                            v.read(bytes);
                            break;
                        case "e4": // todo - not implemented as it is not used in datatable as of now
                            bytes = new byte[4];
                            v.read(bytes);
                            break;
                        case "t8": // todo - not implemented as it is not used in datatable as of now
                            bytes = new byte[8];
                            v.read(bytes);
                            break;
                        case "T4": //todo - not implemented as it is not used in datatable as of now
                            bytes = new byte[4];
                            v.read(bytes);
                            break;
                        case "t4": //todo - not implemented as it is not used in datatable as of now
                            bytes = new byte[4];
                            v.read(bytes);
                            break;
                        case "t2": // todo - not implemented as it is not used in datatable as of now
                            bytes = new byte[2];
                            v.read(bytes);
                            break;
                        case "o8": // todo - not implemented as it is not used in datatable as of now
                            bytes = new byte[8];
                            v.read(bytes);
                            break;

                    }
                    outputRow.append(seperator);
                } catch (IOException e) {
                    e.printStackTrace();
                }
//            });
            }
            outputRow.setLength(outputRow.length() - 1); // chop off trailing comma
            outputRow.append("\n"); //new line after generating one row // todo - fix the newline character
            }
        System.out.println();
        System.out.println("OUTPUT ROWS");
        outputRow.setLength(outputRow.length()-1); // to strip off the extra new line at the end
        System.out.println(outputRow.toString());
        System.out.println("OUTPUT ROWS");
    }

        finally {
            // iterate over all the file pointers and close them
            filePointers.forEach((String k, BufferedInputStream v) -> {
                try {
                    v.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            );
            if(br!=null) {
                br.close();
            }
        }
    }
}
