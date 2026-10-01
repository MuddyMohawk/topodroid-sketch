package com.topodroid.TDX;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SymbolEditorDocumentTest
{
  @Test public void requiredLineSymbols_areIdentifiedByStableTherionName()
  {
    assertTrue( RequiredLineSymbols.isRequired( "user" ) );
    assertTrue( RequiredLineSymbols.isRequired( "wall" ) );
    assertTrue( RequiredLineSymbols.isRequired( "section" ) );
    assertFalse( RequiredLineSymbols.isRequired( "slope" ) );
  }

  @Test public void requiredLineEditor_exposesAppearanceFields()
  {
    assertTrue( RequiredLineSymbols.isAppearanceField( "color" ) );
    assertTrue( RequiredLineSymbols.isAppearanceField( "width" ) );
    assertTrue( RequiredLineSymbols.isAppearanceField( "dash" ) );
    assertTrue( RequiredLineSymbols.isAppearanceField( "sketch_picker_category" ) );
  }

  @Test public void requiredLineEditor_hidesRoleFields()
  {
    assertFalse( RequiredLineSymbols.isAppearanceField( "th_name" ) );
    assertFalse( RequiredLineSymbols.isAppearanceField( "group" ) );
    assertFalse( RequiredLineSymbols.isAppearanceField( "level" ) );
    assertFalse( RequiredLineSymbols.isAppearanceField( "roundtrip" ) );
    assertFalse( RequiredLineSymbols.isAppearanceField( "closed" ) );
    assertFalse( RequiredLineSymbols.isAppearanceField( "style" ) );
  }
}
