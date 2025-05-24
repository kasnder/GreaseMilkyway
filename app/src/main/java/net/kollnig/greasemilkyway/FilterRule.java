package net.kollnig.greasemilkyway;

import java.util.Set;

/**
 * Represents a single content blocking rule.
 */
class FilterRule {
    final String packageName;
    final String targetViewId;
    final Set<String> contentDescriptions;
    final int color;
    final String description;
    boolean enabled;

    FilterRule(String pkg, String viewId, Set<String> descs, int color, String description) {
        this.packageName = pkg;
        this.targetViewId = viewId;
        this.contentDescriptions = descs;
        this.color = color;
        this.description = description;
        this.enabled = true;
    }

    boolean matchesPackage(CharSequence pkgName) {
        return pkgName != null && packageName.contentEquals(pkgName);
    }
}
