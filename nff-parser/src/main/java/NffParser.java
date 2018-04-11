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


import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

public class NffParser {

    private String inputNffDirectory;
    private int numRowsRead;
    int numRows, numColumns;
    private BufferedReader metaNffReader;
    private TreeMap<String, List<String>> metaNff;
    private String fieldSeparator;
    // TODO - currently opens the n files simultaneously- each having data of a column
    private TreeMap<String, BufferedInputStream> filePointers;
    private HashMap<String,Integer> s4CountMap;
    private HashMap<String,Integer> s8CountMap;
    private StringBuilder outputRow;

    public NffParser(String inputNffDirectory) {
        this.inputNffDirectory = inputNffDirectory;
        try {
            this.metaNffReader = new BufferedReader(new FileReader(inputNffDirectory + "_meta.nff"));
            this.metaNff = new TreeMap<>();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        this.fieldSeparator = ",";
        this.filePointers = new TreeMap<>();
        this.s4CountMap = new HashMap<>();
        this.s8CountMap = new HashMap<>();
    }

    public void initialize() throws IOException {
        String[] row;
        String line = this.metaNffReader.readLine(); // first row
        String numRows = line = this.metaNffReader.readLine(); // second row
        String header = line = this.metaNffReader.readLine(); // file header

        line = this.metaNffReader.readLine(); // fourth row onwards
        while (line != null) {
            row = line.split(",");
            this.metaNff.put(row[0], new ArrayList<>(Arrays.asList(row)).subList(1, row.length));
            line = this.metaNffReader.readLine();
        }
        this.numColumns = this.metaNff.keySet().size();
        int bufferSize = 16384; // TODO - fix
        this.metaNff.forEach((k, v) -> {
            try {
                this.filePointers.put(k, new BufferedInputStream(new FileInputStream(this.inputNffDirectory + k), bufferSize));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        });

        this.outputRow = new StringBuilder();
        //TODO - take care of big-endian < - > little-endian conversions as bytes need to be read and decoded properly
        // Can use something like - (ByteOrder.nativeOrder().equals(ByteOrder.BIG_ENDIAN)
        this.numRows = Integer.parseInt(numRows.split("=")[1].trim());
    }

    public String getNext(){
        // count of number of string read
        int s4Count , s8Count;
        for ( String key : this.filePointers.keySet()) {
            BufferedInputStream inputStream = this.filePointers.get(key);
            try {
                // Read the byte stream of the line
                String colType = this.metaNff.get(key).get(0);
                // C types - http://en.cppreference.com/w/cpp/types/integer
                switch (colType) {
                    case "i1":
                        byte[] bytes = new byte[1];
                        inputStream.read(bytes);
                        this.outputRow.append(getI(bytes)); // read 1 byte
                        break;
                    case "i2": // todo - check if it can be implemented using ByteBuffer
                        bytes = new byte[2];
                        inputStream.read(bytes);
                        this.outputRow.append(getI(bytes));
                        break;
                    case "i4":
                        bytes = new byte[4];
                        inputStream.read(bytes);
                        this.outputRow.append(getI(bytes));
                        break;
                    case "i8":
                        bytes = new byte[8];
                        inputStream.read(bytes);
                        this.outputRow.append(getI8(bytes));
                        break;
                    case "b1":
                        int value = inputStream.read();
                        //                            if (ss == 1) outputRow.append(true);
                        //                            else if (ss == 0) outputRow.append(false);
                        //                            else outputRow.append("null");
                        this.outputRow.append(Integer.toString(value));
                        break;
                    case "r4": // todo - can't generate test data from datatable - not sure if implemented
                        bytes = new byte[4];
                        inputStream.read(bytes);
                        float valueR4 = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getFloat();
                        this.outputRow.append(valueR4);
                        break;
                    case "r8":
                        bytes = new byte[8];
                        inputStream.read(bytes);
                        double valueR8 = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getDouble();
                        this.outputRow.append(valueR8);
                        break;
                    case "d2": // todo - not implemented as it is not used in datatable as of now
                        bytes = new byte[2];
                        inputStream.read(bytes);
                        break;
                    case "d4": // todo - not implemented as it is not used in datatable as of now
                        bytes = new byte[4];
                        inputStream.read(bytes);
                        break;
                    case "d8": // todo - not implemented as it is not used in datatable as of now
                        bytes = new byte[8];
                        inputStream.read(bytes);
                        break;
                    case "s4":
                        if (!this.s4CountMap.containsKey(key)){
                            s4Count=0;
                            this.s4CountMap.put(key,0);
                        }else{
                            s4Count = this.s4CountMap.get(key);
                        }
                        String dataS4 = readS4(s4Count, this.inputNffDirectory, key, 4);
                        // update the offset of bytes read for this column in the map
                        s4Count++;
                        this.s4CountMap.put(key,s4Count);
                        this.outputRow.append("\"" + dataS4 + "\"");
                        break;
                    case "s8": // todo - not sure if implemented in datatable - but the below should work
                        if (!this.s8CountMap.containsKey(key)){
                            s8Count=0;
                            this.s8CountMap.put(key,0);
                        }else{
                            s8Count = this.s8CountMap.get(key);
                        }
                        String dataS8 = readS4(s8Count, this.inputNffDirectory, key, 8);
                        s8Count++;
                        this.s8CountMap.put(key,s8Count);
                        this.outputRow.append("\"" + dataS8 + "\"");
                        break;
                    case "sx": // todo - implement this
                        break;
                    case "e1": // todo - not implemented as it is not used in datatable as of now
                        bytes = new byte[1];
                        inputStream.read(bytes);
                        break;
                    case "e2": // todo - not implemented as it is not used in datatable as of now
                        bytes = new byte[2];
                        inputStream.read(bytes);
                        break;
                    case "e4": // todo - not implemented as it is not used in datatable as of now
                        bytes = new byte[4];
                        inputStream.read(bytes);
                        break;
                    case "t8": // todo - not implemented as it is not used in datatable as of now
                        bytes = new byte[8];
                        inputStream.read(bytes);
                        break;
                    case "T4": //todo - not implemented as it is not used in datatable as of now
                        bytes = new byte[4];
                        inputStream.read(bytes);
                        break;
                    case "t4": //todo - not implemented as it is not used in datatable as of now
                        bytes = new byte[4];
                        inputStream.read(bytes);
                        break;
                    case "t2": // todo - not implemented as it is not used in datatable as of now
                        bytes = new byte[2];
                        inputStream.read(bytes);
                        break;
                    case "o8": // todo - not implemented as it is not used in datatable as of now
                        bytes = new byte[8];
                        inputStream.read(bytes);
                        break;
                }
                this.outputRow.append(this.fieldSeparator);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.outputRow.setLength(this.outputRow.length() - 1); // chop off trailing comma
        this.outputRow.append("\n"); //new line after generating one row // todo - fix the newline character

        this.numRowsRead++;

        if (numRowsRead==this.numRows){
            this.outputRow.setLength(this.outputRow.length()-1);
        }
        return this.outputRow.toString(); //todo - return outputRow
    }

    public boolean hasNext(){
        return (this.numRowsRead < this.numRows);
    }

    public void close(){
        try{

        }finally {
            // iterate over all the file pointers and close them
            this.filePointers.forEach((String k, BufferedInputStream v) -> {
                        try {
                            v.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
            );
            if(metaNffReader !=null) {
                try {
                    this.metaNffReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public String getHeader(){
        StringBuilder headerOutput = new StringBuilder();
        for(String k: this.metaNff.keySet()){
            headerOutput.append(this.metaNff.get(k).get(2));
            headerOutput.append(",");
        }
        headerOutput.setLength(headerOutput.length()-1);
        return headerOutput.toString();
    }

    public String[] getColumnNames(){
        String[] columnNames = new String[this.metaNff.keySet().size()];
        int i = 0;
        for(String k: this.metaNff.keySet()){
            columnNames[i] = k;
            i++;
        }
        return columnNames;
    }

    // Method to read signed and unsigned integer values fo various sizes and decode them
    public static long getI8(byte[] bytes){
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

    public static int getI(byte[] bytes){
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

    private static String readS4(int s4Count, String dirPath, String k, int fieldLength) throws IOException {

        //state variables for reading s4 type
        long s4CurrentOffset, s4PreviousOffset, s4Length;
        int count=0;
        byte[] data = new byte[0], offsetBytes = new byte[fieldLength];
        BufferedInputStream v = new BufferedInputStream(new FileInputStream(dirPath + k),16384);
        BufferedInputStream dataBuffer = new BufferedInputStream(new FileInputStream(dirPath + k + "_str"),16384); // todo - fix buffer size
        v.read(offsetBytes);
        s4PreviousOffset = getI(offsetBytes);

        // if its the first byte, read twice - which would yield the first column
        if(s4PreviousOffset==-1){
            v.read(offsetBytes);
            s4CurrentOffset = getI(offsetBytes);
            s4Length = s4CurrentOffset-1;
            data = new byte[(int)s4Length];
            dataBuffer.read(data); // read the actual string
            s4PreviousOffset = s4CurrentOffset;
            count++;
            if(s4Count==0) {
                return new String(data);
            }
        }

        for (; count!=s4Count+1 ;count++){
            v.read(offsetBytes);
            s4CurrentOffset = getI(offsetBytes);

            if (s4CurrentOffset<-1) {
                data = null;
                continue;
            }

            if (s4PreviousOffset!=-1){
                s4Length = s4CurrentOffset-s4PreviousOffset;
            }else{
                s4Length = s4CurrentOffset-1;
            }

            data = new byte[(int)s4Length];
            dataBuffer.read(data); // read the actual string
            s4PreviousOffset = s4CurrentOffset;
        }
        v.close();
        dataBuffer.close();
        if(data!=null) {
            return new String(data);
        }else {
            return null;
        }
    }

}
