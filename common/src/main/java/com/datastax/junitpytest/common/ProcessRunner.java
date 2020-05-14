/*
 * Copyright DataStax, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datastax.junitpytest.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProcessRunner
{
    private final Process process;
    private final Thread shutdownHook;
    private final AtomicBoolean registered = new AtomicBoolean();
    private final long killTimeout;
    private final TimeUnit killTimeoutUnit;
    private OutputStream out = System.out;
    private OutputStream err = System.err;

    public ProcessRunner(Process process, long killTimeout, TimeUnit killTimeoutUnit)
    {
        this.process = process;
        this.shutdownHook = new Thread(this::killProcess, "Process Killer " + process);
        this.killTimeout = killTimeout;
        this.killTimeoutUnit = killTimeoutUnit;
    }

    private void killProcess()
    {
        process.destroy();
        try
        {
            awaitInternal(killTimeout, killTimeoutUnit,
                          null,
                          System.out,
                          System.err);
            process.destroyForcibly();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private void awaitInternal(long timeout, TimeUnit timeUnit,
                               InputStream stdin, OutputStream stdout, OutputStream stderr) throws IOException, TimeoutException
    {
        OutputStream in = process.getOutputStream();
        InputStream out = process.getInputStream();
        InputStream err = process.getErrorStream();

        long timeoutAt = System.nanoTime() + timeUnit.toNanos(timeout);

        byte[] buf = new byte[1024];
        while (true)
        {
            processIO(stdin, in, buf);
            boolean outHandled = processIO(out, stdout, buf);
            boolean errHandled = processIO(err, stderr, buf);
            if (!errHandled && !outHandled)
            {
                if (!process.isAlive())
                    return;

                if (timeoutAt - System.nanoTime() < 0L)
                    throw new TimeoutException("Timeout exceeded waiting for process");

                try
                {
                    Thread.sleep(1L);
                }
                catch (InterruptedException e)
                {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private boolean processIO(InputStream input, OutputStream output, byte[] buf) throws IOException
    {
        if (input == null)
            return false;

        try
        {
            int avail = input.available();
            if (avail <= 0)
                return false;

            int rd = input.read(buf, 0, Math.min(avail, buf.length));
            output.write(buf, 0, rd);
            return true;
        }
        catch (IOException e)
        {
            if ("Stream closed".equals(e.getMessage()))
                return false;
            throw e;
        }
    }

    public ProcessRunner withCapturedStdout(OutputStream out)
    {
        this.out = out;
        return this;
    }

    public ProcessRunner withCapturedStderr(OutputStream err)
    {
        this.err = err;
        return this;
    }

    public ProcessRunner await(long timeout, TimeUnit timeUnit)
    {
        register();
        try
        {
            awaitInternal(timeout, timeUnit, null, out, err);
            return this;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
            stop();
        }
    }

    public int getExitCode()
    {
        return process.exitValue();
    }

    public ProcessRunner register()
    {
        if (registered.compareAndSet(false, true))
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        return this;
    }

    public void stop()
    {
        if (registered.compareAndSet(true, false))
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        killProcess();
    }

    public void assertExitCode()
    {
        int exitCode = process.exitValue();
        if (exitCode != 0)
            throw new AssertionError("Expected exit code 0, but is " + exitCode);
    }
}
