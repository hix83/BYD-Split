package ru.logunov.bydsplit;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class AppRepository {
    private final Context context;
    private final PackageManager packageManager;

    AppRepository(Context context) {
        this.context = context;
        this.packageManager = context.getPackageManager();
    }

    List<AppEntry> loadLaunchableApps() {
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> matches = packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.MATCH_ALL
        );
        List<AppEntry> result = new ArrayList<>();
        Set<ComponentName> seen = new HashSet<>();

        for (ResolveInfo match : matches) {
            if (match.activityInfo == null
                    || context.getPackageName().equals(match.activityInfo.packageName)) {
                continue;
            }
            ComponentName component = new ComponentName(
                    match.activityInfo.packageName,
                    match.activityInfo.name
            );
            if (!seen.add(component)) {
                continue;
            }
            CharSequence rawLabel = match.loadLabel(packageManager);
            String label = rawLabel == null
                    ? match.activityInfo.packageName
                    : rawLabel.toString();
            Drawable icon = match.loadIcon(packageManager);
            result.add(new AppEntry(label, component, icon));
        }

        result.sort(Comparator.comparing(
                entry -> entry.label.toLowerCase(Locale.getDefault())
        ));
        return Collections.unmodifiableList(result);
    }

    AppEntry resolve(ComponentName component) {
        if (component == null) {
            return null;
        }
        try {
            android.content.pm.ActivityInfo info = packageManager.getActivityInfo(component, 0);
            CharSequence rawLabel = info.loadLabel(packageManager);
            return new AppEntry(
                    rawLabel == null ? component.getPackageName() : rawLabel.toString(),
                    component,
                    info.loadIcon(packageManager)
            );
        } catch (PackageManager.NameNotFoundException error) {
            return null;
        }
    }
}
