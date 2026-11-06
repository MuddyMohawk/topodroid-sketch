package com.topodroid.qa;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Feature selection is independent of execution tier. Included in test sources only. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface TestFeatures {
  Feature[] value();
  enum Feature { SYMBOLS, DRAWING, DATA, EXCHANGE, DEVICES, RUNNER, VANILLA }
}
