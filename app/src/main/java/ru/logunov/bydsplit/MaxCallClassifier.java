package ru.logunov.bydsplit;

final class MaxCallClassifier {
    static boolean isLiveCall(String packageName,String category,boolean ongoing,boolean fullScreen,int callType) {
        return "ru.oneme.app".equals(packageName)&&"call".equals(category)
            && (ongoing||fullScreen||callType==1||callType==2);
    }
}
