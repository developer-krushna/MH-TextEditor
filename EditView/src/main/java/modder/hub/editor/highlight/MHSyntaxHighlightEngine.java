/*
* MH-TextEditor - An Advanced and optimized TextEditor for android
* Copyright 2025, developer-krushna
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

package modder.hub.editor.highlight;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.text.TextPaint;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Author : Krushna Chandra Maharna(@developer-krushna)
 *
 * <p>This is a regex based Syntax highlighter .. Which may cause severe CPU usage during startup
 * time but overall ok for highlighting code.
 *
 * <p>There is no feature for commenting multi line based (Which currently iam learning)
 *
 * <p>Some incorrect highlight may occur.
 */

/**
 * MHSyntaxHighlightEngine ------------------------ A syntax highlighting engine that parses and
 * colors text using regex-based rules.
 *
 * <p>It supports multiple languages (via JSON rule files) and color themes (day/night). The engine
 * can draw highlighted text line-by-line on a Canvas, caching results for speed.
 */
public class MHSyntaxHighlightEngine {

    private static final String TAG = "MHSyntaxHighlightEngine";

    /**
     * Rule: Represents a single syntax highlighting rule. Each rule has a regex pattern, type
     * (style name), optional group styles, and priority.
     */
    public static class Rule {
        public String type; // Style/type name (maps to color)
        public Pattern pattern; // Regex pattern to match
        public Map<Integer, String> groupStyles; // Map of capture group → style
        public int priority; // Rule application priority
    }

    /** Candidate: Represents a potential styled text region before overlap filtering. */
    private static class Candidate {
        int start, end; // Character range
        String style; // Style name
        int priority; // Rule priority
        int length; // Computed length for sorting

        Candidate(int s, int e, String st, int p) {
            start = s;
            end = e;
            style = st;
            priority = p;
            length = e - s;
        }
    }

    /** Token: Represents a final colored text span after resolving overlaps. */
    private static class Token {
        int start, end; // Character range
        int color; // Color value

        Token(int s, int e, int c) {
            start = s;
            end = e;
            color = c;
        }
    }

    // Mapping of style names → colors (loaded from colors.json)
    private final Map<String, Integer> colors = new HashMap<String, Integer>();

    // All syntax rules loaded from the language JSON file
    private final List<Rule> rules = new ArrayList<Rule>();

    // Paint object used for text rendering
    private final TextPaint paint;

    // True if using dark mode (night colors)
    private final boolean darkMode;

    /**
     * LRU cache for per-line tokenized data. Key: line index Value: list of tokens for that line
     */
    private final LinkedHashMap<Integer, List<Token>> lineCache = new LinkedHashMap<
            Integer, List<Token>>(512, 0.75f, true) {
        private static final int MAX = 1000; // Maximum cached lines

        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, List<Token>> eldest) {
            return size() > MAX;
        }
    };

    /** Constructor: Initializes the engine with color and language configurations. */
    public MHSyntaxHighlightEngine(Context ctx, TextPaint textPaint, String languageAssetFile, boolean darkMode) {
        this.paint = textPaint;
        this.darkMode = darkMode;
        try {
            initColors(ctx);
            initLanguage(ctx, languageAssetFile);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /** Loads color definitions from assets/colors.json */
    private void initColors(Context ctx) throws Exception {
        String s = loadAsset(ctx, "colors.json");
        JSONObject jo = new JSONObject(s);
        Iterator<String> it = jo.keys();
        while (it.hasNext()) {
            String k = it.next();
            JSONObject col = jo.getJSONObject(k);
            // Choose "day" or "night" color based on darkMode flag
            String hex = darkMode ? col.getString("night") : col.getString("day");
            colors.put(k, Color.parseColor(hex));
        }
        // Ensure default color exists
        if (!colors.containsKey("default")) colors.put("default", Color.BLACK);
    }

    /** Loads language-specific highlighting rules from assets/langFile (JSON) */
    private void initLanguage(Context ctx, String langFile) throws Exception {
        String s = loadAsset(ctx, langFile);
        JSONObject lang = new JSONObject(s);

        // Predefined regex snippets used in rules
        Map<String, String> defines = new HashMap<String, String>();
        if (lang.has("defines")) {
            JSONObject def = lang.getJSONObject("defines");
            Iterator<String> dik = def.keys();
            while (dik.hasNext()) {
                String dn = dik.next();
                Object dv = def.get(dn);
                if (dv instanceof String) {
                    defines.put(dn, (String) dv);
                } else if (dv instanceof JSONObject) {
                    JSONObject dobj = (JSONObject) dv;
                    if (dobj.has("regex")) defines.put(dn, dobj.getString("regex"));
                }
            }
        }

        if (!lang.has("rules")) return;
        JSONArray arr = lang.getJSONArray("rules");

        // Parse each rule
        for (int i = 0; i < arr.length(); i++) {
            JSONObject rj = arr.getJSONObject(i);

            // Handle "include" rules that reference defines
            if (rj.has("include")) {
                String inc = rj.getString("include");
                if (defines.containsKey(inc)) {
                    JSONObject nr = new JSONObject();
                    nr.put("regex", defines.get(inc));
                    nr.put("type", rj.optString("type", "default"));
                    rj = nr;
                }
            }

            // Handle keyword arrays → combined regex
            if (rj.has("keywords")) {
                JSONArray kw = rj.getJSONArray("keywords");
                ArrayList<String> list = new ArrayList<String>();
                for (int k = 0; k < kw.length(); k++) list.add(kw.getString(k));
                // Sort keywords longest-first to avoid partial matches
                Collections.sort(list, new Comparator<String>() {
                    public int compare(String a, String b) {
                        return b.length() - a.length();
                    }
                });
                // Build regex for keywords
                StringBuilder sb = new StringBuilder();
                for (int k = 0; k < list.size(); k++) {
                    if (k > 0) sb.append("|");
                    sb.append(Pattern.quote(list.get(k)));
                }
                // Pattern ensures keywords are bounded by whitespace or brackets
                String patternStr = "(?:(?<=^)|(?<=\\s)|(?<=\\())(?:(?:" + sb.toString() + "))(?![A-Za-z0-9_/$\\.])";
                Rule r = new Rule();
                r.type = rj.optString("type", "keyword");
                r.pattern = Pattern.compile(patternStr, Pattern.MULTILINE);
                r.groupStyles = null;
                r.priority = i;
                rules.add(r);
                continue;
            }

            // Regular regex-based rules
            if (!rj.has("regex")) continue;
            Rule r = new Rule();
            r.type = rj.optString("type", null);
            r.pattern = Pattern.compile(rj.getString("regex"), Pattern.MULTILINE);
            r.priority = i;

            // Optional group-specific styles
            if (rj.has("groupStyles")) {
                r.groupStyles = new HashMap<Integer, String>();
                JSONObject gs = rj.getJSONObject("groupStyles");
                Iterator<String> gk = gs.keys();
                while (gk.hasNext()) {
                    String key = gk.next();
                    try {
                        int gi = Integer.parseInt(key);
                        r.groupStyles.put(gi, gs.getString(key));
                    } catch (Exception ignore) {
                    }
                }
            } else {
                r.groupStyles = null;
            }
            rules.add(r);
        }
    }

    /** Reads a file from the assets directory as UTF-8 text. */
    private String loadAsset(Context ctx, String name) throws Exception {
        InputStream is = ctx.getAssets().open(name);
        byte[] b = new byte[is.available()];
        is.read(b);
        is.close();
        return new String(b, StandardCharsets.UTF_8);
    }

    /** Clears the entire token cache */
    public void clearCache() {
        synchronized (lineCache) {
            lineCache.clear();
        }
    }

    /** Draws a single line of highlighted text on the canvas. */
    public void drawLine(Canvas canvas, String line, int lineIndex, int x, int y) {
        if (line == null) return;
        List<Token> tokens;
        synchronized (lineCache) {
            tokens = lineCache.get(lineIndex);
        }
        // Generate tokens if not cached
        if (tokens == null) {
            tokens = tokenizeLine(line);
            synchronized (lineCache) {
                lineCache.put(lineIndex, tokens);
            }
        }
        renderTokens(canvas, line, tokens, x, y);
    }

    /**
     * Tokenizes a line into styled segments according to the rules. Resolves overlaps and converts
     * candidates into tokens.
     */
    private List<Token> tokenizeLine(String line) {
        ArrayList<Candidate> all = new ArrayList<Candidate>();
        int L = (line == null) ? 0 : line.length();
        if (L == 0) return new ArrayList<Token>();

        // Apply each rule to find matches
        for (int ri = 0; ri < rules.size(); ri++) {
            Rule r = rules.get(ri);
            Matcher m = r.pattern.matcher(line);
            while (m.find()) {
                int ms = m.start();
                int me = m.end();
                if (ms < 0 || me <= ms) continue;

                // Handle grouped styles
                if (r.groupStyles != null && !r.groupStyles.isEmpty()) {
                    Iterator<Map.Entry<Integer, String>> it = r.groupStyles.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<Integer, String> ge = it.next();
                        int gi = ge.getKey();
                        String style = ge.getValue();
                        try {
                            int gs = m.start(gi);
                            int gei = m.end(gi);
                            if (gs < 0 || gei <= gs) continue;
                            if (gs >= L) continue;
                            if (gei > L) gei = L;
                            all.add(new Candidate(gs, gei, style, r.priority));
                        } catch (Exception ex) {
                            // ignore missing group
                        }
                    }
                } else if (r.type != null) {
                    int s = ms;
                    int e = me;
                    if (s < 0) s = 0;
                    if (e > L) e = L;
                    if (s >= e) continue;
                    all.add(new Candidate(s, e, r.type, r.priority));
                }
            }
        }

        // Sort candidates by priority, then position
        Collections.sort(all, new Comparator<Candidate>() {
            public int compare(Candidate a, Candidate b) {
                if (a.priority != b.priority) return a.priority - b.priority;
                if (a.start != b.start) return a.start - b.start;
                return b.length - a.length;
            }
        });

        // Choose non-overlapping tokens
        boolean[] taken = new boolean[L];
        ArrayList<Token> chosen = new ArrayList<Token>();
        for (int i = 0; i < all.size(); i++) {
            Candidate c = all.get(i);
            if (c.start < 0) c.start = 0;
            if (c.end > L) c.end = L;
            if (c.start >= c.end) continue;
            boolean overlap = false;
            for (int p = c.start; p < c.end; p++) {
                if (taken[p]) {
                    overlap = true;
                    break;
                }
            }
            if (overlap) continue;

            // Resolve color
            Integer col = colors.get(c.style);
            if (col == null) col = colors.get("default");

            // Add final token
            chosen.add(new Token(c.start, c.end, col.intValue()));

            // Mark characters as used
            for (int p = c.start; p < c.end; p++) taken[p] = true;
        }

        // Sort final tokens by position
        Collections.sort(chosen, new Comparator<Token>() {
            public int compare(Token a, Token b) {
                return a.start - b.start;
            }
        });

        return chosen;
    }

    /** Draws text segments with their respective colors. */
    private void renderTokens(Canvas canvas, String line, List<Token> tokens, int x, int y) {
        int cur = 0;
        float cx = x;
        int L = (line == null) ? 0 : line.length();

        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.start < 0) t.start = 0;
            if (t.end > L) t.end = L;
            if (t.start >= t.end) continue;

            // Draw text before token (default color)
            if (cur < t.start) {
                paint.setColor(colors.containsKey("default") ? colors.get("default") : Color.BLACK);
                String sub = safeSubstring(line, cur, t.start);
                canvas.drawText(sub, cx, y, paint);
                cx += paint.measureText(sub);
            }

            // Draw token text (colored)
            paint.setColor(t.color);
            String sub = safeSubstring(line, t.start, t.end);
            canvas.drawText(sub, cx, y, paint);
            cx += paint.measureText(sub);
            cur = t.end;
        }

        // Draw remaining text (if any)
        if (cur < L) {
            paint.setColor(colors.containsKey("default") ? colors.get("default") : Color.BLACK);
            String sub = safeSubstring(line, cur, L);
            canvas.drawText(sub, cx, y, paint);
        }
    }

    /** Removes a specific line’s cache entry */
    public void clearLineCache(int lineIndex) {
        synchronized (lineCache) {
            lineCache.remove(lineIndex);
        }
    }

    /** Safe substring extraction that prevents IndexOutOfBounds errors */
    private String safeSubstring(String s, int start, int end) {
        if (s == null) return "";
        int len = s.length();
        if (start < 0) start = 0;
        if (end > len) end = len;
        if (start >= end) return "";
        return s.substring(start, end);
    }

    /** Debug helper: returns a list of token descriptions for inspection */
    public List<String> debugTokenizeLine(String line) {
        ArrayList<String> out = new ArrayList<String>();
        ArrayList<Candidate> all = new ArrayList<Candidate>();
        int L = (line == null) ? 0 : line.length();
        if (L == 0) return out;

        // Same logic as tokenizeLine(), but returns descriptive strings
        for (int ri = 0; ri < rules.size(); ri++) {
            Rule r = rules.get(ri);
            Matcher m = r.pattern.matcher(line);
            while (m.find()) {
                int ms = m.start();
                int me = m.end();
                if (ms < 0 || me <= ms) continue;
                if (r.groupStyles != null && !r.groupStyles.isEmpty()) {
                    Iterator<Map.Entry<Integer, String>> it = r.groupStyles.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<Integer, String> ge = it.next();
                        int gi = ge.getKey();
                        String style = ge.getValue();
                        try {
                            int gs = m.start(gi);
                            int gei = m.end(gi);
                            if (gs < 0 || gei <= gs) continue;
                            if (gs >= L) continue;
                            if (gei > L) gei = L;
                            all.add(new Candidate(gs, gei, style, r.priority));
                        } catch (Exception ex) {
                        }
                    }
                } else if (r.type != null) {
                    int s = ms;
                    int e = me;
                    if (s < 0) s = 0;
                    if (e > L) e = L;
                    if (s >= e) continue;
                    all.add(new Candidate(s, e, r.type, r.priority));
                }
            }
        }

        // Sort and select non-overlapping matches
        Collections.sort(all, new Comparator<Candidate>() {
            public int compare(Candidate a, Candidate b) {
                if (a.priority != b.priority) return a.priority - b.priority;
                if (a.start != b.start) return a.start - b.start;
                return b.length - a.length;
            }
        });

        boolean[] taken = new boolean[L];
        for (int i = 0; i < all.size(); i++) {
            Candidate c = all.get(i);
            if (c.start < 0) c.start = 0;
            if (c.end > L) c.end = L;
            if (c.start >= c.end) continue;
            boolean overlap = false;
            for (int p = c.start; p < c.end; p++) {
                if (taken[p]) {
                    overlap = true;
                    break;
                }
            }
            if (overlap) continue;

            // Add readable debug output
            out.add(String.format("tok[%d,%d] pr=%d style=%s text=\"%s\"", c.start, c.end, c.priority, c.style, safeSubstring(line, c.start, c.end)));
            for (int p = c.start; p < c.end; p++) taken[p] = true;
        }
        return out;
    }

    /** Logs debug information for tokenized line to Logcat */
    public void logDebugTokens(String line) {
        List<String> t = debugTokenizeLine(line);
        for (int i = 0; i < t.size(); i++) {
            Log.d(TAG, t.get(i));
        }
    }

}
