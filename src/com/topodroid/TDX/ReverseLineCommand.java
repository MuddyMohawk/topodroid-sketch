package com.topodroid.TDX;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;

/** Direction-only edit: the line itself remains in the drawing stack. */
final class ReverseLineCommand implements ICanvasCommand
{
  private final DrawingLinePath mLine;
  private final boolean mBefore;

  ReverseLineCommand( DrawingLinePath line )
  {
    mLine = line;
    mBefore = line.isReversed();
  }

  void undo() { mLine.setReversed( mBefore ); }
  void redo() { mLine.setReversed( ! mBefore ); }

  public int commandType() { return 3; }

  // Geometry is drawn and transformed by the line's own stack entry, once.
  public void draw( Canvas canvas, RectF bounds ) { }
  public void draw( Canvas canvas, Matrix matrix, RectF bounds ) { }
  public void draw( Canvas canvas, Matrix matrix, float scale, RectF bounds ) { }
  public void draw( Canvas canvas, Matrix matrix, float scale, RectF bounds, int xorColor ) { }
  public void flipXAxis( float z ) { }
  public void shiftPathBy( float x, float y ) { }
  public void scalePathBy( float z, Matrix matrix ) { }
  public void affineTransformPathBy( float[] transform, Matrix matrix ) { }
  public void computeBounds( RectF bounds, boolean exact ) { }
}
