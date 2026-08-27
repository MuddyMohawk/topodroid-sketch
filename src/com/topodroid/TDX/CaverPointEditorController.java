/* @file CaverPointEditorController.java
 *
 * @brief Variant and survey-unit height controls for the caver point
 */
package com.topodroid.TDX;

import com.topodroid.prefs.TDSetting;
import com.topodroid.ui.SegmentedToggleBar;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.Locale;

final class CaverPointEditorController implements SpecialPointEditorController
{
  private final Context mContext;
  private final DrawingSemanticPointPath mPoint;

  private SegmentedToggleBar mVariant;
  private LinearLayout mMetricFields;
  private LinearLayout mImperialFields;
  private EditText mMeters;
  private EditText mFeet;
  private EditText mInches;
  private boolean mUseFeet;
  private double mOriginalHeightMeters;
  private String mInitialMetersText;
  private String mInitialFeetText;
  private String mInitialInchesText;
  private CaverPointState.Variant mDisplayedVariant;
  private double mPendingHeightMeters = Double.NaN;

  CaverPointEditorController( Context context, DrawingSemanticPointPath point )
  {
    mContext = context;
    mPoint = point;
  }

  @Override public void bind( LinearLayout container, EditText primary_text )
  {
    if ( container == null || primary_text == null
        || ! ( mPoint.specialState() instanceof CaverPointState ) ) return;
    primary_text.setVisibility( View.GONE );

    CaverPointState state = (CaverPointState)mPoint.specialState();
    mOriginalHeightMeters = state.heightMeters;
    View root = LayoutInflater.from( mContext ).inflate( R.layout.drawing_caver_editor, container, false );
    container.addView( root );

    mVariant = (SegmentedToggleBar)root.findViewById( R.id.caver_variant );
    mMetricFields = (LinearLayout)root.findViewById( R.id.caver_metric_fields );
    mImperialFields = (LinearLayout)root.findViewById( R.id.caver_imperial_fields );
    mMeters = (EditText)root.findViewById( R.id.caver_height_meters );
    mFeet = (EditText)root.findViewById( R.id.caver_height_feet );
    mInches = (EditText)root.findViewById( R.id.caver_height_inches );

    mVariant.setLabels( mContext.getString( R.string.caver_man ),
                        mContext.getString( R.string.caver_woman ),
                        mContext.getString( R.string.caver_banana_slug ) );
    mDisplayedVariant = state.variant;
    mVariant.setSelectedIndex( variantIndex( state.variant ) );

    mUseFeet = usesFeet();
    mMetricFields.setVisibility( mUseFeet ? View.GONE : View.VISIBLE );
    mImperialFields.setVisibility( mUseFeet ? View.VISIBLE : View.GONE );
    if ( mUseFeet ) {
      CaverHeightUnits.FeetInches value = CaverHeightUnits.fromMeters( state.heightMeters );
      mInitialFeetText = Integer.toString( value.feet );
      mInitialInchesText = formatDecimal( value.inches );
      mFeet.setText( mInitialFeetText );
      mInches.setText( mInitialInchesText );
    } else {
      mInitialMetersText = formatDecimal( state.heightMeters );
      mMeters.setText( mInitialMetersText );
    }
    mVariant.setOnSelectionChangedListener( new SegmentedToggleBar.OnSelectionChangedListener() {
      @Override public void onSelectionChanged( int index )
      {
        CaverPointState.Variant next = variantAt( index );
        updateVariantDefaultHeight( mDisplayedVariant, next );
        mDisplayedVariant = next;
      }
    } );
  }

  @Override public boolean canApply()
  {
    if ( mVariant == null || mMeters == null || mFeet == null || mInches == null ) return false;
    clearErrors();
    if ( mUseFeet ) return validateImperial();
    Double meters = parseDecimal( mMeters.getText().toString() );
    if ( meters == null || ! Double.isFinite( meters ) || meters <= 0.0 ) {
      mMeters.setError( mContext.getString( R.string.caver_height_invalid ) );
      mMeters.requestFocus();
      return false;
    }
    mPendingHeightMeters = mMeters.getText().toString().equals( mInitialMetersText )
      ? mOriginalHeightMeters : meters;
    return true;
  }

  @Override public void apply()
  {
    if ( ! Double.isFinite( mPendingHeightMeters ) || mPendingHeightMeters <= 0.0 ) return;
    CaverPointState.Variant variant = variantAt( mVariant.selectedIndex() );
    mPoint.setSpecialState( new CaverPointState( variant, mPendingHeightMeters ), true );
  }

  private void updateVariantDefaultHeight( CaverPointState.Variant previous,
                                           CaverPointState.Variant next )
  {
    Double current = displayedHeightMeters();
    if ( current == null || Math.abs( current - CaverPointState.defaultHeightMeters( previous ) ) > 1.0e-8 ) {
      return;
    }
    setDisplayedHeight( CaverPointState.defaultHeightMeters( next ) );
  }

  private Double displayedHeightMeters()
  {
    if ( ! mUseFeet ) return parseDecimal( mMeters.getText().toString() );
    try {
      int feet = Integer.parseInt( mFeet.getText().toString().trim() );
      Double inches = parseDecimal( mInches.getText().toString() );
      return inches == null ? null : CaverHeightUnits.toMeters( feet, inches );
    } catch ( NumberFormatException e ) {
      return null;
    }
  }

  private void setDisplayedHeight( double meters )
  {
    if ( mUseFeet ) {
      CaverHeightUnits.FeetInches value = CaverHeightUnits.fromMeters( meters );
      mFeet.setText( Integer.toString( value.feet ) );
      mInches.setText( formatDecimal( value.inches ) );
    } else {
      mMeters.setText( formatDecimal( meters ) );
    }
  }

  private static int variantIndex( CaverPointState.Variant variant )
  {
    if ( variant == CaverPointState.Variant.WOMAN ) return 1;
    if ( variant == CaverPointState.Variant.BANANA_SLUG ) return 2;
    return 0;
  }

  private static CaverPointState.Variant variantAt( int index )
  {
    if ( index == 1 ) return CaverPointState.Variant.WOMAN;
    if ( index == 2 ) return CaverPointState.Variant.BANANA_SLUG;
    return CaverPointState.Variant.MAN;
  }

  private boolean validateImperial()
  {
    int feet;
    try {
      String value = mFeet.getText().toString().trim();
      if ( value.length() == 0 ) throw new NumberFormatException();
      feet = Integer.parseInt( value );
    } catch ( NumberFormatException e ) {
      mFeet.setError( mContext.getString( R.string.caver_feet_invalid ) );
      mFeet.requestFocus();
      return false;
    }
    Double inches = parseDecimal( mInches.getText().toString() );
    if ( feet < 0 ) {
      mFeet.setError( mContext.getString( R.string.caver_feet_invalid ) );
      mFeet.requestFocus();
      return false;
    }
    if ( inches == null || ! Double.isFinite( inches ) || inches < 0.0
        || inches >= CaverHeightUnits.INCHES_PER_FOOT ) {
      mInches.setError( mContext.getString( R.string.caver_inches_invalid ) );
      mInches.requestFocus();
      return false;
    }
    double meters = CaverHeightUnits.toMeters( feet, inches );
    if ( ! Double.isFinite( meters ) || meters <= 0.0 ) {
      mFeet.setError( mContext.getString( R.string.caver_height_invalid ) );
      mFeet.requestFocus();
      return false;
    }
    boolean unchanged = mFeet.getText().toString().equals( mInitialFeetText )
      && mInches.getText().toString().equals( mInitialInchesText );
    mPendingHeightMeters = unchanged ? mOriginalHeightMeters : meters;
    return true;
  }

  private void clearErrors()
  {
    mMeters.setError( null );
    mFeet.setError( null );
    mInches.setError( null );
    mPendingHeightMeters = Double.NaN;
  }

  static boolean usesFeet()
  {
    return "ft".equalsIgnoreCase( TDSetting.mUnitLengthStr )
        || "feet".equalsIgnoreCase( TDSetting.mUnitLengthStr );
  }

  static String formatDecimal( double value )
  {
    DecimalFormat format = new DecimalFormat( "0.######", DecimalFormatSymbols.getInstance() );
    format.setGroupingUsed( false );
    return format.format( value );
  }

  static Double parseDecimal( String text )
  {
    if ( text == null ) return null;
    String value = text.trim();
    if ( value.length() == 0 ) return null;
    NumberFormat format = NumberFormat.getNumberInstance( Locale.getDefault() );
    format.setGroupingUsed( false );
    ParsePosition position = new ParsePosition( 0 );
    Number parsed = format.parse( value, position );
    if ( parsed != null && position.getIndex() == value.length() ) return parsed.doubleValue();
    // Existing TopoDroid numeric fields and serialized examples use a period;
    // keep accepting it even when the current locale uses a decimal comma.
    try {
      return Double.parseDouble( value );
    } catch ( NumberFormatException e ) {
      return null;
    }
  }
}
