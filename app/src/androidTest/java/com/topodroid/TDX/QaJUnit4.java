package com.topodroid.TDX;

import android.os.SystemClock;
import org.junit.runners.model.*;
import java.util.concurrent.atomic.AtomicLong;

/** Preserve AndroidJUnit4 behavior while accounting for @Before/@After separately. */
public final class QaJUnit4 extends androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner {
  public QaJUnit4(Class<?> type) throws InitializationError {super(type);}
  private Statement clock(Statement next,AtomicLong elapsed) {
    return new Statement(){@Override public void evaluate() throws Throwable {
      long start=SystemClock.elapsedRealtime();
      try{next.evaluate();}finally{elapsed.addAndGet(SystemClock.elapsedRealtime()-start);}
    }};
  }
  @Override protected Statement withBefores(FrameworkMethod method,Object target,Statement next) {
    AtomicLong nested=new AtomicLong();
    Statement original=super.withBefores(method,target,clock(next,nested));
    return new Statement(){@Override public void evaluate() throws Throwable {
      long start=SystemClock.elapsedRealtime();
      try{original.evaluate();}finally{QaFixtureRule.recordHook(true,Math.max(0,SystemClock.elapsedRealtime()-start-nested.get()));}
    }};
  }
  @Override protected Statement withAfters(FrameworkMethod method,Object target,Statement next) {
    AtomicLong nested=new AtomicLong();
    Statement original=super.withAfters(method,target,clock(next,nested));
    return new Statement(){@Override public void evaluate() throws Throwable {
      long start=SystemClock.elapsedRealtime();
      try{original.evaluate();}finally{QaFixtureRule.recordHook(false,Math.max(0,SystemClock.elapsedRealtime()-start-nested.get()));}
    }};
  }
}
