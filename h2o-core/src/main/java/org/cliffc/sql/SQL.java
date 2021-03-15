package org.cliffc.sql;

import water.H2O;

public class SQL {
  public static void main( String[] args ) {

    H2O.main(new String[0]);
    
    throw new RuntimeException("hey");
  }
}
