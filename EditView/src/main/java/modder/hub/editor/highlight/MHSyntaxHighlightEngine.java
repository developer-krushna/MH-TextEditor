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
import android.graphics.Paint;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

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

    // Mapping of style names → colors (loaded from colors.json)
    private final Map<String, Integer> colors = new HashMap<String, Integer>();

    // All syntax rules loaded from the language JSON file
    private final List<Rule> rules = new ArrayList<Rule>();

    // Paint object used for text rendering
    private final TextPaint paint;

    // True if using dark mode (night colors)
    private final boolean darkMode;

    // save comment block
    private final List<CommentDef> commentDefs = new ArrayList<>();

    // preserve comment block
    public String commentBlock;

    private static final Set<
            String> VALID_ESCAPES = new HashSet<>(Arrays.asList("n", "t", "r", "b", "f", "\\", "'", "\"", "u"));
    // persistent multi-line block comment state (per SyntaxConfig instance)

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /**
     * LRU cache for per-line tokenized data. Key: line index Value: list of tokens for that line
     */
    private final LinkedHashMap<Integer, LineResult> lineCache = new LinkedHashMap<
            Integer, LineResult>(512, 0.75f, true) {
        private static final int MAX = 1000; // Maximum cached lines

        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, LineResult> eldest) {
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

        loadCommentDefsFromLang(lang);

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
                    // preserve lineBackground if present in original rule
                    if (rj.has("lineBackground"))
                        nr.put("lineBackground", rj.getString("lineBackground"));
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
                // read optional lineBackground
                if (rj.has("lineBackground")) {
                    r.lineBackground = rj.optString("lineBackground", null);
                    if (r.lineBackground != null && !r.lineBackground.isEmpty()) {
                        try {
                            r.lineBackgroundColor = Color.parseColor(r.lineBackground);
                        } catch (Exception ex) {
                            r.lineBackgroundColor = null;
                        }
                    }
                }
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

            // NEW: optional line background on a rule (hex string)
            if (rj.has("lineBackground")) {
                r.lineBackground = rj.optString("lineBackground", null);
                if (r.lineBackground != null && !r.lineBackground.isEmpty()) {
                    try {
                        r.lineBackgroundColor = Color.parseColor(r.lineBackground);
                    } catch (Exception ex) {
                        r.lineBackgroundColor = null;
                    }
                }
            }

            rules.add(r);
        }
    }

    // Loading comment object from the langauage file
    private void loadCommentDefsFromLang(JSONObject lang) {
        commentDefs.clear();
        commentBlock = null;
        try {
            if (!lang.has("comment")) return;
            Object c = lang.get("comment");
            if (c instanceof JSONObject) {
                JSONObject o = (JSONObject) c;
                String s = o.optString("startsWith", null);
                String e = o.optString("endsWith", null);
                if (s != null && !s.isEmpty() && (e == null || e.isEmpty())) {
                    commentBlock = s;
                }
                if (s != null && !s.isEmpty()) {
                    commentDefs.add(new CommentDef(s, e));
                }
            } else if (c instanceof JSONArray) {
                JSONArray arr = (JSONArray) c;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    String s = o.optString("startsWith", null);
                    String e = o.optString("endsWith", null);
                    if (s != null && !s.isEmpty() && (e == null || e.isEmpty())) {
                        commentBlock = s;
                    }
                    if (s != null && !s.isEmpty()) {
                        commentDefs.add(new CommentDef(s, e));
                    }
                }
            }
        } catch (Exception ex) {
            // load fail instead of crash
            Log.w(TAG, "Failed to load comment defs", ex);
        }
    }

    // Helper method, usefull for EditView for extracting comment block
    public String getCommentSyntaxBlock() {
        return commentBlock;
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

    // Special case for loading line background color
    public void drawLineBackground(Canvas canvas,
            String line,
            int index,
            int left,
            int top,
            int right,
            int bottom) {
        LineResult result = getOrTokenize(index, line);

        if (result.backgroundColor != null) {
            bgPaint.setColor(result.backgroundColor);
            canvas.drawRect(left, top, right, bottom, bgPaint);
        }
    }

    /** Draws a single line of highlighted text on the canvas. */
    public void drawLineText(Canvas canvas,
            String line,
            int index,
            int x,
            int y) {
        LineResult result = getOrTokenize(index, line);

        // Only tokens → no background here
        renderTokens(canvas, line, result.tokens, x, y);
    }

    /** Optimized shared cache lookup */
    private LineResult getOrTokenize(int index, String line) {
        LineResult result;

        synchronized (lineCache) {
            result = lineCache.get(index);
        }

        if (result != null) return result;

        // Tokenize
        result = tokenizeLine(line);

        synchronized (lineCache) {
            lineCache.put(index, result);
        }

        return result;
    }

    /**
     * Tokenizes a line into styled segments according to the rules. Resolves overlaps and converts
     * candidates into tokens.
     */
    private LineResult tokenizeLine(String line) {
        ArrayList<Candidate> all = new ArrayList<Candidate>();
        int L = (line == null) ? 0 : line.length();
        if (L == 0) return new LineResult(new ArrayList<Token>(), null);

        // pre: simple-scanner found big ranges (strings, comments)
        ArrayList<Candidate> pre = new ArrayList<Candidate>();

        // overrides: small spans inside strings (valid escapes -> "number", invalid -> "error")
        ArrayList<Candidate> overrides = new ArrayList<Candidate>();

        int i = 0;
        while (i < L) {
            char ch = line.charAt(i);

            // QUOTE: " or '
            if (ch == '"' || ch == '\'') {
                char quote = ch;
                int start = i;
                i++; // move past opening quote
                boolean escaped = false;
                while (i < L) {
                    char c2 = line.charAt(i);
                    if (c2 == '\\' && !escaped) {
                        escaped = true;
                        i++;
                        continue;
                    }
                    if (c2 == quote && !escaped) {
                        i++; // include closing quote
                        break;
                    }
                    escaped = false;
                    i++;
                }
                int end = i; // exclusive
                if (end > start) {
                    // add the full string span as a high-priority candidate (keeps string color)
                    pre.add(new Candidate(start, end, "string", -1000));

                    // process escapes inside string, but add them to overrides (so they don't
                    // prevent the string span)
                    int p = start + 1; // skip opening quote
                    while (p < end - 1) { // need at least "\" + next char
                        if (line.charAt(p) != '\\') {
                            p++;
                            continue;
                        }

                        // count consecutive backslashes starting at p
                        int bsStart = p;
                        int count = 0;
                        while (p < end && line.charAt(p) == '\\') {
                            count++;
                            p++;
                        }

                        // if the run reaches the end of string
                        if (p >= end) {
                            if ((count % 2) == 1) {
                                // dangling backslash -> mark the last backslash as error
                                int lastSlash = bsStart + count - 1;
                                overrides.add(new Candidate(lastSlash, lastSlash + 1, "error", -2000));
                            }
                            break;
                        }

                        char next = line.charAt(p); // char after run

                        if ((count % 2) == 1) { // odd -> last backslash introduces escape
                            int lastSlashIndex = bsStart + count - 1;

                            if (next == 'u') {
                                int hexStart = p + 1;
                                int hexEnd = hexStart + 4;
                                boolean validUnicode = true;
                                if (hexEnd <= end) {
                                    for (int h = hexStart; h < hexEnd; h++) {
                                        char hx = line.charAt(h);
                                        boolean isHex = (hx >= '0' && hx <= '9')
                                                || (hx >= 'a' && hx <= 'f')
                                                || (hx >= 'A' && hx <= 'F');
                                        if (!isHex) {
                                            validUnicode = false;
                                            break;
                                        }
                                    }
                                } else validUnicode = false;

                                if (validUnicode) {
                                    int tokenEnd = hexEnd;
                                    overrides.add(new Candidate(lastSlashIndex, tokenEnd, "number", -1500));
                                    p = hexEnd; // advance past hex digits
                                    continue;
                                } else {
                                    overrides.add(new Candidate(lastSlashIndex, lastSlashIndex + 2, "error", -2000));
                                    p = p + 1; // move past 'u'
                                    continue;
                                }
                            }

                            // single-char escapes
                            String esc = String.valueOf(next);
                            if (VALID_ESCAPES.contains(esc)) {
                                // valid: highlight \X as "number"
                                overrides.add(new Candidate(lastSlashIndex, lastSlashIndex + 2, "number", -1500));
                            } else {
                                // invalid: highlight \X as "error"
                                overrides.add(new Candidate(lastSlashIndex, lastSlashIndex + 2, "error", -2000));
                            }
                            // advance past the escaped char
                            p = p + 1;
                        } else {
                            // even number of backslashes -> no escape for the following char
                            // continue scanning from that char
                            p = p + 1;
                        }
                    }
                }
                continue;
            }

            // COMMENT: check any commentDefs that match at this index
            boolean matchedCommentThisPos = false;
            for (CommentDef cd : commentDefs) {
                String s = cd.startsWith;
                if (s == null || s.isEmpty()) continue;
                if (line.startsWith(s, i)) {
                    int start = i;
                    if (cd.endsWith == null || cd.endsWith.isEmpty()) {
                        // single-line: rest of line is comment
                        pre.add(new Candidate(start, L, "comment", -1000));
                        i = L; // done with line
                        matchedCommentThisPos = true;
                        break;
                    } else {
                        // block comment that should end on the same line (simple mode)
                        int endIdx = line.indexOf(cd.endsWith, i + s.length());
                        if (endIdx == -1) {
                            pre.add(new Candidate(start, L, "comment", -1000));
                            i = L;
                            matchedCommentThisPos = true;
                            break;
                        } else {
                            int end = endIdx + cd.endsWith.length();
                            pre.add(new Candidate(start, end, "comment", -1000));
                            i = end;
                            matchedCommentThisPos = true;
                            break;
                        }
                    }
                }
            }
            if (matchedCommentThisPos) continue;

            // otherwise move forward
            i++;
        }

        // Add pre (strings/comments) into main candidate list
        all.addAll(pre);

        // We'll track a selected full-line background if any rule indicates it
        Integer selectedLineBg = null;
        int selectedLineBgPriority = Integer.MAX_VALUE;

        // Run existing regex-based rules, skipping matches completely inside any pre-token
        for (int ri = 0; ri < rules.size(); ri++) {
            Rule r = rules.get(ri);
            Matcher m = r.pattern.matcher(line);
            while (m.find()) {
                int ms = m.start();
                int me = m.end();
                if (ms < 0 || me <= ms) continue;

                boolean insidePre = false;
                for (Candidate pc : pre) {
                    if (ms >= pc.start && me <= pc.end) {
                        insidePre = true;
                        break;
                    }
                }
                if (insidePre) continue;

                // If this rule has a lineBackground defined, mark the full line background
                if (r.lineBackgroundColor != null) {
                    // prefer the rule with lowest priority index (earlier in file)
                    if (r.priority < selectedLineBgPriority) {
                        selectedLineBgPriority = r.priority;
                        selectedLineBg = r.lineBackgroundColor;
                    }
                }

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

        // Sort candidates by priority, start, length
        Collections.sort(all, new Comparator<Candidate>() {
            public int compare(Candidate a, Candidate b) {
                if (a.priority != b.priority) return a.priority - b.priority;
                if (a.start != b.start) return a.start - b.start;
                return b.length - a.length;
            }
        });

        // Choose non-overlapping tokens for main candidates (strings/comments/other rules)
        boolean[] taken = new boolean[L];
        ArrayList<Token> chosen = new ArrayList<Token>();
        for (int i2 = 0; i2 < all.size(); i2++) {
            Candidate c = all.get(i2);
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

            Integer col = colors.get(c.style);
            if (col == null) col = colors.get("default");

            chosen.add(new Token(c.start, c.end, col.intValue()));

            for (int p = c.start; p < c.end; p++) taken[p] = true;
        }

        // Now add override tokens (escape/error) — they are allowed to overlap strings.
        for (Candidate o : overrides) {
            int s = o.start;
            int e = o.end;
            if (s < 0) s = 0;
            if (e > L) e = L;
            if (s >= e) continue;
            Integer col = colors.get(o.style);
            if (col == null) col = colors.get("default");
            chosen.add(new Token(s, e, col.intValue()));
        }

        // Sort final tokens by start — but ensure longer (string) spans come before short overrides
        // where same start
        Collections.sort(chosen, new Comparator<Token>() {
            public int compare(Token a, Token b) {
                if (a.start != b.start) return a.start - b.start;
                int lenA = a.end - a.start;
                int lenB = b.end - b.start;
                return lenB - lenA; // longer first
            }
        });

        return new LineResult(chosen, selectedLineBg);
    }

    /** Draws text segments with their respective colors. */
    // Fixed support for Arabic Letters by ChatGPT
    private void renderTokens(Canvas canvas, String line, List<Token> tokens, int x, int y) {
        if (line == null) return;

        // Build full-line spannable
        SpannableString ss = new SpannableString(line);

        // 1) Apply default black for the entire line first (will be overridden by token spans)
        ss.setSpan(
                new ForegroundColorSpan(Color.BLACK),
                0,
                ss.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        // 2) Apply syntax colors from tokens (these override the default black)
        if (tokens != null) {
            for (Token t : tokens) {
                try {
                    int start = Math.max(0, t.start);
                    int end = Math.min(line.length(), t.end);
                    if (start >= end) continue;
                    ss.setSpan(
                            new ForegroundColorSpan(t.color),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                } catch (Exception ignore) {
                }
            }
        }

        // 3) Choose available width - measure full line width (ensure >=1)
        float fullWidth = paint.measureText(line);
        int availableWidth = Math.max(1, (int) Math.ceil(fullWidth));

        // 4) Build StaticLayout ensuring LTR direction and no extra padding
        StaticLayout layout = StaticLayout.Builder
                .obtain(ss, 0, ss.length(), paint, availableWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL) // left alignment for LTR flow
                .setIncludePad(false)
                .setTextDirection(TextDirectionHeuristics.LTR) // FORCE LTR for all text
                .build();

        // 5) Translate so that the layout's first baseline matches the 'y' baseline passed in.
        //    paint.getFontMetrics().top is the offset from baseline to the top of the text box.
        Paint.FontMetrics fm = paint.getFontMetrics();
        float topOffset = y + fm.top; // layout top such that baseline = y

        canvas.save();
        canvas.translate(x, topOffset);
        layout.draw(canvas);
        canvas.restore();
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
        Integer selectedLineBg = null;
        int selectedLineBgPriority = Integer.MAX_VALUE;

        for (int ri = 0; ri < rules.size(); ri++) {
            Rule r = rules.get(ri);
            Matcher m = r.pattern.matcher(line);
            while (m.find()) {
                int ms = m.start();
                int me = m.end();
                if (ms < 0 || me <= ms) continue;
                // collect group styles or full match as candidates (no pre skipping here for debug)
                if (r.lineBackgroundColor != null) {
                    if (r.priority < selectedLineBgPriority) {
                        selectedLineBgPriority = r.priority;
                        selectedLineBg = r.lineBackgroundColor;
                    }
                }
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

        if (selectedLineBg != null) {
            out.add(String.format("LINE-BG color=%s", String.format("#%06X", (0xFFFFFF & selectedLineBg))));
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
