/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.openejb.concurrencyutilities.ee.impl;

import org.apache.openejb.concurrencyutilities.ee.future.CUScheduleFuture;
import org.apache.openejb.concurrencyutilities.ee.task.CUCallable;
import org.apache.openejb.concurrencyutilities.ee.task.CURunnable;
import org.apache.openejb.concurrencyutilities.ee.task.TriggerCallable;
import org.apache.openejb.concurrencyutilities.ee.task.TriggerRunnable;
import org.apache.openejb.util.Duration;

import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.enterprise.concurrent.ManagedTask;
import javax.enterprise.concurrent.Trigger;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ManagedScheduledExecutorServiceImpl extends ManagedExecutorServiceImpl implements ManagedScheduledExecutorService {
    private final ScheduledExecutorService delegate;

    public ManagedScheduledExecutorServiceImpl(final ScheduledExecutorService delegate, final Duration waitAtShutdown) {
        super(delegate, waitAtShutdown);
        this.delegate = delegate;
    }


    @Override
    public ScheduledFuture<?> schedule(final Runnable runnable, final Trigger trigger) {
        final Date taskScheduledTime = new Date();
        final TriggerRunnable wrapper = new TriggerRunnable(this, runnable, new CURunnable(runnable), trigger, taskScheduledTime, getTaskId(runnable));
        final ScheduledFuture<?> future = delegate.schedule(new Runnable() {
            @Override
            public void run() {
                wrapper.run();
                final ScheduledFuture<?> future = schedule(this, trigger.getNextRunTime(wrapper.getLastExecution(), taskScheduledTime).getTime() - nowMs(), TimeUnit.MILLISECONDS);
                wrapper.taskSubmitted(future, ManagedScheduledExecutorServiceImpl.this, runnable);
            }
        }, trigger.getNextRunTime(wrapper.getLastExecution(), taskScheduledTime).getTime() - nowMs(), TimeUnit.MILLISECONDS);
        wrapper.taskSubmitted(future, this, runnable);
        return new CUScheduleFuture<Object>(ScheduledFuture.class.cast(future), wrapper);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(final Callable<V> vCallable, final Trigger trigger) {
        final Date taskScheduledTime = new Date();
        final TriggerCallable<V> wrapper = new TriggerCallable<V>(this, vCallable, new CUCallable<V>(vCallable), trigger, taskScheduledTime, getTaskId(vCallable));
        final ScheduledFuture<V> future = delegate.schedule(new Callable<V>() {
            @Override
            public V call() throws Exception {
                final V result = wrapper.call();
                final ScheduledFuture<V> future = schedule(this, trigger.getNextRunTime(wrapper.getLastExecution(), taskScheduledTime).getTime() - nowMs(), TimeUnit.MILLISECONDS);
                wrapper.taskSubmitted(future, ManagedScheduledExecutorServiceImpl.this, vCallable);
                return result;
            }
        }, trigger.getNextRunTime(wrapper.getLastExecution(), taskScheduledTime).getTime() - nowMs(), TimeUnit.MILLISECONDS);
        wrapper.taskSubmitted(future, this, vCallable);
        return new CUScheduleFuture<V>(future, wrapper);
    }

    @Override
    public ScheduledFuture<?> schedule(final Runnable command, final long delay, final TimeUnit unit) {
        final CURunnable wrapper = new CURunnable(command);
        final ScheduledFuture<?> future = delegate.schedule(wrapper, delay, unit);
        wrapper.taskSubmitted(future, this, command);
        return new CUScheduleFuture<Object>(ScheduledFuture.class.cast(future), wrapper);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(final Callable<V> callable, final long delay, final TimeUnit unit) {
        final CUCallable<V> wrapper = new CUCallable<V>(callable);
        final ScheduledFuture<V> future = delegate.schedule(wrapper, delay, unit);
        wrapper.taskSubmitted(future, this, callable);
        return new CUScheduleFuture<V>(future, wrapper);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(final Runnable command, final long initialDelay, final long period, final TimeUnit unit) {
        final CURunnable wrapper = new CURunnable(command);
        final ScheduledFuture<?> future = delegate.scheduleAtFixedRate(wrapper, initialDelay, period, unit);
        wrapper.taskSubmitted(future, this, command);
        return new CUScheduleFuture<Object>(ScheduledFuture.class.cast(future), wrapper);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(final Runnable command, final long initialDelay, final long delay, final TimeUnit unit) {
        final CURunnable wrapper = new CURunnable(command);
        final ScheduledFuture<?> future = delegate.scheduleWithFixedDelay(wrapper, initialDelay, delay, unit);
        wrapper.taskSubmitted(future, this, command);
        return new CUScheduleFuture<Object>(ScheduledFuture.class.cast(future), wrapper);
    }

    public static long nowMs() {
        return System.currentTimeMillis(); // need to be comparable to java.util.Date
    }

    private static String getTaskId(final Object runnable) {
        if (ManagedTask.class.isInstance(runnable)) {
            return ManagedTask.class.cast(runnable).getExecutionProperties().get(ManagedTask.IDENTITY_NAME);
        }
        return null;
    }
}
