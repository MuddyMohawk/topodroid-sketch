package com.topodroid.TDX;

import org.junit.Test;
import static org.junit.Assert.*;

public class SectionReferenceSelectionTest
{
  @Test public void eitherEndpointKeepsLegButOnlyCheckedStationHasSplays()
  {
    SectionReferenceSelection choices = new SectionReferenceSelection();
    choices.setStation( "A", true );
    assertTrue( choices.includesLeg( "A", "B" ) );
    assertTrue( choices.includesLeg( "B", "A" ) );
    assertFalse( choices.includesLeg( "B", "C" ) );
    assertTrue( choices.includesSplays( "A" ) );
    assertFalse( choices.includesSplays( "B" ) );
    choices.setSplays( "A", false );
    choices.setStation( "A", false );
    choices.setStation( "A", true );
    assertFalse( choices.includesSplays( "A" ) );
  }

  @Test public void draftDoesNotModifyOriginalAndAllowsEmptySelection()
  {
    SectionReferenceSelection original = new SectionReferenceSelection();
    original.setStation( "A", true );
    SectionReferenceSelection draft = new SectionReferenceSelection( original );
    draft.setStation( "A", false );
    draft.showOutOfPlane = false;
    assertTrue( original.includes( "A" ) );
    assertTrue( original.showOutOfPlane );
    assertTrue( draft.stations.isEmpty() );
    assertFalse( draft.includesLeg( "A", "B" ) );
  }

  @Test public void filterUsesDegreesAndStrictThresholdInBothDirections()
  {
    assertFalse( SectionReferenceSelection.isOutOfPlane( 30, 60 ) );
    assertFalse( SectionReferenceSelection.isOutOfPlane( 60, 60 ) );
    assertFalse( SectionReferenceSelection.isOutOfPlane( -60, 60 ) );
    assertTrue( SectionReferenceSelection.isOutOfPlane( 60.01f, 60 ) );
    assertTrue( SectionReferenceSelection.isOutOfPlane( -60.01f, 60 ) );
  }

  @Test public void simultaneousRenamesPreserveSplayChoices()
  {
    SectionReferenceSelection choices = new SectionReferenceSelection();
    choices.setStation( "A", true );
    choices.setSplays( "B", false );
    java.util.Map<String,String> map = new java.util.HashMap<>();
    map.put( "A", "B" ); map.put( "B", "A" );
    choices.rename( map );
    assertTrue( choices.includes( "B" ) );
    assertFalse( choices.includes( "A" ) );
    assertTrue( choices.hiddenSplays.contains( "A" ) );
    assertFalse( choices.hiddenSplays.contains( "B" ) );
  }
}
