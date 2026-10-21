package android.test;

public @interface RepetitiveTest {
    int numIterations() default 1;
}
