/* @file BeddingProjection.java
 *
 * @brief Immutable projection metadata saved with a bedding point
 */
package com.topodroid.TDX;

final class BeddingProjection
{
  final BeddingAttitudePointState.ViewKind viewKind;
  final boolean traceValid;
  final double canvasTraceAngleDegrees;
  final double apparentDipDegrees;
  final double extendedReferenceBearingDegrees;
  final double extendedExtendSign;
  final boolean extendedReferenceAmbiguous;

  BeddingProjection( BeddingAttitudePointState.ViewKind view_kind, boolean trace_valid,
                     double trace_angle, double apparent_dip, double reference_bearing,
                     double extend_sign, boolean reference_ambiguous )
  {
    viewKind = view_kind;
    traceValid = trace_valid;
    canvasTraceAngleDegrees = trace_angle;
    apparentDipDegrees = apparent_dip;
    extendedReferenceBearingDegrees = reference_bearing;
    extendedExtendSign = extend_sign;
    extendedReferenceAmbiguous = reference_ambiguous;
  }

  static BeddingProjection plan()
  {
    return new BeddingProjection( BeddingAttitudePointState.ViewKind.PLAN, false,
      Double.NaN, Double.NaN, Double.NaN, Double.NaN, false );
  }

  static BeddingProjection unsupported()
  {
    return new BeddingProjection( BeddingAttitudePointState.ViewKind.UNSUPPORTED, false,
      Double.NaN, Double.NaN, Double.NaN, Double.NaN, false );
  }
}
