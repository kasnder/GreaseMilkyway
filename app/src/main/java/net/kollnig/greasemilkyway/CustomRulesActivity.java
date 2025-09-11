package net.kollnig.greasemilkyway;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class CustomRulesActivity extends AppCompatActivity {
    private EditText rulesEditor;
    private ServiceConfig config;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_rules);

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
} 