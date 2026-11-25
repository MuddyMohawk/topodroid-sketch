package com.topodroid.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

@com.topodroid.qa.TestFeatures({com.topodroid.qa.TestFeatures.Feature.EXCHANGE, com.topodroid.qa.TestFeatures.Feature.DATA})
public class TDsafUriTest
{
  @Test
  public void displayNameWinsOverOpaqueUriId()
  {
    assertEquals( "survey.dat", TDsafUri.selectDocumentName( "survey.dat", "123456" ) );
  }

  @Test
  public void lastPathSegmentIsUsedWhenDisplayNameIsUnavailable()
  {
    assertEquals( "survey.dat", TDsafUri.selectDocumentName( null, "survey.dat" ) );
    assertEquals( "survey.dat", TDsafUri.selectDocumentName( "", "survey.dat" ) );
    assertNull( TDsafUri.selectDocumentName( null, null ) );
  }
}
