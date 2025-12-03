package net.kollnig.greasemilkyway;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import com.google.android.material.materialswitch.MaterialSwitch;
import android.widget.TextView;

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

    private final Context context;
    private final ServiceConfig config;
    private final PackageManager packageManager;
    private final List<Object> items = new ArrayList<>();
    private OnRuleStateChangedListener onRuleStateChangedListener;
    private boolean serviceEnabled = false;

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

            // Sort rules by description
            packageRules.sort((r1, r2) -> r1.description.compareToIgnoreCase(r2.description));

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
            
            // Grey out if service is disabled
            viewHolder.itemView.setAlpha(serviceEnabled ? 1.0f : 0.4f);
            viewHolder.packageSwitch.setEnabled(serviceEnabled);
            String packageName = appItem.packageName;

            try {
                ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
                viewHolder.appName.setText(packageManager.getApplicationLabel(appInfo));
                viewHolder.appIcon.setImageDrawable(packageManager.getApplicationIcon(appInfo));
                viewHolder.packageName.setText(packageName); // Always set package name
            } catch (PackageManager.NameNotFoundException e) {
                viewHolder.appName.setText(packageName); // Show package name as title
                viewHolder.packageName.setText(context.getString(R.string.app_not_installed)); // Show 'App not installed' as subtitle
                viewHolder.appIcon.setImageResource(android.R.drawable.sym_def_app_icon);
            }

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
        } else if (holder instanceof RuleViewHolder && item instanceof RuleItem) {
            RuleViewHolder viewHolder = (RuleViewHolder) holder;
            RuleItem ruleItem = (RuleItem) item;
            FilterRule rule = ruleItem.rule;
            
            // Grey out if service is disabled
            viewHolder.itemView.setAlpha(serviceEnabled ? 1.0f : 0.4f);

            viewHolder.ruleDescription.setText(rule.description);

            // Hide ruleDetails by default
            viewHolder.ruleDetails.setVisibility(View.GONE);

            // If the rule has contentDescriptions (desc field), show the alert
            if (!rule.contentDescriptions.isEmpty()) {
                viewHolder.ruleDetails.setText(context.getString(R.string.rule_requires_english));
                viewHolder.ruleDetails.setVisibility(View.VISIBLE);
            }

            // Check if the package is disabled
            boolean isPackageDisabled = config.isPackageDisabled(rule.packageName);
            
            // Remove any existing listener to prevent duplicate callbacks
            viewHolder.ruleSwitch.setOnCheckedChangeListener(null);
            // Set the current state
            viewHolder.ruleSwitch.setChecked(rule.enabled);
            // Disable the switch if the package is disabled OR service is disabled
            viewHolder.ruleSwitch.setEnabled(serviceEnabled && !isPackageDisabled);
            // Add the listener back
            viewHolder.ruleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                int adapterPosition = viewHolder.getAdapterPosition();
                if (adapterPosition == RecyclerView.NO_POSITION) return;
                Object currentItem = items.get(adapterPosition);
                if (currentItem instanceof RuleItem) {
                    RuleItem currentRuleItem = (RuleItem) currentItem;
                    FilterRule currentRule = currentRuleItem.rule;
                    if (currentRule.enabled != isChecked) {  // Only update if the state actually changed
                        currentRule.enabled = isChecked;
                        config.setRuleEnabled(currentRule, isChecked);

                        // Notify the service to update its rules
                        DistractionControlService service = DistractionControlService.getInstance();
                        if (service != null) {
                            service.updateRules();
                        }

                        if (onRuleStateChangedListener != null) {
                            onRuleStateChangedListener.onRuleStateChanged(currentRule);
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
        boolean newServiceEnabled = isAccessibilityServiceEnabled();
        if (this.serviceEnabled != newServiceEnabled) {
            this.serviceEnabled = newServiceEnabled;
            notifyDataSetChanged(); // Refresh all items to update alpha
        } else {
            // Find the service header position
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i) instanceof ServiceHeaderItem) {
                    notifyItemChanged(i);
                    break;
                }
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
        MaterialSwitch serviceEnabled;
        View helpToggleButton;
        ImageView helpChevron;
        View helpContent;
        boolean isHelpExpanded = false;

        ServiceHeaderViewHolder(View itemView) {
            super(itemView);
            serviceEnabled = itemView.findViewById(R.id.service_enabled);
            helpToggleButton = itemView.findViewById(R.id.help_toggle_button);
            helpChevron = itemView.findViewById(R.id.help_chevron);
            helpContent = itemView.findViewById(R.id.help_content);
            
            // Setup collapse/expand toggle
            helpToggleButton.setOnClickListener(v -> {
                isHelpExpanded = !isHelpExpanded;
                helpContent.setVisibility(isHelpExpanded ? View.VISIBLE : View.GONE);
                helpChevron.setRotation(isHelpExpanded ? 180 : 0);
            });
        }
    }

    static class AppHeaderViewHolder extends RecyclerView.ViewHolder {
        TextView appName;
        TextView packageName;
        ImageView appIcon;
        MaterialSwitch packageSwitch;

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
        final MaterialSwitch ruleSwitch;
        // Removed position field

        RuleViewHolder(View itemView) {
            super(itemView);
            ruleDescription = itemView.findViewById(R.id.rule_description);
            ruleDetails = itemView.findViewById(R.id.rule_details);
            ruleSwitch = itemView.findViewById(R.id.rule_switch);
        }
    }
} 