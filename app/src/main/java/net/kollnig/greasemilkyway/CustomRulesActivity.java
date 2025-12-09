package net.kollnig.greasemilkyway;

import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowInsetsController;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;

public class CustomRulesActivity extends AppCompatActivity {
    private EditText rulesEditor;
    private ServiceConfig config;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_rules);
        
        // Setup navigation bar color to match app background
        setupNavigationBarColor();

        // Setup toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(R.string.custom_rules_title);

        // Initialize config
        config = new ServiceConfig(this);

        // Initialize views
        rulesEditor = findViewById(R.id.rules_editor);
        
        // Load existing custom rules
        String[] customRules = config.getCustomRules();
        if (customRules != null) {
            rulesEditor.setText(String.join("\n", customRules));
        }
        
        // Setup README link (after loading rules to avoid any interference)
        TextView readmeLink = findViewById(R.id.readme_link);
        if (readmeLink != null) {
            setupReadmeLink(readmeLink);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveRules();
    }

    private void saveRules() {
        String rulesText = rulesEditor.getText().toString();
        String[] rules = rulesText.split("\n");
        
        // Parse rules
        FilterRuleParser parser = new FilterRuleParser();
        try {
            parser.parseRules(rules);
            config.saveCustomRules(rules);
            
            // Update service rules
            DistractionControlService service = DistractionControlService.getInstance();
            if (service != null) {
                service.updateRules();
            }
        } catch (Exception e) {
            Toast.makeText(this, R.string.invalid_rules, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupNavigationBarColor() {
        // Get the background color from theme
        int backgroundColor = getResources().getColor(R.color.background_main, getTheme());
        // Set navigation bar color to match app background
        getWindow().setNavigationBarColor(backgroundColor);
        
        // Set navigation bar icon color: grey in light mode, white in dark mode
        boolean isLightMode = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) 
                             != Configuration.UI_MODE_NIGHT_YES;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                int appearance = isLightMode ? WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS : 0;
                controller.setSystemBarsAppearance(appearance, WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        } else {
            View decorView = getWindow().getDecorView();
            int flags = decorView.getSystemUiVisibility();
            if (isLightMode) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            decorView.setSystemUiVisibility(flags);
        }
    }

    private void setupReadmeLink(TextView textView) {
        if (textView == null) {
            return;
        }
        
        try {
            String fullText = getString(R.string.custom_rules_readme_link);
            SpannableString spannableString = new SpannableString(fullText);
            
            int start = fullText.indexOf("README");
            if (start >= 0) {
                int end = start + "README".length();
                
                // Make "README" clickable (no special styling)
                ClickableSpan clickableSpan = new ClickableSpan() {
                    @Override
                    public void onClick(View widget) {
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/kasnder/GreaseMilkyway/blob/main/README.md"));
                        startActivity(browserIntent);
                    }
                    
                    @Override
                    public void updateDrawState(android.text.TextPaint ds) {
                        // Keep default text color, underline to show it's a link
                        ds.setUnderlineText(true);
                        ds.setColor(textView.getCurrentTextColor());
                    }
                };
                
                spannableString.setSpan(clickableSpan, start, end, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            
            textView.setText(spannableString);
            textView.setMovementMethod(LinkMovementMethod.getInstance());
        } catch (Exception e) {
            // If setup fails, just set the plain text
            textView.setText(getString(R.string.custom_rules_readme_link));
        }
    }
} 