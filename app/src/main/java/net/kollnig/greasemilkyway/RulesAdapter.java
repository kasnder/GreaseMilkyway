package net.kollnig.greasemilkyway;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.NumberPicker;
import androidx.appcompat.app.AlertDialog;



import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class RulesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_SERVICE_HEADER = 0;
    private static final int TYPE_APP_HEADER = 1;
    private static final int TYPE_RULE = 2;
    private static final int TYPE_COLLAPSIBLE_SECTION = 3;
    private static final int TYPE_NOTIFICATION_SETTINGS= 4;


    private final Context context;
    private final ServiceConfig config;
    private final PackageManager packageManager;
    private final List<Object> items = new ArrayList<>();
    private OnRuleStateChangedListener onRuleStateChangedListener;

    public RulesAdapter(Context context, ServiceConfig config) {
        this.context = context;
        this.config = config;
        this.packageManager = context.getPackageManager();
    }

    public void setOnRuleStateChangedListener(OnRuleStateChangedListener listener) {
        this.onRuleStateChangedListener = listener;
    }

    public boolean isAccessibilityServiceEnabled() {
        String serviceName = context.getPackageName() + "/" + DistractionControlService.class.getCanonicalName();
        int accessibilityEnabled = 0;
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                    context.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) {
            return false;
        }

        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (settingValue != null) {
                return settingValue.contains(serviceName);
            }
        }
        return false;
    }

    public static class CollapsibleSectionItem {
        public String title;
        public boolean isExpanded;
        
        public CollapsibleSectionItem(String title) {
            this.title = title;
            this.isExpanded = false;
        }
    }
    public class NotificationSettingsItem {
        // You can later add fields if needed, like title or default values
    }



    public class CollapsibleSectionViewHolder extends RecyclerView.ViewHolder {
        TextView headerText;
        ImageView expandIcon;
        LinearLayout contentLayout;
        Switch youtubeSwitch, igSwitch;
        private SharedPreferences prefs;

        public CollapsibleSectionViewHolder(View itemView) {
            super(itemView);
            headerText = itemView.findViewById(R.id.header_text);
            expandIcon = itemView.findViewById(R.id.expand_icon);
            contentLayout = itemView.findViewById(R.id.content_behavioral);
            youtubeSwitch = itemView.findViewById(R.id.switch_youtube_scroll);
            igSwitch = itemView.findViewById(R.id.switch_ig_nearend);

            // Initialize SharedPreferences
            prefs = context.getSharedPreferences("GreasePrefs", Context.MODE_PRIVATE);

            // Set up header click listener for expand/collapse
            itemView.findViewById(R.id.header_layout).setOnClickListener(v -> toggleExpansion());

            // Restore saved switch states
            youtubeSwitch.setChecked(prefs.getBoolean("enable_yt_scroll_reminder", true));
            igSwitch.setChecked(prefs.getBoolean("enable_ig_lockout", true));

            // Set up switch listeners to save changes
            youtubeSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                    prefs.edit().putBoolean("enable_yt_scroll_reminder", isChecked).apply());

            igSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                    prefs.edit().putBoolean("enable_ig_lockout", isChecked).apply());
        }

        private void toggleExpansion() {
            CollapsibleSectionItem item = (CollapsibleSectionItem) items.get(getAdapterPosition());
            item.isExpanded = !item.isExpanded;

            if (item.isExpanded) {
                contentLayout.setVisibility(View.VISIBLE);
                expandIcon.animate().rotation(180f).setDuration(200).start();
            } else {
                contentLayout.setVisibility(View.GONE);
                expandIcon.animate().rotation(0f).setDuration(200).start();
            }
        }
    }

    public class NotificationSettingsViewHolder extends RecyclerView.ViewHolder {
        Button btnOpenTimePicker;

        public NotificationSettingsViewHolder(View itemView) {
            super(itemView);
            btnOpenTimePicker = itemView.findViewById(R.id.btn_open_time_picker);
        }

        public void bind(Context context, SharedPreferences prefs) {
            btnOpenTimePicker.setOnClickListener(v -> {
                // Inflate the dialog layout
                View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_time_picker, null);

                // Get NumberPickers
                NumberPicker hourPicker = dialogView.findViewById(R.id.hour_picker);
                NumberPicker minutePicker = dialogView.findViewById(R.id.minute_picker);
                NumberPicker lockoutHourPicker = dialogView.findViewById(R.id.lockout_hour_picker);
                NumberPicker lockoutMinutePicker = dialogView.findViewById(R.id.lockout_minute_picker);

                // Set ranges
                hourPicker.setMinValue(0); hourPicker.setMaxValue(23);
                minutePicker.setMinValue(0); minutePicker.setMaxValue(59);
                lockoutHourPicker.setMinValue(0); lockoutHourPicker.setMaxValue(23);
                lockoutMinutePicker.setMinValue(0); lockoutMinutePicker.setMaxValue(59);

                // Load saved values
                int delayMins = prefs.getInt("notification_delay", 60);
                hourPicker.setValue(delayMins / 60);
                minutePicker.setValue(delayMins % 60);

                int lockoutMins = prefs.getInt("lockout_time", 60);
                lockoutHourPicker.setValue(lockoutMins / 60);
                lockoutMinutePicker.setValue(lockoutMins % 60);

                // Create AlertDialog
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setView(dialogView);
                AlertDialog dialog = builder.create();

                // Handle OK and Cancel
                Button okBtn = dialogView.findViewById(R.id.ok_button);
                Button cancelBtn = dialogView.findViewById(R.id.cancel_button);

                okBtn.setOnClickListener(btn -> {
                    // Get the values from the time pickers
                    int delay = hourPicker.getValue() * 60 + minutePicker.getValue(); // Convert to minutes
                    int lockout = lockoutHourPicker.getValue() * 60 + lockoutMinutePicker.getValue(); // Convert to minutes

                    // Save values to SharedPreferences
//                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                    prefs.edit()
                            .putInt("notification_delay", delay)
                            .putInt("lockout_time", lockout)
                            .apply();

                    // Close the dialog after saving
                    dialog.dismiss();
                });


                cancelBtn.setOnClickListener(btn -> dialog.dismiss());

                dialog.show();
            });
        }
    }


    public void setRules(List<FilterRule> rules) {
        // Create a map of existing rules by their hash code for state preservation
        Map<Integer, Boolean> existingStates = new HashMap<>();
        for (Object item : items) {
            if (item instanceof RuleItem) {
                FilterRule rule = ((RuleItem) item).rule;
                existingStates.put(rule.hashCode(), rule.enabled);
            }
        }

        this.items.clear();

        // Add service header
        items.add(new ServiceHeaderItem());

        //add behavioural & timer section
        items.add(new CollapsibleSectionItem("Behavioral Controls"));
        items.add(new NotificationSettingsItem());

        

        // Group rules by package name
        Map<String, List<FilterRule>> rulesByPackage = new HashMap<>();
        for (FilterRule rule : rules) {
            // Preserve the enabled state from existing rules
            Boolean existingState = existingStates.get(rule.hashCode());
            if (existingState != null) {
                rule.enabled = existingState;
            }
            rulesByPackage.computeIfAbsent(rule.packageName, k -> new ArrayList<>()).add(rule);
        }

        // Add items in order: app header followed by its rules
        for (Map.Entry<String, List<FilterRule>> entry : rulesByPackage.entrySet()) {
            String packageName = entry.getKey();
            List<FilterRule> packageRules = entry.getValue();

            // Add app header
            items.add(new AppHeaderItem(packageName));

            // Add rules for this app
            for (FilterRule rule : packageRules) {
                items.add(new RuleItem(rule));
            }
        }

        notifyDataSetChanged();
    }

    @Override

    public int getItemViewType(int position) {
        Object item = items.get(position);
        if (item instanceof ServiceHeaderItem) return TYPE_SERVICE_HEADER;
        if (item instanceof AppHeaderItem) return TYPE_APP_HEADER;
        if (item instanceof RuleItem) return TYPE_RULE;
        if (item instanceof CollapsibleSectionItem) return TYPE_COLLAPSIBLE_SECTION;
        if (item instanceof NotificationSettingsItem) return TYPE_NOTIFICATION_SETTINGS;
        return TYPE_RULE;
    }


    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case TYPE_SERVICE_HEADER:
                return new ServiceHeaderViewHolder(inflater.inflate(R.layout.item_service_header, parent, false));
            case TYPE_APP_HEADER:
                return new AppHeaderViewHolder(inflater.inflate(R.layout.item_app_group, parent, false));
            case TYPE_COLLAPSIBLE_SECTION:
                return new CollapsibleSectionViewHolder(inflater.inflate(R.layout.item_behaviour_list_collapsible, parent, false));
            case TYPE_NOTIFICATION_SETTINGS:
                return new NotificationSettingsViewHolder(inflater.inflate(R.layout.item_notification_settings, parent, false));
            default:
                return new RuleViewHolder(inflater.inflate(R.layout.item_rule, parent, false));
        }
    }




    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = items.get(position);
        if (holder instanceof ServiceHeaderViewHolder && item instanceof ServiceHeaderItem) {
            ServiceHeaderViewHolder viewHolder = (ServiceHeaderViewHolder) holder;

            // Check if service is enabled
            String serviceName = context.getPackageName() + "/" + DistractionControlService.class.getCanonicalName();
            int accessibilityEnabled = 0;
            try {
                accessibilityEnabled = Settings.Secure.getInt(
                        context.getContentResolver(),
                        Settings.Secure.ACCESSIBILITY_ENABLED);
            } catch (Settings.SettingNotFoundException e) {
                viewHolder.serviceEnabled.setChecked(false);
                return;
            }

            if (accessibilityEnabled == 1) {
                String settingValue = Settings.Secure.getString(
                        context.getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                boolean isEnabled = settingValue != null && settingValue.contains(serviceName);
                viewHolder.serviceEnabled.setChecked(isEnabled);
            } else {
                viewHolder.serviceEnabled.setChecked(false);
            }

            viewHolder.serviceEnabled.setOnClickListener(v -> {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            });
        } else if (holder instanceof AppHeaderViewHolder && item instanceof AppHeaderItem) {
            AppHeaderViewHolder viewHolder = (AppHeaderViewHolder) holder;
            AppHeaderItem appItem = (AppHeaderItem) item;
            String packageName = appItem.packageName;

            try {
                ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
                viewHolder.appName.setText(packageManager.getApplicationLabel(appInfo));
                viewHolder.appIcon.setImageDrawable(packageManager.getApplicationIcon(appInfo));
            } catch (PackageManager.NameNotFoundException e) {
                viewHolder.appName.setText(packageName);
                viewHolder.appIcon.setImageResource(android.R.drawable.sym_def_app_icon);
            }
            viewHolder.packageName.setText(packageName);

            // Set up package switch
            viewHolder.packageSwitch.setOnCheckedChangeListener(null); // Remove any existing listener
            viewHolder.packageSwitch.setChecked(!config.isPackageDisabled(packageName)); // Invert the disabled state for the switch
            viewHolder.packageSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                config.setPackageDisabled(packageName, !isChecked); // Invert the switch state for disabled state
                
                // When disabling a package, we don't change individual rule states
                // When enabling a package, we restore the individual rule states
                if (isChecked) {
                    // Update all rules for this package to their saved states
                    for (int i = position + 1; i < items.size(); i++) {
                        Object nextItem = items.get(i);
                        if (nextItem instanceof RuleItem) {
                            RuleItem ruleItem = (RuleItem) nextItem;
                            if (ruleItem.rule.packageName.equals(packageName)) {
                                // Get the saved state from SharedPreferences
                                String key = ServiceConfig.KEY_RULE_ENABLED + ruleItem.rule.hashCode();
                                boolean savedState = config.getPrefs().getBoolean(key, true);
                                ruleItem.rule.enabled = savedState;
                            }
                        } else if (nextItem instanceof AppHeaderItem) {
                            // Stop when we reach the next app header
                            break;
                        }
                    }
                }
                notifyDataSetChanged();

                // Notify the service to update its rules
                DistractionControlService service = DistractionControlService.getInstance();
                if (service != null) {
                    service.updateRules();
                }
            });
            
        }else if (holder instanceof NotificationSettingsViewHolder && item instanceof NotificationSettingsItem) {
            NotificationSettingsViewHolder viewHolder = (NotificationSettingsViewHolder) holder;
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

            // Find the summary text and update it
            TextView summaryText = viewHolder.itemView.findViewById(R.id.summary_text);

            int delayMins = prefs.getInt("notification_delay", 60);
            int lockoutMins = prefs.getInt("lockout_time", 60);
            String summary = String.format(
                    "Current: Notification Delay %dh %dm, Lockout Time %dh %dm",
                    delayMins / 60, delayMins % 60,
                    lockoutMins / 60, lockoutMins % 60
            );
            summaryText.setText(summary);

            // Show time picker on button click
            viewHolder.btnOpenTimePicker.setOnClickListener(v -> {
                View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_time_picker, null);

                NumberPicker hourPicker = dialogView.findViewById(R.id.hour_picker);
                NumberPicker minutePicker = dialogView.findViewById(R.id.minute_picker);
                NumberPicker lockoutHourPicker = dialogView.findViewById(R.id.lockout_hour_picker);
                NumberPicker lockoutMinutePicker = dialogView.findViewById(R.id.lockout_minute_picker);

                hourPicker.setMinValue(0); hourPicker.setMaxValue(23);
                minutePicker.setMinValue(0); minutePicker.setMaxValue(59);
                lockoutHourPicker.setMinValue(0); lockoutHourPicker.setMaxValue(23);
                lockoutMinutePicker.setMinValue(0); lockoutMinutePicker.setMaxValue(59);

                hourPicker.setValue(delayMins / 60);
                minutePicker.setValue(delayMins % 60);
                lockoutHourPicker.setValue(lockoutMins / 60);
                lockoutMinutePicker.setValue(lockoutMins % 60);

                AlertDialog dialog = new AlertDialog.Builder(context)
                        .setView(dialogView)
                        .create();

                dialogView.findViewById(R.id.ok_button).setOnClickListener(ok -> {
                    int newDelay = hourPicker.getValue() * 60 + minutePicker.getValue();
                    int newLockout = lockoutHourPicker.getValue() * 60 + lockoutMinutePicker.getValue();

                    prefs.edit()
                            .putInt("notification_delay", newDelay)
                            .putInt("lockout_time", newLockout)
                            .apply();

                    // Update summary text immediately
                    String newSummary = String.format(
                            "Current: Notification Delay %dh %dm, Lockout Time %dh %dm",
                            newDelay / 60, newDelay % 60,
                            newLockout / 60, newLockout % 60
                    );
                    summaryText.setText(newSummary);

                    dialog.dismiss();
                });

                dialogView.findViewById(R.id.cancel_button).setOnClickListener(cancel -> dialog.dismiss());

                dialog.show();
            });
        }




        else if (holder instanceof RuleViewHolder && item instanceof RuleItem) {
                RuleViewHolder viewHolder = (RuleViewHolder) holder;
                RuleItem ruleItem = (RuleItem) item;
                FilterRule rule = ruleItem.rule;

                // Store the position in the ViewHolder
                viewHolder.position = position;

                viewHolder.ruleDescription.setText(rule.description);

                // Build details string
                StringBuilder details = new StringBuilder();
                if (rule.targetViewId != null && !rule.targetViewId.isEmpty()) {
                    details.append("View ID: ").append(rule.targetViewId);
                }
                if (!rule.contentDescriptions.isEmpty()) {
                    if (details.length() > 0) {
                        details.append("\n");
                    }
                    details.append("Description: ").append(String.join(", ", rule.contentDescriptions));
                }
                viewHolder.ruleDetails.setText(details.toString());

                // Check if the package is disabled
                boolean isPackageDisabled = config.isPackageDisabled(rule.packageName);
                
                // Remove any existing listener to prevent duplicate callbacks
                viewHolder.ruleSwitch.setOnCheckedChangeListener(null);
                // Set the current state
                viewHolder.ruleSwitch.setChecked(rule.enabled);
                // Disable the switch if the package is disabled
                viewHolder.ruleSwitch.setEnabled(!isPackageDisabled);
                // Add the listener back
                viewHolder.ruleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    // Verify we're still bound to the same position
                    if (viewHolder.position == position) {
                        if (rule.enabled != isChecked) {  // Only update if the state actually changed
                            rule.enabled = isChecked;
                            config.setRuleEnabled(rule, isChecked);

                            // Notify the service to update its rules
                            DistractionControlService service = DistractionControlService.getInstance();
                            if (service != null) {
                                service.updateRules();
                            }

                            if (onRuleStateChangedListener != null) {
                                onRuleStateChangedListener.onRuleStateChanged(rule);
                            }
                        }
                    }
                });
            }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void refreshServiceState() {
        // Find the service header position
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) instanceof ServiceHeaderItem) {
                notifyItemChanged(i);
                break;
            }
        }
    }

    public interface OnRuleStateChangedListener {
        void onRuleStateChanged(FilterRule rule);
    }

    // Item classes for different view types
    private static class ServiceHeaderItem {
    }

    private static class AppHeaderItem {
        final String packageName;

        AppHeaderItem(String packageName) {
            this.packageName = packageName;
        }
    }

    private static class RuleItem {
        final FilterRule rule;

        RuleItem(FilterRule rule) {
            this.rule = rule;
        }
    }

    static class ServiceHeaderViewHolder extends RecyclerView.ViewHolder {
        Switch serviceEnabled;

        ServiceHeaderViewHolder(View itemView) {
            super(itemView);
            serviceEnabled = itemView.findViewById(R.id.service_enabled);
        }
    }

    static class AppHeaderViewHolder extends RecyclerView.ViewHolder {
        TextView appName;
        TextView packageName;
        ImageView appIcon;
        Switch packageSwitch;

        AppHeaderViewHolder(View itemView) {
            super(itemView);
            appName = itemView.findViewById(R.id.app_name);
            packageName = itemView.findViewById(R.id.package_name);
            appIcon = itemView.findViewById(R.id.app_icon);
            packageSwitch = itemView.findViewById(R.id.package_switch);
        }
    }

    public static class RuleViewHolder extends RecyclerView.ViewHolder {
        final TextView ruleDescription;
        final TextView ruleDetails;
        final Switch ruleSwitch;
        int position = -1;  // Track the position this ViewHolder is bound to

        RuleViewHolder(View itemView) {
            super(itemView);
            ruleDescription = itemView.findViewById(R.id.rule_description);
            ruleDetails = itemView.findViewById(R.id.rule_details);
            ruleSwitch = itemView.findViewById(R.id.rule_switch);
        }
    }
} 