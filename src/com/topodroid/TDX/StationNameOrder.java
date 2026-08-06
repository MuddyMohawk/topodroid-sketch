/* @file StationNameOrder.java
 *
 * @brief Natural display ordering for survey station names
 */
package com.topodroid.TDX;

/** Compares digit runs numerically so station 2 precedes station 10. */
final class StationNameOrder
{
  private StationNameOrder() { }

  static int compare( String first, String second )
  {
    if ( first == second ) return 0;
    if ( first == null ) return -1;
    if ( second == null ) return 1;

    int first_index = 0;
    int second_index = 0;
    int first_length = first.length();
    int second_length = second.length();
    while ( first_index < first_length && second_index < second_length ) {
      char first_char = first.charAt( first_index );
      char second_char = second.charAt( second_index );
      if ( Character.isDigit( first_char ) && Character.isDigit( second_char ) ) {
        int first_end = digitRunEnd( first, first_index );
        int second_end = digitRunEnd( second, second_index );
        int first_significant = skipLeadingZeros( first, first_index, first_end );
        int second_significant = skipLeadingZeros( second, second_index, second_end );
        int first_digits = first_end - first_significant;
        int second_digits = second_end - second_significant;
        if ( first_digits != second_digits ) return first_digits < second_digits ? -1 : 1;
        for ( int offset = 0; offset < first_digits; ++offset ) {
          char a = first.charAt( first_significant + offset );
          char b = second.charAt( second_significant + offset );
          if ( a != b ) return a < b ? -1 : 1;
        }
        int first_run_length = first_end - first_index;
        int second_run_length = second_end - second_index;
        if ( first_run_length != second_run_length ) {
          // Equal numeric values: prefer the spelling with fewer leading zeroes.
          return first_run_length < second_run_length ? -1 : 1;
        }
        first_index = first_end;
        second_index = second_end;
        continue;
      }

      char first_folded = Character.toLowerCase( first_char );
      char second_folded = Character.toLowerCase( second_char );
      if ( first_folded != second_folded ) return first_folded < second_folded ? -1 : 1;
      ++first_index;
      ++second_index;
    }
    if ( first_index != first_length || second_index != second_length ) {
      return first_index == first_length ? -1 : 1;
    }
    return first.compareTo( second );
  }

  private static int digitRunEnd( String value, int start )
  {
    int end = start;
    while ( end < value.length() && Character.isDigit( value.charAt( end ) ) ) ++end;
    return end;
  }

  private static int skipLeadingZeros( String value, int start, int end )
  {
    int index = start;
    while ( index < end && value.charAt( index ) == '0' ) ++index;
    return index;
  }
}
