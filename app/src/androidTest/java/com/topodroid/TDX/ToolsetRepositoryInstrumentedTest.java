package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.topodroid.types.SymbolType;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith( AndroidJUnit4.class )
public class ToolsetRepositoryInstrumentedTest
{
  private DataHelper mData;

  @Before
  public void setUp()
  {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    TDInstance.setContext( context.getApplicationContext() );
    if ( TopoDroidApp.mData == null ) TopoDroidApp.mData = new DataHelper( context );
    mData = TopoDroidApp.mData;
  }

  @Test
  public void profilesAreGlobalSelectionsArePerSurveyAndDeletedSelectionsFallBack()
  {
    ToolsetProfile base = ToolsetRepository.profile( mData, ToolsetProfile.DEFAULT_ID );
    String name = "Repository test " + System.nanoTime();
    ToolsetProfile duplicate = ToolsetRepository.duplicate( mData, base, name );
    assertNotNull( duplicate );
    duplicate.mRows[2][3] = new ToolsetProfile.Slot( SymbolType.LINE, SymbolLibrary.WALL );
    duplicate.moveRowToCanvasEnd( 2 );
    assertTrue( ToolsetRepository.saveProfile( mData, duplicate ) );

    long firstSurvey = 982451653L;
    long secondSurvey = 982451707L;
    assertTrue( ToolsetRepository.selectProfile( mData, firstSurvey, duplicate.mId ) );
    assertEquals( duplicate.mId, ToolsetRepository.activeProfileId( mData, firstSurvey ) );
    assertEquals( ToolsetProfile.DEFAULT_ID, ToolsetRepository.activeProfileId( mData, secondSurvey ) );
    assertEquals( SymbolLibrary.WALL, ToolsetRepository.activeProfile( mData, firstSurvey ).mRows[2][3].mFullThName );

    assertFalse( ToolsetRepository.deleteProfile( mData, ToolsetProfile.DEFAULT_ID ) );
    assertTrue( ToolsetRepository.deleteProfile( mData, duplicate.mId ) );
    assertEquals( ToolsetProfile.DEFAULT_ID, ToolsetRepository.activeProfileId( mData, firstSurvey ) );
  }
}
