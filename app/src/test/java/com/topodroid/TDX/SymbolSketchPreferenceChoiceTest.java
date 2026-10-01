package com.topodroid.TDX;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class SymbolSketchPreferenceChoiceTest
{
  @Test public void unfamiliarSymbolUsesStandardAndKeepsCurrentPreset()
  {
    SymbolSketchPreferences.Choice choice = new SymbolSketchPreferences.Choice( 0, 0 );
    assertEquals( 2, choice.resolveStyle( 3, 3 ) );
    assertEquals( 4, choice.resolvePreset( 4, 4 ) );
    assertEquals( 1, choice.resolveStyle( 1, 1 ) );
  }

  @Test public void hiddenSlotsFallBackWithoutChangingTheirIdentity()
  {
    SymbolSketchPreferences.Choice choice = new SymbolSketchPreferences.Choice( 5, 6 );
    assertEquals( 2, choice.resolveStyle( 3, 3 ) );
    assertEquals( 1, choice.resolvePreset( 4, 1 ) );
    assertEquals( 5, choice.resolveStyle( 5, 1 ) );
    assertEquals( 6, choice.resolvePreset( 6, 1 ) );
  }
}
