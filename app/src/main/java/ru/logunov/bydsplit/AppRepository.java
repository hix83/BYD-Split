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

    List<AppEntry> loadShortcutApps(boolean music){
        Set<String> declared=new HashSet<>();
        Intent intent=new Intent(Intent.ACTION_MAIN).addCategory(music?Intent.CATEGORY_APP_MUSIC:Intent.CATEGORY_APP_MESSAGING);
        for(ResolveInfo match:packageManager.queryIntentActivities(intent,PackageManager.MATCH_ALL))
            if(match.activityInfo!=null)declared.add(match.activityInfo.packageName);
        List<AppEntry> result=new ArrayList<>();
        for(AppEntry app:loadLaunchableApps()){
            String pkg=app.component.getPackageName(),label=app.label.toLowerCase(Locale.ROOT);
            int category=android.content.pm.ApplicationInfo.CATEGORY_UNDEFINED;
            try{category=packageManager.getApplicationInfo(pkg,0).category;}catch(PackageManager.NameNotFoundException ignored){}
            boolean matches=declared.contains(pkg)||(music?
                category==android.content.pm.ApplicationInfo.CATEGORY_AUDIO||pkg.equals("ru.yandex.music")||pkg.equals("com.yandex.music")||pkg.equals("com.spotify.music")||pkg.equals("com.maxmpz.audioplayer")||label.contains("music")||label.contains("музык"):
                category==android.content.pm.ApplicationInfo.CATEGORY_SOCIAL||pkg.equals("ru.oneme.app")||pkg.startsWith("org.telegram.")||pkg.startsWith("com.whatsapp")||pkg.equals("com.viber.voip")||pkg.equals("org.thoughtcrime.securesms")||pkg.equals("com.vkontakte.android")||pkg.equals("com.tencent.mm")||pkg.equals("com.google.android.apps.messaging")||pkg.equals("com.android.mms")||label.contains("messenger")||label.contains("мессенджер"));
            if(matches)result.add(app);
        }
        return result;
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
