/* ====================================================================
 * Copyright (c) 2012-2021 AbandonedCart.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. All advertising materials mentioning features or use of this
 *    software and redistributions of any form whatsoever
 *    must display the following acknowledgment:
 *    "This product includes software developed by AbandonedCart" unless
 *    otherwise displayed by tagged, public repository entries.
 *
 * 4. The names "8-Bit Dream", "TwistedUmbrella" and "AbandonedCart"
 *    must not be used in any form to endorse or promote products
 *    derived from this software without prior written permission. For
 *    written permission, please contact enderinexiledc@gmail.com
 *
 * 5. Products derived from this software may not be called "8-Bit Dream",
 *    "TwistedUmbrella" or "AbandonedCart" nor may these labels appear
 *    in their names without prior written permission of AbandonedCart.
 *
 * THIS SOFTWARE IS PROVIDED BY AbandonedCart ``AS IS'' AND ANY
 * EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE OpenSSL PROJECT OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * ====================================================================
 *
 * The license and distribution terms for any publicly available version or
 * derivative of this code cannot be changed.  i.e. this code cannot simply be
 * copied and put under another distribution license
 * [including the GNU Public License.] Content not subject to these terms is
 * subject to to the terms and conditions of the Apache License, Version 2.0.
 */

package com.eightbit.io;

import android.os.Build;
import android.util.Log;

import com.hiddenramblings.tagmo.BuildConfig;
import com.hiddenramblings.tagmo.TagMo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;

public class Debug {
/* TODO: Intercept all uncaught exceptions and print logcat file
 *     Thread.setDefaultUncaughtExceptionHandler((t, error) -> {
 *         StringWriter exception = new StringWriter();
 *         error.printStackTrace(new PrintWriter(exception));
 *         Error(error.getClass(), exception.toString());
 *         error.printStackTrace();
 *         try {
 *             Debug.generateLogcat(file);
 *         } catch (IOException ioe) {
 *             ioe.printStackTrace();
 *         }
 *         android.os.Process.killProcess(android.os.Process.myPid());
 *         System.exit(0);
 *     });
 */

    public static String TAG(Class<?> src) {
        return src.getSimpleName();
    }

    public static void Log(Class<?> src, String params) {
        if (!TagMo.getPrefs().disableDebug().get())
            Log.d(TAG(src), params);
    }

    public static void Log(Class<?> src, int resource) {
        Log(src, TagMo.getStringRes(resource));
    }

    public static void Log(Class<?> src, int resource, String params) {
        Log(src, TagMo.getStringRes(resource, params));
    }

    public static void Log(Exception ex) {
        if (ex.getStackTrace().length > 0) {
            StringWriter exception = new StringWriter();
            ex.printStackTrace(new PrintWriter(exception));
            Log(ex.getClass(), exception.toString());
        }
    }

    public static void Log(int resource, Exception ex) {
        Log.d(TAG(ex.getClass()), TagMo.getStringRes(resource), ex);
    }

    public static void Error(Class<?> src, String params) {
        Log.e(TAG(src), params);
    }

    public static void Error(Class<?> src, int resource) {
        Error(src, TagMo.getStringRes(resource));
    }

    public static void Error(Class<?> src, int resource, String params) {
        Error(src, TagMo.getStringRes(resource, params));
    }

    public static void Error(Exception ex) {
        if (ex.getStackTrace().length > 0) {
            StringWriter exception = new StringWriter();
            ex.printStackTrace(new PrintWriter(exception));
            Error(ex.getClass(), exception.toString());
        }
    }

    public static void Error(int resource, Exception ex) {
        Log.e(TAG(ex.getClass()), TagMo.getStringRes(resource), ex);
    }

    public static File generateLogcat(File file) throws IOException {
        final StringBuilder log = new StringBuilder();
        String separator = System.getProperty("line.separator");
        log.append(android.os.Build.MANUFACTURER);
        log.append(" ");
        log.append(android.os.Build.MODEL);
        log.append(separator);
        log.append("Android SDK ");
        log.append(Build.VERSION.SDK_INT);
        log.append(" (");
        log.append(Build.VERSION.RELEASE);
        log.append(")");
        log.append(separator);
        log.append("TagMo Version " + BuildConfig.VERSION_NAME);
        Process mLogcatProc = Runtime.getRuntime().exec(new String[]{
                "logcat", "-d",
                BuildConfig.APPLICATION_ID,
                "com.smartrac.nfc",
                "-t", "2048"
        });
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                mLogcatProc.getInputStream()));
        log.append(separator);
        log.append(separator);
        String line;
        while ((line = reader.readLine()) != null) {
            log.append(line);
            log.append(separator);
        }
        reader.close();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(log.toString().getBytes());
        }
        return file;
    }
}
