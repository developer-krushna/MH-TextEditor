package modder.hub.editor;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.HashMap;
import java.util.Map;

public class DebugActivity extends Activity {

    private static final Map<String, String> exceptionMap = new HashMap<String, String>() {
        {
            put("StringIndexOutOfBoundsException", "Invalid string operation\n");
            put("IndexOutOfBoundsException", "Invalid list operation\n");
            put("ArithmeticException", "Invalid arithmetical operation\n");
            put("NumberFormatException", "Invalid toNumber block operation\n");
            put("ActivityNotFoundException", "Invalid intent operation\n");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String errorMessage = "";
        if (intent != null) {
            errorMessage = intent.getStringExtra("error");
        }

        SpannableStringBuilder formattedMessage = new SpannableStringBuilder();

        if (errorMessage != null && !errorMessage.isEmpty()) {
            // Split lines for parsing
            String[] split = errorMessage.split("\n");
            String firstLine = split[0].trim();

            // Extract only simple exception class name (no package prefix)
            String exceptionType = firstLine;
            int dotIndex = firstLine.lastIndexOf('.');
            if (dotIndex != -1 && dotIndex < firstLine.length() - 1) {
                exceptionType = firstLine.substring(dotIndex + 1);
            }

            // Remove trailing message after colon if present
            int colonIndex = exceptionType.indexOf(':');
            if (colonIndex != -1) {
                exceptionType = exceptionType.substring(0, colonIndex).trim();
            }

            // Lookup friendly message
            String friendlyMessage = exceptionMap.getOrDefault(exceptionType, "");

            if (!friendlyMessage.isEmpty()) {
                formattedMessage.append(friendlyMessage).append("\n");
            }

            // Append the full error content (stack trace)
            formattedMessage.append(errorMessage);
        } else {
            formattedMessage.append("No error message available.");
        }

        // Set activity title
        setTitle(getTitle() + " Crashed");

        // Setup TextView
        TextView errorView = new TextView(this);
        errorView.setText(formattedMessage);
        errorView.setTextIsSelectable(true);
        errorView.setTypeface(Typeface.MONOSPACE);
        errorView.setPadding(32, 32, 32, 32);

        // Add scroll support (both directions)
        HorizontalScrollView hscroll = new HorizontalScrollView(this);
        ScrollView vscroll = new ScrollView(this);
        vscroll.addView(errorView);
        hscroll.addView(vscroll);

        setContentView(hscroll);
    }
}
