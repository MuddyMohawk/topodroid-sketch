package com.topodroid.TDX;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Small stroke icons, gesture glyphs and device pictograms with the mockup's viewports. */
final class ButtonShortcutIcon extends View
{
  static final int PAN = 8;
  static final int GESTURE_PRESS = 10;
  static final int BUTTON_PEN = 20;
  static final int FINGERS_TWO = 30;
  private final int mIcon;
  private final int mInk;
  private final Paint mPaint = new Paint( Paint.ANTI_ALIAS_FLAG );
  private final Path mPath = new Path();
  private final RectF mRect = new RectF();

  ButtonShortcutIcon( Context context, int icon, int ink )
  {
    super( context ); mIcon = icon; mInk = ink;
    mPaint.setStrokeCap( Paint.Cap.ROUND ); mPaint.setStrokeJoin( Paint.Join.ROUND );
    setImportantForAccessibility( IMPORTANT_FOR_ACCESSIBILITY_NO );
  }

  @Override protected void onDraw( Canvas canvas )
  {
    super.onDraw( canvas );
    float width = mIcon >= BUTTON_PEN ? 60 : mIcon >= GESTURE_PRESS ? 18 : 24;
    float height = mIcon >= BUTTON_PEN ? 40 : mIcon >= GESTURE_PRESS ? 8 : 24;
    float scale = Math.min( getWidth() / width, getHeight() / height );
    canvas.save(); canvas.translate( ( getWidth() - width * scale ) / 2, ( getHeight() - height * scale ) / 2 ); canvas.scale( scale, scale );
    mPaint.setColor( mInk ); mPaint.setStyle( Paint.Style.STROKE ); mPaint.setStrokeWidth( 1.9f ); mPath.reset();
    if ( mIcon >= FINGERS_TWO ) drawFingers( canvas );
    else if ( mIcon >= BUTTON_PEN ) drawButton( canvas );
    else if ( mIcon >= GESTURE_PRESS ) drawGesture( canvas );
    else drawAction( canvas );
    canvas.restore();
  }

  private void drawAction( Canvas canvas )
  {
    switch ( mIcon ) {
      case 1:
        line( 9, 14, 4, 9, 9, 4 );
        mPath.moveTo( 4, 9 ); mPath.lineTo( 15, 9 ); mRect.set( 10, 9, 20, 19 ); mPath.arcTo( mRect, -90, 180 ); mPath.lineTo( 11, 19 );
        break;
      case 2:
        line( 15, 14, 20, 9, 15, 4 );
        mPath.moveTo( 20, 9 ); mPath.lineTo( 9, 9 ); mRect.set( 4, 9, 14, 19 ); mPath.arcTo( mRect, -90, -180 ); mPath.lineTo( 13, 19 );
        break;
      case 6:
        mPath.moveTo( 4, 16.5f ); mPath.lineTo( 13.5f, 7 );
        mPath.cubicTo( 14.27f, 6.23f, 15.53f, 6.23f, 16.3f, 7 ); mPath.lineTo( 19, 9.7f );
        mPath.cubicTo( 19.77f, 10.47f, 19.77f, 11.73f, 19, 12.5f ); mPath.lineTo( 12, 19.5f ); mPath.lineTo( 7, 19.5f ); mPath.close();
        line( 9.5f, 11, 15, 16.5f ); line( 13, 19.5f, 20, 19.5f );
        break;
      case 3:
        mPaint.setStrokeWidth( 1.2f ); canvas.drawLine( 4, 6, 20, 6, mPaint );
        mPaint.setStrokeWidth( 2.4f ); canvas.drawLine( 4, 12, 20, 12, mPaint );
        mPaint.setStrokeWidth( 3.8f ); canvas.drawLine( 4, 18, 20, 18, mPaint ); return;
      case 5: line( 15, 5, 8, 12, 15, 19 ); break;
      case 0: line( 6, 6, 18, 18 ); break;
      case 7:
        canvas.drawCircle( 9, 10, 5, mPaint ); line( 13, 14, 19, 20 );
        line( 19, 3, 19, 13 ); line( 16, 6, 19, 3, 22, 6 ); line( 16, 10, 19, 13, 22, 10 ); break;
      case PAN:
        line( 12, 3, 12, 21 ); line( 3, 12, 21, 12 ); line( 9, 6, 12, 3, 15, 6 ); line( 9, 18, 12, 21, 15, 18 );
        line( 6, 9, 3, 12, 6, 15 ); line( 18, 9, 21, 12, 18, 15 ); break;
    }
    canvas.drawPath( mPath, mPaint );
  }

  private void drawGesture( Canvas canvas )
  {
    mPaint.setStyle( Paint.Style.FILL );
    switch ( mIcon - GESTURE_PRESS ) {
      case 0: canvas.drawCircle( 9, 4, 2.6f, mPaint ); break;
      case 1: canvas.drawCircle( 5, 4, 2.6f, mPaint ); canvas.drawCircle( 13, 4, 2.6f, mPaint ); break;
      case 2: mRect.set( 1.5f, 1.4f, 16.5f, 6.6f ); canvas.drawRoundRect( mRect, 2.6f, 2.6f, mPaint ); break;
      case 3:
        canvas.drawCircle( 4, 4, 2.6f, mPaint ); mPaint.setStyle( Paint.Style.STROKE ); mPaint.setStrokeWidth( 1.4f );
        line( 7.5f, 4, 15.5f, 4 ); line( 13, 1.5f, 15.8f, 4, 13, 6.5f ); canvas.drawPath( mPath, mPaint ); break;
    }
  }

  private void drawButton( Canvas canvas )
  {
    int outline = ToolbarVisualStyle.color( getContext(), R.color.button_shortcuts_pictogram_outline );
    mPaint.setColor( outline ); mPaint.setStrokeWidth( 2.5f );
    if ( mIcon == BUTTON_PEN ) {
      line( 8, 30, 14, 24, 48, 2 ); mPath.cubicTo( 50.4f, .4f, 53.6f, 3.6f, 52, 6 ); mPath.lineTo( 22, 36 ); mPath.close(); canvas.drawPath( mPath, mPaint );
      canvas.save(); canvas.rotate( -33, 30, 14 ); mPaint.setColor( mInk ); mPaint.setStyle( Paint.Style.FILL );
      mRect.set( 26, 11, 35, 16 ); canvas.drawRoundRect( mRect, 2.5f, 2.5f, mPaint ); canvas.restore();
    } else {
      mRect.set( 8, 3, 47, 37 ); canvas.drawRoundRect( mRect, 3, 3, mPaint ); mPaint.setStyle( Paint.Style.FILL );
      int[] positions = { 7, 15, 26 }; int selected = mIcon == BUTTON_PEN + 1 ? 2 : mIcon == BUTTON_PEN + 2 ? 0 : 1;
      for ( int i = 0; i < positions.length; ++i ) {
        mPaint.setColor( i != selected ? outline : selected == 2 ? ToolbarVisualStyle.color( getContext(), R.color.toolbar_danger ) : mInk );
        mRect.set( 47, positions[i], 52, positions[i] + 6.5f ); canvas.drawRoundRect( mRect, 1, 1, mPaint );
      }
    }
  }

  private void drawFingers( Canvas canvas )
  {
    mPaint.setStrokeWidth( 2.2f );
    switch ( mIcon - FINGERS_TWO ) {
      case 0: canvas.drawCircle( 22, 20, 5.5f, mPaint ); canvas.drawCircle( 38, 20, 5.5f, mPaint ); break;
      case 1: canvas.drawCircle( 16, 24, 5.5f, mPaint ); canvas.drawCircle( 30, 14, 5.5f, mPaint ); canvas.drawCircle( 44, 24, 5.5f, mPaint ); break;
      case 2: canvas.drawCircle( 10, 26, 5.5f, mPaint ); canvas.drawCircle( 22, 14, 5.5f, mPaint ); canvas.drawCircle( 38, 14, 5.5f, mPaint ); canvas.drawCircle( 50, 26, 5.5f, mPaint ); break;
    }
  }

  private void line( float... points )
  {
    mPath.moveTo( points[0], points[1] );
    for ( int i = 2; i < points.length; i += 2 ) mPath.lineTo( points[i], points[i + 1] );
  }
}
