package com.topodroid.TDX;

/** Serializes finite device operations and a single pending survey switch. */
final class DeviceSwitchState
{
  private int mOperations;
  private boolean mSwitching;
  private String mPending;

  synchronized boolean beginOperation()
  {
    if ( mSwitching || mPending != null || mOperations != 0 ) return false;
    ++mOperations;
    return true;
  }

  synchronized void endOperation()
  {
    if ( mOperations <= 0 ) throw new IllegalStateException( "No device operation to finish" );
    --mOperations;
  }

  synchronized boolean request( String current, String target )
  {
    if ( target == null || target.equals( current ) || mSwitching ) return false;
    mPending = target;
    return true;
  }

  synchronized String takePending()
  {
    if ( mOperations != 0 || mSwitching || mPending == null ) return null;
    String target = mPending;
    mPending = null;
    mSwitching = true;
    return target;
  }

  synchronized void finishSwitch() { mSwitching = false; }
  synchronized void cancelPending() { mPending = null; }
  synchronized boolean isSwitching() { return mSwitching; }
  synchronized boolean isBusy() { return mOperations != 0 || mSwitching || mPending != null; }
}
