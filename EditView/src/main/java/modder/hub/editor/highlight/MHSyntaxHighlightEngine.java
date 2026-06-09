
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


 *     Please contact Krushna by email mt.modder.hub@gmail.in if you need
 *     additional information or have any questions
 */

package modder.hub.editor.highlight;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.text.LineBreaker;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.text.style.ForegroundColorSpan;
import android.text.style.TabStopSpan;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

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

/**
 * Author : @developer-krushna
 * MHSyntaxHighlightEngine ------------------------ A syntax highlighting engine that parses and
 * colors text using regex-based rules.
 * OPTIMIZED AND COMMENTS BY THE AI
 * <p>It supports multiple languages (via JSON rule files) and color themes (day/night). The engine
 * can draw highlighted text line-by-line on a Canvas, caching results for speed.
 */
public class MHSyntaxHighlightEngine {

    private static final String TAG = "MHSyntaxHighlightEngine";
    private static final Set<String> VALID_ESCAPES = new HashSet<>(Arrays.asList("n", "t", "r", "b", "f", "\\", "'", "\"", "u"));
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
    // persistent multi-line block comment state (per SyntaxConfig instance)
    private final List<Integer> lineStates = new ArrayList<>();
    private final List<Integer> lineBraceLevels = new ArrayList<>();
    private final TabStopSpan[] tabStops = new TabStopSpan[100];
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /**
     * LRU cache for per-line tokenized data. Key: line index Value: list of tokens for that line
     */
    private final LinkedHashMap<Integer, LineResult> lineCache = new LinkedHashMap<
            Integer, LineResult>(1024, 0.75f, true) {
        private static final int MAX = 2000; // Increased cache for better performance

        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, LineResult> eldest) {
            return size() > MAX;
        }
    };
    // preserve comment block
    public String commentBlock;
    private String languageName;
    private LineProvider lineProvider;
    private boolean hasMultilineComments = false;
    private int mTabSize = 4;
    private int mWrapWidth = 5000000;
    private boolean mWordWrap = false;

    /**
     * Constructor: Initializes the engine with color and language configurations.
     */
    public MHSyntaxHighlightEngine(Context ctx, TextPaint textPaint, String languageAssetFile, boolean darkMode) {
        this.paint = textPaint;
        this.darkMode = darkMode;
        try {
            initColors(ctx);
            if (languageAssetFile != null && !languageAssetFile.isEmpty()) {
                initLanguage(ctx, languageAssetFile);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        updateTabStops();
    }

    public void setLineProvider(LineProvider provider) {
        this.lineProvider = provider;
    }

    public void setWordWrap(boolean enabled, int width) {
        this.mWordWrap = enabled;
        this.mWrapWidth = enabled ? width : 5000000;
        clearLayoutCache();
    }


    public void setTabSize(int size) {
        if (size > 0 && size != mTabSize) {
            mTabSize = size;
            updateTabStops();
        }
    }


    /**
     * Updates the tab-stop grid based on the current text size
     */
    public void updateTabStops() {
        float spaceWidth = paint.measureText(" ");
        int tabInterval = (int) Math.ceil(spaceWidth * mTabSize);
        if (tabInterval <= 0) tabInterval = 1;

        for (int i = 0; i < 100; i++) {
            tabStops[i] = new TabStopSpan.Standard((i + 1) * tabInterval);
        }
        clearLayoutCache();
    }

    /**
     * Loads color definitions from assets/colors.json
     */
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

    /**
     * Loads language-specific highlighting rules from assets/langFile (JSON)
     */
    private void initLanguage(Context ctx, String langFile) throws Exception {
        String s = loadAsset(ctx, langFile);
        JSONObject lang = new JSONObject(s);

        if (lang.has("name")) {
            Object nameObj = lang.get("name");
            if (nameObj instanceof JSONArray) {
                JSONArray names = (JSONArray) nameObj;
                if (names.length() > 0) {
                    this.languageName = names.getString(0);
                }
            } else if (nameObj instanceof String) {
                this.languageName = (String) nameObj;
            }
        }

        if (this.languageName == null) {
            // Fallback to filename if "name" key is missing or invalid
            this.languageName = langFile;
        }

        loadCommentDefsFromLang(lang);

        // Load custom styles/colors for this language
        if (lang.has("styles")) {
            JSONObject stylesObj = lang.getJSONObject("styles");
            Iterator<String> keys = stylesObj.keys();
            while (keys.hasNext()) {
                String styleName = keys.next();
                Object val = stylesObj.get(styleName);
                if (val instanceof JSONObject) {
                    JSONObject styleDef = (JSONObject) val;
                    String hex = darkMode ? styleDef.optString("night", styleDef.optString("day", null))
                            : styleDef.optString("day", styleDef.optString("night", null));
                    if (hex != null) {
                        try {
                            colors.put(styleName, Color.parseColor(hex));
                        } catch (Exception ignore) {
                        }
                    }
                } else if (val instanceof String) {
                    // Mapping to an existing color name: "macro" > "namespace"
                    String existingStyle = (String) val;
                    if (colors.containsKey(existingStyle)) {
                        colors.put(styleName, colors.get(existingStyle));
                    }
                }
            }
        }

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
                String patternStr = String.format("(?:(?<=^)|(?<=\\s)|(?<=\\())(?:(?:%s))(?![A-Za-z0-9_/$\\.])", sb);
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
        hasMultilineComments = false;
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
                    if (e != null && !e.isEmpty()) hasMultilineComments = true;
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
                        if (e != null && !e.isEmpty()) hasMultilineComments = true;
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

    public List<CommentDef> getCommentDefs() {
        return commentDefs;
    }

    /**
     * Reads a file from the assets directory as UTF-8 text.
     */
    public String getLanguageName() {
        return languageName;
    }

    private String loadAsset(Context ctx, String name) throws Exception {
        InputStream is = ctx.getAssets().open(name);
        byte[] b = new byte[is.available()];
        is.read(b);
        is.close();
        return new String(b, StandardCharsets.UTF_8);
    }

    /**
     * Clears the entire token cache
     */
    public void clearCache() {
        synchronized (lineCache) {
            lineCache.clear();
        }
        synchronized (lineStates) {
            lineStates.clear();
        }
        synchronized (lineBraceLevels) {
            lineBraceLevels.clear();
        }
    }

    /**
     * Clears only the visual layouts, keeping logical states (tokens, comments, braces) intact
     */
    public void clearLayoutCache() {
        synchronized (lineCache) {
            for (LineResult res : lineCache.values()) {
                res.layout = null;
            }
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

    /**
     * Draws a single line of highlighted text on the canvas.
     */
    public void drawLineText(Canvas canvas,
                             String line,
                             int index,
                             int x,
                             int y) {
        LineResult result = getOrTokenize(index, line);

        if (result.layout != null) {
            // y is now the top of the logical line
            canvas.save();
            canvas.translate(x, y);
            result.layout.draw(canvas);
            canvas.restore();
        } else {
            // HIGH-PERFORMANCE FALLBACK: For extremely long lines (>10k chars),
            // we draw only the visible part using tokens directly.
            // Convert top to baseline for manual rendering
            int baseline = y + (int) Math.ceil(-paint.ascent());

            Rect clip = canvas.getClipBounds();
            float spaceWidth = paint.measureText(" ");

            // Calculate visible character indices based on x and clip
            int startChar = Math.max(0, (int) ((clip.left - x) / spaceWidth) - 2);
            int endChar = (int) ((clip.right - x) / spaceWidth) + 2;

            if (line != null) {
                renderVisibleTokens(canvas, line, result, x, baseline, startChar, endChar);
            }
        }
    }

    private void renderVisibleTokens(Canvas canvas, String line, LineResult result, int x, int y, int startChar, int endChar) {
        if (line == null) return;
        int len = line.length();
        int drawStart = Math.max(0, startChar);
        int drawEnd = Math.min(len, endChar);
        if (drawStart >= drawEnd) return;

        float spaceWidth = paint.measureText(" ");
        Integer defCol = colors.get("default");
        int defaultColor = defCol != null ? defCol : (darkMode ? Color.WHITE : Color.BLACK);

        List<Token> tokens = result.tokens;

        int currentPos = drawStart;
        while (currentPos < drawEnd) {
            Token match = null;
            if (tokens != null) {
                // Find if currentPos is inside any token
                for (Token t : tokens) {
                    if (currentPos >= t.start && currentPos < t.end) {
                        match = t;
                        break;
                    }
                }
            }

            int segmentEnd;
            int color;
            if (match != null) {
                segmentEnd = Math.min(drawEnd, match.end);
                color = match.color;
            } else {
                // Find next token start
                segmentEnd = drawEnd;
                if (tokens != null) {
                    for (Token t : tokens) {
                        if (t.start > currentPos && t.start < segmentEnd) {
                            segmentEnd = t.start;
                        }
                    }
                }
                color = defaultColor;
            }

            paint.setColor(color);
            // Optimization: Avoid creating massive substrings. Use char array segment if needed.
            String segment = line.substring(currentPos, segmentEnd);
            canvas.drawText(segment, x + (currentPos * spaceWidth), y, paint);
            currentPos = segmentEnd;
        }
    }

    /**
     * Optimized shared cache lookup
     */
    public LineResult getOrTokenize(int index, String line) {
        LineResult result;

        synchronized (lineCache) {
            result = lineCache.get(index);
        }

        // Optimized cache check: avoid toString()
        if (result != null && result.layout != null && result.text != null) {
            if (result.text.length() == line.length() && result.text.equals(line)) {
                return result;
            }
        }

        // If we have cached tokens but layout is missing/stale, rebuild layout only
        if (result != null && result.tokens != null) {
            createLayout(line, result);
            return result;
        }

        int startState = 0;
        int startBraceLevel = 0;

        ensureStatesComputed(index);
        synchronized (lineStates) {
            if (index > 1 && lineStates.size() >= index - 1) {
                startState = lineStates.get(index - 2);
            }
        }
        synchronized (lineBraceLevels) {
            if (index > 1 && lineBraceLevels.size() >= index - 1) {
                startBraceLevel = lineBraceLevels.get(index - 2);
            }
        }

        // Tokenize with state
        result = tokenizeLine(line, startState, startBraceLevel);

        // Update state list and handle propagation
        updateLineState(index, result.endState, result.endBraceLevel);

        // Build layout
        createLayout(line, result);

        synchronized (lineCache) {
            lineCache.put(index, result);
        }

        return result;
    }

    private void ensureStatesComputed(int index) {
        synchronized (lineStates) {
            if (lineStates.size() >= index - 1) return;

            if (lineProvider == null) {
                return;
            }

            // Compute missing states sequentially.
            // Limit scanning on UI thread to prevent jank during large jumps.
            int startIdx = lineStates.size() + 1;
            int limit = 500;
            int endIdx = Math.min(index, startIdx + limit);

            for (int i = startIdx; i < endIdx; i++) {
                int startState = (i > 1 && lineStates.size() >= i - 1) ? lineStates.get(i - 2) : 0;
                int startBraceLevel = (i > 1 && lineBraceLevels.size() >= i - 1) ? lineBraceLevels.get(i - 2) : 0;
                String line = lineProvider.getLine(i);

                int[] states = scanLine(line, startState, startBraceLevel);

                if (lineStates.size() < i) {
                    lineStates.add(states[0]);
                    lineBraceLevels.add(states[1]);
                } else {
                    lineStates.set(i - 1, states[0]);
                    lineBraceLevels.set(i - 1, states[1]);
                }
            }
        }
    }

    /**
     * Light-weight version of tokenizeLine that only computes state changes.
     * Essential for fast-forwarding through large files.
     */
    private int[] scanLine(String line, int startState, int startBraceLevel) {
        int L = (line == null) ? 0 : line.length();
        int endState = startState;
        int currentBraceLevel = startBraceLevel;

        if (L == 0) return new int[]{endState, currentBraceLevel};

        int i = 0;
        // Continue multi-line comment
        if (startState > 0 && startState <= commentDefs.size()) {
            CommentDef cd = commentDefs.get(startState - 1);
            int endIdx = line.indexOf(cd.endsWith);
            if (endIdx == -1) {
                i = L;
            } else {
                i = endIdx + cd.endsWith.length();
                endState = 0;
            }
        }

        while (i < L) {
            char ch = line.charAt(i);

            // Skip strings (to avoid finding comments/braces in them)
            if (ch == '"' || ch == '\'') {
                i++;
                boolean escaped = false;
                while (i < L) {
                    char c2 = line.charAt(i);
                    if (c2 == '\\' && !escaped) {
                        escaped = true;
                        i++;
                        continue;
                    }
                    if ((c2 == ch) && !escaped) {
                        i++;
                        break;
                    }
                    escaped = false;
                    i++;
                }
                continue;
            }

            // Check comments
            boolean matched = false;
            for (int ci = 0; ci < commentDefs.size(); ci++) {
                CommentDef cd = commentDefs.get(ci);
                if (cd.startsWith != null && line.startsWith(cd.startsWith, i)) {
                    if (cd.endsWith == null || cd.endsWith.isEmpty()) {
                        i = L; // single-line
                    } else {
                        int endIdx = line.indexOf(cd.endsWith, i + cd.startsWith.length());
                        if (endIdx == -1) {
                            endState = ci + 1;
                            i = L;
                        } else {
                            i = endIdx + cd.endsWith.length();
                            endState = 0;
                        }
                    }
                    matched = true;
                    break;
                }
            }
            if (matched) continue;

            // Tracking braces
            if (ch == '{') {
                currentBraceLevel++;
            } else if (ch == '}') {
                currentBraceLevel--;
            }
            i++;
        }
        return new int[]{endState, currentBraceLevel};
    }

    private void updateLineState(int index, int newState, int newBraceLevel) {
        boolean changed = false;
        synchronized (lineStates) {
            int stateIdx = index - 1;
            if (stateIdx < lineStates.size()) {
                int oldState = lineStates.get(stateIdx);
                if (oldState != newState) {
                    lineStates.set(stateIdx, newState);
                    changed = true;
                }
            } else {
                // Fill gaps if any
                while (lineStates.size() < stateIdx) {
                    int last = lineStates.isEmpty() ? 0 : lineStates.get(lineStates.size() - 1);
                    lineStates.add(last);
                }
                lineStates.add(newState);
                changed = true;
            }
        }
        synchronized (lineBraceLevels) {
            int stateIdx = index - 1;
            if (stateIdx < lineBraceLevels.size()) {
                int oldLevel = lineBraceLevels.get(stateIdx);
                if (oldLevel != newBraceLevel) {
                    lineBraceLevels.set(stateIdx, newBraceLevel);
                    changed = true;
                }
            } else {
                while (lineBraceLevels.size() < stateIdx) {
                    int last = lineBraceLevels.isEmpty() ? 0 : lineBraceLevels.get(lineBraceLevels.size() - 1);
                    lineBraceLevels.add(last);
                }
                lineBraceLevels.add(newBraceLevel);
                changed = true;
            }
        }

        if (changed) {
            invalidateSubsequentStates(index);
            invalidateSubsequentLines(index);
        }
    }

    public void invalidateSubsequentStates(int fromIndex) {
        synchronized (lineStates) {
            if (fromIndex < lineStates.size()) {
                lineStates.subList(fromIndex, lineStates.size()).clear();
            }
        }
        synchronized (lineBraceLevels) {
            if (fromIndex < lineBraceLevels.size()) {
                lineBraceLevels.subList(fromIndex, lineBraceLevels.size()).clear();
            }
        }
    }

    public void invalidateSubsequentLines(int fromIndex) {
        synchronized (lineCache) {
            Iterator<Integer> it = lineCache.keySet().iterator();
            while (it.hasNext()) {
                if (it.next() >= fromIndex) {
                    it.remove();
                }
            }
        }
    }

    private void createLayout(String line, LineResult result) {
        if (line == null) return;
        result.text = line;
        result.shiftMap = null;

        // PERFORMANCE CAP: If the line is extremely long, do not create a StaticLayout for the whole thing.
        // StaticLayout is O(N) for measurements and line breaking. 3M chars will freeze the UI.
        // need some core optimization for large lines based files
        if (line.length() > 10000 && !mWordWrap) {
            result.layout = null; // Mark that we should use renderTokens fallback
            result.width = (int) (paint.measureText(" ") * line.length());
            return;
        }

        SpannableString ss = new SpannableString(line);

        Integer defaultColor = colors.get("default");
        if (defaultColor == null) defaultColor = darkMode ? Color.WHITE : Color.BLACK;

        ss.setSpan(new ForegroundColorSpan(defaultColor), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        if (result.tokens != null) {
            for (Token t : result.tokens) {
                int start = Math.max(0, t.start);
                int end = Math.min(line.length(), t.end);
                if (start < end && start < ss.length()) {
                    ss.setSpan(new ForegroundColorSpan(t.color), start, Math.min(end, ss.length()), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }

        if (line.indexOf('\t') != -1) {
            for (int i = 0; i < 20; i++) {
                ss.setSpan(tabStops[i], 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        int width = mWordWrap ? mWrapWidth : 5000000;
        StaticLayout.Builder builder = StaticLayout.Builder
                .obtain(ss, 0, ss.length(), paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .setBreakStrategy(LineBreaker.BREAK_STRATEGY_BALANCED)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .setTextDirection(TextDirectionHeuristics.LTR);

        if (mWordWrap) {
            int indent = (int) (paint.measureText(" ") * 2.5f);
            // Minimal right padding for icons
            int iconSize = (int) (paint.getTextSize() * 0.5f);
            int rightIndent = iconSize + (int) (paint.measureText(" ") * 1.5f);

            builder.setIndents(new int[]{0, indent}, new int[]{rightIndent, rightIndent});
            result.wrapIndent = indent;
            result.rightIndent = rightIndent;
        } else {
            result.wrapIndent = 0;
            result.rightIndent = 0;
        }

        result.layout = builder.build();
        result.width = mWordWrap ? width : (int) Math.ceil(result.layout.getLineWidth(0));
    }

    /**
     * Tokenizes a line into styled segments according to the rules. Resolves overlaps and converts
     * candidates into tokens.
     */
    private LineResult tokenizeLine(String line, int startState, int startBraceLevel) {
        ArrayList<Candidate> all = new ArrayList<Candidate>();
        int L = (line == null) ? 0 : line.length();
        int endState = startState;
        int currentBraceLevel = startBraceLevel;

        if (L == 0)
            return new LineResult(new ArrayList<Token>(), null, endState, startBraceLevel, currentBraceLevel);

        // PERFORMANCE CAP: If the line is exceptionally long, we limit highlighting to avoid UI freeze.
        final int highlightLimit = 5000;
        final int activeL = Math.min(L, highlightLimit);

        ArrayList<Candidate> pre = new ArrayList<Candidate>();
        ArrayList<Candidate> overrides = new ArrayList<Candidate>();

        int i = 0;
        boolean hasLang = !rules.isEmpty() || !commentDefs.isEmpty();

        // Continue multi-line comment if startState > 0
        if (startState > 0 && startState <= commentDefs.size()) {
            CommentDef cd = commentDefs.get(startState - 1);
            int endIdx = line.indexOf(cd.endsWith);
            if (endIdx == -1) {
                pre.add(new Candidate(0, L, "comment", -1000));
                i = L;
            } else {
                int end = endIdx + cd.endsWith.length();
                pre.add(new Candidate(0, end, "comment", -1000));
                i = end;
                endState = 0; // Comment finished
            }
        }

        while (i < activeL) {
            char ch = line.charAt(i);

            if (hasLang) {
                // QUOTE: " or '
                if (ch == '"' || ch == '\'') {
                    char quote = ch;
                    int start = i;
                    i++; // move past opening quote
                    boolean escaped = false;
                    while (i < activeL) {
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
                        pre.add(new Candidate(start, end, "string", -1000));

                        // Process escapes inside string
                        int p = start + 1;
                        while (p < end - 1) {
                            if (line.charAt(p) != '\\') {
                                p++;
                                continue;
                            }
                            int bsStart = p;
                            int count = 0;
                            while (p < end && line.charAt(p) == '\\') {
                                count++;
                                p++;
                            }
                            if (p >= end) {
                                if ((count % 2) == 1) {
                                    int lastSlash = bsStart + count - 1;
                                    overrides.add(new Candidate(lastSlash, lastSlash + 1, "error", -2000));
                                }
                                break;
                            }
                            char next = line.charAt(p);
                            if ((count % 2) == 1) {
                                int lastSlashIndex = bsStart + count - 1;
                                if (next == 'u') {
                                    int hexStart = p + 1;
                                    int hexEnd = hexStart + 4;
                                    boolean validUnicode = true;
                                    if (hexEnd <= end) {
                                        for (int h = hexStart; h < hexEnd; h++) {
                                            char hx = line.charAt(h);
                                            boolean isHex = (hx >= '0' && hx <= '9') || (hx >= 'a' && hx <= 'f') || (hx >= 'A' && hx <= 'F');
                                            if (!isHex) {
                                                validUnicode = false;
                                                break;
                                            }
                                        }
                                    } else validUnicode = false;
                                    if (validUnicode) {
                                        overrides.add(new Candidate(lastSlashIndex, hexEnd, "number", -1500));
                                        p = hexEnd;
                                        continue;
                                    } else {
                                        overrides.add(new Candidate(lastSlashIndex, lastSlashIndex + 2, "error", -2000));
                                        p = p + 1;
                                        continue;
                                    }
                                }
                                String esc = String.valueOf(next);
                                if (VALID_ESCAPES.contains(esc)) {
                                    overrides.add(new Candidate(lastSlashIndex, lastSlashIndex + 2, "number", -1500));
                                } else {
                                    overrides.add(new Candidate(lastSlashIndex, lastSlashIndex + 2, "error", -2000));
                                }
                                p = p + 1;
                            } else {
                                p = p + 1;
                            }
                        }
                    }
                    continue;
                }

                // COMMENT: check any commentDefs that match at this index
                boolean matchedCommentThisPos = false;
                for (int ci = 0; ci < commentDefs.size(); ci++) {
                    CommentDef cd = commentDefs.get(ci);
                    String s = cd.startsWith;
                    if (s == null || s.isEmpty()) continue;
                    if (line.startsWith(s, i)) {
                        int start = i;
                        if (cd.endsWith == null || cd.endsWith.isEmpty()) {
                            // single-line
                            pre.add(new Candidate(start, L, "comment", -1000));
                            i = L;
                            matchedCommentThisPos = true;
                            break;
                        } else {
                            // block comment
                            int endIdx = line.indexOf(cd.endsWith, i + s.length());
                            if (endIdx == -1) {
                                pre.add(new Candidate(start, L, "comment", -1000));
                                i = L;
                                endState = ci + 1; // Enter block comment state
                                matchedCommentThisPos = true;
                                break;
                            } else {
                                int end = endIdx + cd.endsWith.length();
                                pre.add(new Candidate(start, end, "comment", -1000));
                                i = end;
                                endState = 0; // Block comment finished
                                matchedCommentThisPos = true;
                                break;
                            }
                        }
                    }
                }
                if (matchedCommentThisPos) continue;
            }

            // BRACES tracking (skips comments/strings because of continue above)
            if (ch == '{') {
                currentBraceLevel++;
            } else if (ch == '}') {
                currentBraceLevel--;
            }

            i++;
        }

        // Scan the rest of the line ONLY for brace tracking to keep guidelines accurate
        while (i < L) {
            char ch = line.charAt(i);
            if (ch == '{') {
                currentBraceLevel++;
            } else if (ch == '}') {
                currentBraceLevel--;
            }
            i++;
        }

        all.addAll(pre);

        Integer selectedLineBg = null;
        int selectedLineBgPriority = Integer.MAX_VALUE;

        // Apply highlighting rules only to the visible/capped portion
        String highlightPart = (L > highlightLimit) ? line.substring(0, highlightLimit) : line;

        for (int ri = 0; ri < rules.size(); ri++) {
            Rule r = rules.get(ri);
            Matcher m = r.pattern.matcher(highlightPart);
            while (m.find()) {
                int ms = m.start();
                int me = m.end();
                if (ms < 0 || me <= ms) continue;

                boolean insidePre = false;
                for (Candidate pc : pre) {
                    if (ms >= pc.start && ms < pc.end) {
                        insidePre = true;
                        break;
                    }
                }
                if (insidePre) continue;

                if (r.lineBackgroundColor != null) {
                    if (r.priority < selectedLineBgPriority) {
                        selectedLineBgPriority = r.priority;
                        selectedLineBg = r.lineBackgroundColor;
                    }
                }

                if (r.groupStyles != null && !r.groupStyles.isEmpty()) {
                    for (Map.Entry<Integer, String> ge : r.groupStyles.entrySet()) {
                        int gi = ge.getKey();
                        String style = ge.getValue();
                        try {
                            int gs = m.start(gi);
                            int gei = m.end(gi);
                            if (gs < 0 || gei <= gs || gs >= L) continue;
                            if (gei > L) gei = L;
                            all.add(new Candidate(gs, gei, style, r.priority));
                        } catch (Exception ignore) {
                        }
                    }
                } else if (r.type != null) {
                    int s = Math.max(0, ms);
                    int e = Math.min(L, me);
                    if (s < e) all.add(new Candidate(s, e, r.type, r.priority));
                }
            }
        }

        Collections.sort(all, new Comparator<Candidate>() {
            public int compare(Candidate a, Candidate b) {
                if (a.priority != b.priority) return a.priority - b.priority;
                if (a.start != b.start) return a.start - b.start;
                return b.length - a.length;
            }
        });

        boolean[] taken = new boolean[L];
        ArrayList<Token> chosen = new ArrayList<Token>();
        for (Candidate c : all) {
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

        for (Candidate o : overrides) {
            Integer col = colors.get(o.style);
            if (col == null) col = colors.get("default");
            chosen.add(new Token(o.start, o.end, col.intValue()));
        }

        Collections.sort(chosen, new Comparator<Token>() {
            public int compare(Token a, Token b) {
                if (a.start != b.start) return a.start - b.start;
                return (b.end - b.start) - (a.end - a.start);
            }
        });

        return new LineResult(chosen, selectedLineBg, endState, startBraceLevel, currentBraceLevel);
    }


    public interface LineProvider {
        String getLine(int index);
    }

}