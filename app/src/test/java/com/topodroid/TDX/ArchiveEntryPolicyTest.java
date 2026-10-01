package com.topodroid.TDX;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ArchiveEntryPolicyTest
{
  @Test public void acceptsSurveyRelativeEntries()
  {
    assertTrue( ArchiveEntryPolicy.isSafeName( "manifest" ) );
    assertTrue( ArchiveEntryPolicy.isSafeName( "survey.sql" ) );
    assertTrue( ArchiveEntryPolicy.isSafeName( "photo/image.png" ) );
    assertTrue( ArchiveEntryPolicy.isSafeName( "photo/" ) );
  }

  @Test public void rejectsAbsoluteTraversalAndAmbiguousEntries()
  {
    assertFalse( ArchiveEntryPolicy.isSafeName( null ) );
    assertFalse( ArchiveEntryPolicy.isSafeName( "" ) );
    assertFalse( ArchiveEntryPolicy.isSafeName( "/survey.sql" ) );
    assertFalse( ArchiveEntryPolicy.isSafeName( "C:/survey.sql" ) );
    assertFalse( ArchiveEntryPolicy.isSafeName( "../survey.sql" ) );
    assertFalse( ArchiveEntryPolicy.isSafeName( "photo/../survey.sql" ) );
    assertFalse( ArchiveEntryPolicy.isSafeName( "photo\\image.png" ) );
    assertFalse( ArchiveEntryPolicy.isSafeName( "photo//image.png" ) );
    assertFalse( ArchiveEntryPolicy.isSafeName( "photo//" ) );
  }
}
