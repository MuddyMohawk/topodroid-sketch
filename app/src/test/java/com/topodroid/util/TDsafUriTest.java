package com.topodroid.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class TDsafUriTest
{
  @Test
  public void displayNameWinsOverOpaqueUriId()
  {
    assertEquals( "TB.dat", TDsafUri.selectDocumentName( "TB.dat", "1000008055" ) );
  }

  @Test
  public void lastPathSegmentIsUsedWhenDisplayNameIsUnavailable()
  {
    assertEquals( "survey.dat", TDsafUri.selectDocumentName( null, "survey.dat" ) );
    assertEquals( "survey.dat", TDsafUri.selectDocumentName( "", "survey.dat" ) );
    assertNull( TDsafUri.selectDocumentName( null, null ) );
  }
}
