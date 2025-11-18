package modder.hub.editor;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
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
import modder.hub.editor.EditView;
import modder.hub.editor.buffer.GapBuffer;
import modder.hub.editor.listener.OnTextChangedListener;
import org.mozilla.universalchardet.UniversalDetector;


public class MainActivity extends Activity {

    private EditView mEditView;

    private ProgressBar mIndeterminateBar;

    private SharedPreferences mSharedPreference;
    private SharedPreferences theme_prefs;
    private Charset mDefaultCharset = StandardCharsets.UTF_8;
    private String externalPath = File.separator;

    private final String TAG = this.getClass().getSimpleName();

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            // TODO: Implement this method
            super.handleMessage(msg);
            invalidateOptionsMenu();
        }
    };

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mIndeterminateBar = findViewById(R.id.indeterminateBar);
        mIndeterminateBar.setBackground(null);

        theme_prefs = getSharedPreferences("theme_prefs", Activity.MODE_PRIVATE);
        mSharedPreference = PreferenceManager.getDefaultSharedPreferences(this);

        mEditView = findViewById(R.id.editView);
        mEditView.setTypeface(Typeface.DEFAULT);
        if (theme_prefs.contains("selected_position")) {
            if (theme_prefs.getInt("selected_position", 0) == 1) {
                mEditView.setSyntaxLanguageFileName("smali.json");
            }
            if (theme_prefs.getInt("selected_position", 0) == 2) {
                mEditView.setSyntaxLanguageFileName("xml.json");
            }
            if (theme_prefs.getInt("selected_position", 0) == 3) {
                mEditView.setSyntaxLanguageFileName("java.json");
            }
        }
        mEditView.setSyntaxDarkMode(false);
        // mTextView.setWordWrapEnabled(true);

        mEditView.setOnTextChangedListener(new OnTextChangedListener() {
            @Override
            public void onTextChanged() {
                mHandler.sendEmptyMessage(0);
                mEditView.postInvalidate();
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
        mEditView.setEditedMode(!mEditView.getEditedMode());
        mHandler.sendEmptyMessage(0);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        // TODO: Implement this method
        super.onWindowFocusChanged(hasFocus);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // TODO: Implement this method
        MenuItem undo = menu.findItem(R.id.menu_undo);
        undo.setIcon(R.drawable.ic_undo_white_24dp);
        if (mEditView.canUndo())
            undo.setEnabled(true);
        else
            undo.setEnabled(false);

        MenuItem redo = menu.findItem(R.id.menu_redo);
        redo.setIcon(R.drawable.ic_redo_white_24dp);
        if (mEditView.canRedo())
            redo.setEnabled(true);
        else
            redo.setEnabled(false);

        MenuItem editMode = menu.findItem(R.id.menu_edit);

        if (mEditView.getEditedMode())
            editMode.setIcon(R.drawable.ic_edit_white_24dp);
        else
            editMode.setIcon(R.drawable.ic_look_white_24dp);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_undo:
                mEditView.undo();
                break;
            case R.id.menu_redo:
                mEditView.redo();
                break;
            case R.id.menu_edit:
                toggleEditMode();
                break;
            case R.id.menu_open:
                showOpenFileDialog();
                break;
            case R.id.menu_gotoline:
                showGotoLineDialog();
                break;
            case R.id.menu_syntax:
                _themeSelection();
                break;
            case R.id.menu_save:
                String path = mSharedPreference.getString("path", "");
                new WriteFileThread().execute(path);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    public void _themeSelection() {
        final AlertDialog.Builder d_build = new AlertDialog.Builder(MainActivity.this);
        d_build.setTitle("Syntax");
        String[] items = {"Text", "Smali", "Xml", "Java"};
        int checkedItem = (int) _getThemePosition();
        d_build.setSingleChoiceItems(items, checkedItem, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                _savePosition((double) which);
                switch (which) {
                    case 0:
                        mEditView.setSyntaxLanguageFileName(null);
                        break;
                    case 1:
                        mEditView.setSyntaxLanguageFileName("smali.json");
                        break;
                    case 2:
                        mEditView.setSyntaxLanguageFileName("xml.json");
                        break;
                    case 3:
                        mEditView.setSyntaxLanguageFileName("java.json");
                        break;
                }
                dialog.dismiss();
            }
        });
        d_build.setPositiveButton("Close", null);
        d_build.show();
    }

    public double _getThemePosition() {
        if (theme_prefs.contains("selected_position")) {
            return ((double) theme_prefs.getInt("selected_position", 0));
        } else {
            return (0);
        }
    }

    public void _savePosition(final double _position) {
        SharedPreferences.Editor editor = theme_prefs.edit();
        editor.putInt("selected_position", (int) _position);
        editor.apply();
    }

    private void showGotoLineDialog() {
        final View v = getLayoutInflater().inflate(R.layout.dialog_gotoline, null);
        final EditText lineEdit = v.findViewById(R.id.lineEdit);
        lineEdit.setHint("1.." + mEditView.getLineCount());
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(v);
        builder.setTitle("goto line");

        builder.setPositiveButton("goto", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dia, int which) {
                String line = lineEdit.getText().toString();
                if (!line.isEmpty()) {
                    mEditView.gotoLine(Integer.parseInt(line));
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
            mEditView.setEditedMode(false);
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
                mEditView.setBuffer(newBuffer); // Assumes mTextView is your EditView; adjust if
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
            mEditView.setEditedMode(true);
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
                bufferWrite.write(mEditView.getBuffer().toString());
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
}
