package com.udacity.webcrawler.profiler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A method interceptor that checks whether {@link Method}s are annotated with the {@link Profiled}
 * annotation. If they are, the method interceptor records how long the method invocation took.
 */
final class ProfilingMethodInterceptor implements InvocationHandler {

  private final Clock clock;
  private final Object delegate;
  private final ProfilingState state;

  // TODO: You will need to add more instance fields and constructor arguments to this class.
  ProfilingMethodInterceptor(Clock clock, Object delegate, ProfilingState state) {
    this.clock = Objects.requireNonNull(clock);
    this.delegate = Objects.requireNonNull(delegate);
    this.state = Objects.requireNonNull(state);
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable{
    // TODO: This method interceptor should inspect the called method to see if it is a profiled
    //       method. For profiled methods, the interceptor should record the start time, then
    //       invoke the method using the object that is being profiled. Finally, for profiled
    //       methods, the interceptor should record how long the method call took, using the
    //       ProfilingState methods.
      if (method.getDeclaringClass() == Object.class) {
          // If the method is from Object class, just invoke it directly.
          return method.invoke(delegate, args);
      }
      boolean profiled = method.isAnnotationPresent(Profiled.class);
      Instant startTime = null;
      if (profiled) {
          startTime = clock.instant();
      }
      try {
          // Invoke the method on the delegate object.
          return method.invoke(delegate, args);
      } catch (InvocationTargetException e) {
          throw e.getCause();
      } finally {
          if (profiled) {
              Instant endTime = clock.instant();
              state.record(delegate.getClass(), method, Duration.between(startTime, endTime));
          }
      }
    // If the method is not profiled, just invoke it directly.
    // If the method is profiled, record the start time, invoke the method, and
    // then record the elapsed time using the ProfilingState.
    // If the method is not profiled, just invoke it directly.
    // Note: You may need to handle exceptions that can be thrown by the method invocation.
    //       If an exception is thrown, you may need to rethrow it or handle it appropriately.
    //       Make sure to return the result of the method invocation.

  }
}
