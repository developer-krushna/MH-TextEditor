/*
 * MH-TextEditor - An Advanced and optimized TextEditor for android
 * Copyright 2025-26, developer-krushna
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *     * Neither the name of developer-krushna nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.


 *     Please contact Krushna by email modder-hub@zohomail.in if you need
 *     additional information or have any questions
 */
package modder.hub.editor.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Patterns;

/**
 * Utility class to check if a string is a link and to open links in a browser.
 */
public class LinkChecker {

    /**
     * Checks if the given text is a valid web URL.
     *
     * @param text The text to check.
     * @return true if the text matches the web URL pattern, false otherwise.
     */
    public static boolean isLink(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        return Patterns.WEB_URL.matcher(text).matches();
    }

    /**
     * Opens the given URL in the system's default web browser.
     *
     * @param context The context used to start the activity.
     * @param url     The URL to open.
     */
    public static void openLinkInBrowser(Context context, String url) {
        if (url == null || url.trim().isEmpty()) {
            return;
        }

        String formattedUrl = url.trim();
        if (!formattedUrl.startsWith("http://") && !formattedUrl.startsWith("https://")) {
            formattedUrl = "http://" + formattedUrl;
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(formattedUrl));
            // Add NEW_TASK flag if context is not an Activity
            if (!(context instanceof android.app.Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
        } catch (Exception e) {
            // Log the error or show a toast if necessary
        }
    }
}
