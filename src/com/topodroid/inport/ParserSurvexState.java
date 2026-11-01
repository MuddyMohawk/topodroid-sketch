/* @file ParserSurvexState.java
 *
 * @author marco corvi
 * @date jan 2019
 *
 * @brief TopoDroid state for the Survex parser
 *
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * ----------------------------------------------------------
 */
package com.topodroid.inport;

import java.util.Arrays;

class ParserSurvexState
{
  static final int LENGTH = 0;
  static final int BEARING = 1;
  static final int CLINO = 2;
  static final int BACK_LENGTH = 3;
  static final int BACK_BEARING = 4;
  static final int BACK_CLINO = 5;

  ParserSurvexState mParent;

  String mName; // *begin name

  // Zero errors include scale and units; backsight instruments are independent.
  final float[] mUnit = new float[6];
  final float[] mScale = new float[6];
  final float[] mZero = new float[6];

  // Column order is scoped just like units and calibration.
  int jFrom, jTo, jLength, jCompass, jClino;
  int jBackLength, jBackCompass, jBackClino;
  int jLeft, jRight, jUp, jDown, jStation;
  float mDeclination;
  boolean mDuplicate;
  boolean mSurface;
  boolean mSplay;
  int mSurveyLevel;
  int mCase;
  boolean interleaved; // interleaved data

  int data_type;

  ParserSurvexState( String name )
  {
    mParent = null;
    mName   = name;
    setUnitsDefault();
    setCalibrateDefault();
    setDataDefault();

    mDeclination= 0.0f;
    mDuplicate = false;
    mSurface   = false;
    mSplay     = false;
    mSurveyLevel = 0;
    mCase = ParserUtil.CASE_LOWER;
  }

  void setDataDefault()
  {
    data_type = ParserUtil.DATA_NORMAL;
    interleaved = false;
    clearDataIndices();
    jFrom = 0;
    jTo = 1;
    jLength = 2;
    jCompass = 3;
    jClino = 4;
  }

  void clearDataIndices()
  {
    jFrom = jTo = jLength = jCompass = jClino = -1;
    jBackLength = jBackCompass = jBackClino = -1;
    jLeft = jRight = jUp = jDown = jStation = -1;
  }

  void setCalibrateDefault()
  {
    Arrays.fill( mZero, 0.0f );
    Arrays.fill( mScale, 1.0f );
  }

  void setUnitsDefault()
  {
    Arrays.fill( mUnit, 1.0f );
  }

  ParserSurvexState( ParserSurvexState state, String name )
  {
    mParent  = state;
    mName    = name;

    System.arraycopy( state.mUnit, 0, mUnit, 0, mUnit.length );
    System.arraycopy( state.mScale, 0, mScale, 0, mScale.length );
    System.arraycopy( state.mZero, 0, mZero, 0, mZero.length );
    jFrom = state.jFrom;
    jTo = state.jTo;
    jLength = state.jLength;
    jCompass = state.jCompass;
    jClino = state.jClino;
    jBackLength = state.jBackLength;
    jBackCompass = state.jBackCompass;
    jBackClino = state.jBackClino;
    jLeft = state.jLeft;
    jRight = state.jRight;
    jUp = state.jUp;
    jDown = state.jDown;
    jStation = state.jStation;

    mDeclination= state.mDeclination;
    mDuplicate = state.mDuplicate;
    mSurface   = state.mSurface;
    mSplay     = state.mSplay;
    data_type = state.data_type;
    interleaved = state.interleaved;

    mCase = state.mCase;
    mSurveyLevel = state.mSurveyLevel + 1;
  }

}
