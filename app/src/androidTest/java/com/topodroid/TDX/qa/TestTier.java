package com.topodroid.TDX.qa;

import java.lang.annotation.*;

/** Every discovered test must declare its purpose. Methods override their class. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface TestTier {
  Tier value();
  boolean baseline() default false;
  enum Tier { FAST, UI, SMOKE, DIAGNOSTIC, COMPAT }
}
