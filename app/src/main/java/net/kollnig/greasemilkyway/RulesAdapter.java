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
import android.widget.Switch;
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
        this.items.clear();

        // Add service header
        items.add(new ServiceHeaderItem());

        // Group rules by package name
        Map<String, List<FilterRule>> rulesByPackage = new HashMap<>();
        for (FilterRule rule : rules) {
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
            AppHeaderItem headerItem = (AppHeaderItem) item;

            try {
                ApplicationInfo appInfo = packageManager.getApplicationInfo(headerItem.packageName, 0);
                viewHolder.appName.setText(packageManager.getApplicationLabel(appInfo));
                viewHolder.packageName.setText(headerItem.packageName);
                viewHolder.appIcon.setImageDrawable(packageManager.getApplicationIcon(appInfo));
            } catch (PackageManager.NameNotFoundException e) {
                viewHolder.appName.setText(headerItem.packageName);
                viewHolder.packageName.setText("");
                viewHolder.appIcon.setImageResource(android.R.drawable.sym_def_app_icon);
            }
        } else if (holder instanceof RuleViewHolder && item instanceof RuleItem) {
            RuleViewHolder viewHolder = (RuleViewHolder) holder;
            RuleItem ruleItem = (RuleItem) item;
            FilterRule rule = ruleItem.rule;

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

            viewHolder.ruleSwitch.setChecked(rule.enabled);

            viewHolder.ruleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                rule.enabled = isChecked;
                config.setRuleEnabled(rule.packageName, rule.targetViewId, isChecked);

                // Notify the service to update its rules
                DistractionControlService service = DistractionControlService.getInstance();
                if (service != null) {
                    service.updateRules();
                }

                if (onRuleStateChangedListener != null) {
                    onRuleStateChangedListener.onRuleStateChanged(rule);
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

        AppHeaderViewHolder(View itemView) {
            super(itemView);
            appName = itemView.findViewById(R.id.app_name);
            packageName = itemView.findViewById(R.id.package_name);
            appIcon = itemView.findViewById(R.id.app_icon);
        }
    }

    public static class RuleViewHolder extends RecyclerView.ViewHolder {
        final TextView ruleDescription;
        final TextView ruleDetails;
        final Switch ruleSwitch;

        RuleViewHolder(View itemView) {
            super(itemView);
            ruleDescription = itemView.findViewById(R.id.rule_description);
            ruleDetails = itemView.findViewById(R.id.rule_details);
            ruleSwitch = itemView.findViewById(R.id.rule_switch);
        }
    }
} 