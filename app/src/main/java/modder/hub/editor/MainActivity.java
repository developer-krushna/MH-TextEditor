package modder.hub.editor;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import modder.hub.editor.EditView;
import modder.hub.editor.R;
import modder.hub.editor.buffer.GapBuffer;
import modder.hub.editor.listener.OnTextChangedListener;
import org.mozilla.universalchardet.UniversalDetector;
import modder.hub.editor.component.ClipboardPanel;

public class MainActivity extends Activity {

    private final String TAG = this.getClass().getSimpleName();

    private EditView editView;

    private ProgressBar mIndeterminateBar;

    private SharedPreferences mSharedPreference;
    private SharedPreferences editor_pref;

    private Charset mDefaultCharset = StandardCharsets.UTF_8;
    private String externalPath = File.separator;

    private EditText edittext_replace, edittext_find;
    private TextView previous_btn, next_btn, replace_btn, replace_all_btn, item_menu;
    private LinearLayout search_pad, linear_rep;

    private FrameLayout editorContainer;
    private LinearLayout functionBar;

    private static final List<String> SYMBOLS = Arrays.asList(
            "(", ")", "[", "]", "{", "}", ".", ",", ";",
            "'", "\"", "+", "-", "*", "/", "%", "=", "<",
            ">", "&", "|", "~", "^", "!", "?", "\\", ":",
            "#", "@", "`"
    );

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            // TODO: Implement this method
            super.handleMessage(msg);
            invalidateOptionsMenu();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initialize();
        initializeLogic();
    }

    private void initialize() {
        mIndeterminateBar = findViewById(R.id.indeterminateBar);
        mIndeterminateBar.setBackground(null);
        editor_pref = getSharedPreferences("editor_pref", Activity.MODE_PRIVATE);
        mSharedPreference = PreferenceManager.getDefaultSharedPreferences(this);

        edittext_replace = findViewById(R.id.edittext_replace);
        edittext_find = findViewById(R.id.edittext_find);
        previous_btn = findViewById(R.id.previous_btn);
        next_btn = findViewById(R.id.next_btn);
        replace_btn = findViewById(R.id.replace_btn);
        replace_all_btn = findViewById(R.id.replace_all_btn);
        item_menu = findViewById(R.id.item_menu);
        search_pad = findViewById(R.id.search_pad);
        linear_rep = findViewById(R.id.linear_rep);

        editorContainer = findViewById(R.id.editorContainer);
        functionBar = findViewById(R.id.functionBar);

        editView = new EditView(this);
    }

    private void initializeLogic() {
        setTitle("MH Text Editor");
        editView.setLayoutParams(new FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
        ));

        editorContainer.addView(editView);
        editorContainer.setFocusableInTouchMode(true);
        editView.requestFocus();

        addFunctionBar(functionBar, editView);

        editView.setTypeface(Typeface.DEFAULT);
        if (editor_pref.contains("syntax_position")) {
            if (editor_pref.getInt("syntax_position", 0) == 1) {
                editView.setSyntaxLanguageFileName("smali.json");
            }
            if (editor_pref.getInt("syntax_position", 0) == 2) {
                editView.setSyntaxLanguageFileName("xml.json");
            }
            if (editor_pref.getInt("syntax_position", 0) == 3) {
                editView.setSyntaxLanguageFileName("java.json");
            }
        }
        if (editor_pref.contains("menu_style")) {
            if (editor_pref.getInt("menu_style", 0) == 0) {
                editView.setMenuStyle(ClipboardPanel.MenuDisplayMode.ICON_AND_TEXT);
            }
            if (editor_pref.getInt("menu_style", 0) == 1) {
                editView.setMenuStyle(ClipboardPanel.MenuDisplayMode.TEXT_ONLY);
            }
            if (editor_pref.getInt("menu_style", 0) == 2) {
                editView.setMenuStyle(ClipboardPanel.MenuDisplayMode.ICON_ONLY);
            }
        }

        editView.setSyntaxDarkMode(false);
        editView.setOnTextChangedListener(new OnTextChangedListener() {
            @Override
            public void onTextChanged() {
                mHandler.sendEmptyMessage(0);
                editView.postInvalidate();
            }
        });
        if (mSharedPreference.contains("path")) {
            String path = mSharedPreference.getString("path", "");
            if (new File(path).exists()) {
                new ReadFileThread().execute(path);
            }
        }

        String permission = Manifest.permission.WRITE_EXTERNAL_STORAGE;

        if (!hasPermission(permission)) {
            applyPermission(permission);
        }

        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            externalPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        }
    }

    public boolean hasPermission(String permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
        else
            return true;
    }

    public void applyPermission(String permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(permission)) {
                Toast.makeText(this, "request read sdcard permmission", Toast.LENGTH_SHORT).show();
            }
            requestPermissions(new String[]{permission}, 0);
        }
    }

    private void toggleEditMode() {
        editView.setEditedMode(!editView.getEditedMode());
        mHandler.sendEmptyMessage(0);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        // TODO: Implement this method
        super.onWindowFocusChanged(hasFocus);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem moreMenu = menu.findItem(R.id.moreItems);
        moreMenu.getIcon().setTint(Color.WHITE);
        MenuItem saveMenu = menu.findItem(R.id.save);
        MenuItem undo = menu.findItem(R.id.undo);
        undo.setIcon(R.drawable.ic_undo);
        if (editView.canUndo()) {
            undo.getIcon().setTint(Color.WHITE);
            undo.setEnabled(true);
            saveMenu.getIcon().setTint(Color.WHITE);
        } else {
            saveMenu.getIcon().setTint(Color.GRAY);
            undo.getIcon().setTint(Color.GRAY);
            undo.setEnabled(false);
        }
        MenuItem redo = menu.findItem(R.id.redo);
        redo.setIcon(R.drawable.ic_redo);
        if (editView.canRedo()) {
            redo.getIcon().setTint(Color.WHITE);
            redo.setEnabled(true);
        } else {
            redo.getIcon().setTint(Color.GRAY);
            redo.setEnabled(false);
        }
        MenuItem editMode = menu.findItem(R.id.read_only);

        if (editView.getEditedMode()) {
            editMode.setChecked(false);
        } else {
            editMode.setChecked(true);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.editor_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.undo:
                editView.undo();
                break;
            case R.id.search:
                searchPanel();
                break;
            case R.id.redo:
                editView.redo();
                break;
            case R.id.read_only:
                search_pad.setVisibility(View.GONE);
                toggleEditMode();
                break;
            case R.id.openFile:
                showOpenFileDialog();
                break;
            case R.id.gotoLine:
                showGotoLineDialog();
                break;
            case R.id.changeSyntax:
                _syntaxSelection();
                break;
            case R.id.preference:
                menuStyle();
                break;
            case R.id.save:
                break;
            case R.id.delete_line:
                editView.deleteLine();
                return true;

            case R.id.empty_line:
                editView.emptyLine();
                return true;

            case R.id.replace_line:
                editView.replaceLine();
                return true;

            case R.id.duplicate_line:
                editView.duplicateLine();
                return true;

            case R.id.toggle_comment:
                editView.toggleComment();
                return true;

            case R.id.copy_line:
                editView.copyLine();
                return true;

            case R.id.cut_line:
                editView.cutLine();
                return true;

            case R.id.convert_uppercase:
                editView.convertSelectionToUpperCase();
                return true;

            case R.id.convert_lowercase:
                editView.convertSelectionToLowerCase();
                return true;

            case R.id.increase_indent:
                editView.increaseIndent();
                return true;

            case R.id.decrease_indent:
                editView.decreaseIndent();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void _syntaxSelection() {
        final AlertDialog.Builder d_build = new AlertDialog.Builder(MainActivity.this);
        d_build.setTitle("Syntax");
        String[] items = {"Text", "Smali", "Xml", "Java"};
        int checkedItem = (int) _getThemePosition();
        d_build.setSingleChoiceItems(items, checkedItem, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                _savePosition((double) which, "syntax_position");
                switch (which) {
                    case 0:
                        editView.setSyntaxLanguageFileName(null);
                        break;
                    case 1:
                        editView.setSyntaxLanguageFileName("smali.json");
                        break;
                    case 2:
                        editView.setSyntaxLanguageFileName("xml.json");
                        break;
                    case 3:
                        editView.setSyntaxLanguageFileName("java.json");
                        break;
                }
                dialog.dismiss();
            }
        });
        d_build.setPositiveButton("Close", null);
        d_build.show();
    }

    public void menuStyle() {
        final AlertDialog.Builder d_build = new AlertDialog.Builder(MainActivity.this);
        d_build.setTitle("Floating Menu Style");
        String[] items = {"Show all", "Show title only", "Show icon only"};
        int checkedItem = (int) _getMenuStyle();
        d_build.setSingleChoiceItems(items, checkedItem, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                _savePosition((double) which, "menu_style");
                switch (which) {
                    case 0:
                        editView.setMenuStyle(ClipboardPanel.MenuDisplayMode.ICON_AND_TEXT);
                        break;
                    case 1:
                        editView.setMenuStyle(ClipboardPanel.MenuDisplayMode.TEXT_ONLY);
                        break;
                    case 2:
                        editView.setMenuStyle(ClipboardPanel.MenuDisplayMode.ICON_ONLY);
                        break;
                }
                dialog.dismiss();
            }
        });
        d_build.setPositiveButton("Close", null);
        d_build.show();
    }

    public double _getThemePosition() {
        if (editor_pref.contains("syntax_position")) {
            return ((double) editor_pref.getInt("syntax_position", 0));
        } else {
            return (0);
        }
    }

    public double _getMenuStyle() {
        if (editor_pref.contains("menu_style")) {
            return ((double) editor_pref.getInt("menu_style", 0));
        } else {
            return (2);
        }
    }

    public void _savePosition(final double _position, String name) {
        SharedPreferences.Editor editor = editor_pref.edit();
        editor.putInt(name, (int) _position);
        editor.apply();
    }

    private void showGotoLineDialog() {
        final View v = getLayoutInflater().inflate(R.layout.dialog_gotoline, null);
        final EditText lineEdit = v.findViewById(R.id.lineEdit);
        lineEdit.setHint("1.." + editView.getLineCount());
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(v);
        builder.setTitle("goto line");

        builder.setPositiveButton("goto", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dia, int which) {
                String line = lineEdit.getText().toString();
                if (!line.isEmpty()) {
                    editView.gotoLine(Integer.parseInt(line));
                }
            }
        });

        builder.setCancelable(true).show();
    }

    private void showOpenFileDialog() {
        View v = getLayoutInflater().inflate(R.layout.dialog_openfile, null);
        final EditText pathEdit = v.findViewById(R.id.pathEdit);
        String path = mSharedPreference.getString("path", "");
        if (path.isEmpty())
            pathEdit.setHint("please enter the file path");
        else
            pathEdit.setText(path);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(v);
        builder.setTitle("open file");

        builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dia, int which) {
                String pathname = pathEdit.getText().toString();
                if (!pathname.isEmpty()) {
                    mSharedPreference.edit().putString("path", pathname).commit();
                    new ReadFileThread().execute(pathname);
                }
            }
        });
        builder.setCancelable(true).show();
    }

    // read file
    class ReadFileThread extends AsyncTask<String, Integer, Boolean> {

        @Override
        protected void onPreExecute() {
            // TODO: Implement this method
            super.onPreExecute();
            editView.setEditedMode(false);
            mHandler.sendEmptyMessage(0);
            mIndeterminateBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected Boolean doInBackground(String... params) {
            // TODO: Implement this method
            Path path = Paths.get(params[0]);
            try {
                // detect the file charset
                String charset = UniversalDetector.detectCharset(path.toFile());
                if (charset != null)
                    mDefaultCharset = Charset.forName(charset);

                // FIXED: Read entire file as single string (avoids incremental appends/gap shifts)
                String fullText = readFile(path.toString());

                // Replace buffer wholesale (like setText, but async)
                GapBuffer newBuffer = new GapBuffer(fullText);
                editView.setBuffer(newBuffer); // Assumes mTextView is your EditView; adjust if
                // needed

            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }

            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            // TODO: Implement this method
            super.onPostExecute(result);
            editView.setEditedMode(true);
            mHandler.sendEmptyMessage(0);
            mIndeterminateBar.setVisibility(View.GONE);
        }
    }

    // write file
    class WriteFileThread extends AsyncTask<String, Integer, Boolean> {

        @Override
        protected Boolean doInBackground(String... params) {
            // TODO: Implement this method
            Path path = Paths.get(params[0]);

            try {
                BufferedWriter bufferWrite = null;
                bufferWrite = Files.newBufferedWriter(path, mDefaultCharset,
                        StandardOpenOption.WRITE);
                bufferWrite.write(editView.getBuffer().toString());
                bufferWrite.flush();
                bufferWrite.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            // TODO: Implement this method
            super.onPostExecute(result);
            Toast.makeText(getApplicationContext(), "saved success!", Toast.LENGTH_SHORT).show();
        }
    }

    private void searchPanel() {
        edittext_find.requestFocus();

        search_pad.setVisibility(View.VISIBLE);
        if (!editView.getEditedMode()) {
            replace_btn.setEnabled(false);
            replace_btn.setTextColor(Color.parseColor("#EAEAEA"));
        } else {
            replace_btn.setTextColor(Color.parseColor("#111111"));
            replace_btn.setEnabled(true);
        }
        replace_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                replace_all_btn.setTextColor(Color.parseColor("#111111"));
                replace_all_btn.setEnabled(true);
                if (linear_rep.getVisibility() == View.VISIBLE)
                    editView.replaceFirst(edittext_replace.getText().toString());
                else
                    linear_rep.setVisibility(View.VISIBLE);
            }
        });
        replace_all_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                editView.replaceAll(edittext_replace.getText().toString());
            }
        });
        next_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                editView.next();
            }
        });
        previous_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                editView.previous();
            }
        });
        edittext_find.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {
                    // Only regex implented here
                    editView.find(s.toString());
                } catch (Exception e) {
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        item_menu.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                PopupMenu popup = new PopupMenu(MainActivity.this, item_menu);
                popup.inflate(R.menu.menu_search_options);
                popup.getMenu().findItem(R.id.search_option_regex).setChecked(true);
                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem item) {
                        int id = item.getItemId();
                        switch (id) {
                            case R.id.search_option_regex:
                                // to do
                                break;
                            case R.id.search_option_whole_word:
                                // to do
                                break;
                            case R.id.search_option_match_case:
                                // to do
                                break;
                            case R.id.close_search_options:
                                search_pad.setVisibility(View.GONE);
                                break;
                        }
                        return true;
                    }
                });
                popup.show();
            }
        });
    }

    public void addFunctionBar(LinearLayout container, final EditView editView) {
        Toast.makeText(getApplication(), "A basic implantation has done here.. Currently i am studing about it to fix the known issues", Toast.LENGTH_SHORT).show();
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.removeAllViews();

        for (String symbol : SYMBOLS) {

            final TextView tv = new TextView(container.getContext());
            tv.setText(symbol);
            tv.setTag(symbol);
            tv.setBackground(getSelectableBackground());
            tv.setTextSize(18f);
            tv.setTextColor(Color.parseColor("#111111"));
            tv.setPadding(30, 20, 30, 20);
            tv.setGravity(Gravity.CENTER);

            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (edittext_find.hasFocus()) {
                        String str = new String(edittext_find.getText().toString());
                        edittext_find.setText(str.concat(tv.getText().toString()));
                    } else {
                        editView.insertText(tv.getText().toString());
                    }
                }
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT
            );

            container.addView(tv, lp);
        }
    }

    private Drawable getSelectableBackground() {
        TypedValue outValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);

        if (Build.VERSION.SDK_INT >= 21) {
            return getResources().getDrawable(outValue.resourceId, getTheme());
        } else {
            return getResources().getDrawable(outValue.resourceId);
        }
    }

    public static String readFile(String path) {
        StringBuilder sb = new StringBuilder();
        FileReader fr = null;
        try {
            fr = new FileReader(new File(path));

            char[] buff = new char[1024];
            int length = 0;

            while ((length = fr.read(buff)) > 0) {
                sb.append(new String(buff, 0, length));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fr != null) {
                try {
                    fr.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return sb.toString();
    }

}
