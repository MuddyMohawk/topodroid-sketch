package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DelimitedTextTest
{
  @Test public void plainAndNullFieldsRemainSimple()
  {
    assertEquals( "", DelimitedText.field( null, ',' ) );
    assertEquals( "station-1", DelimitedText.field( "station-1", ',' ) );
  }

  @Test public void delimiterQuoteAndLineBreaksAreQuotedAndEscaped()
  {
    assertEquals( "\"left,right\"", DelimitedText.field( "left,right", ',' ) );
    assertEquals( "\"say \"\"hello\"\"\"", DelimitedText.field( "say \"hello\"", ',' ) );
    assertEquals( "\"first\nsecond\"", DelimitedText.field( "first\nsecond", ',' ) );
    assertEquals( "a,b", DelimitedText.field( "a,b", '|' ) );
  }
}
