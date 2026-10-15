package ru.logunov.bydsplit;
public final class SeagullUpdateChannelTest {
 public static void main(String[] args) {
  check(true,"seagull-v0.9.2-alpha.2","0.9.2-seagull-alpha.1");
  check(false,"seagull-v0.9.2-alpha.1","0.9.2-seagull-alpha.2");
  check(false,"seagull-v0.9.2-alpha.2","0.9.2-seagull-alpha.2");
  check(true,"seagull-v0.9.2-alpha.10","0.9.2-seagull-alpha.2");
  check(true,"seagull-v0.9.2-beta.1","0.9.2-seagull-alpha.10");
  check(true,"seagull-v0.9.2-rc.1","0.9.2-seagull-beta.10");
  check(true,"seagull-v0.9.2","0.9.2-seagull-rc.2");
  check(false,"seagull-v0.9.2-alpha.3","0.9.2-seagull");
  check(true,"seagull-v0.10.0-alpha.1","0.9.2-seagull");
  for(String invalid:new String[]{"v10.0.0","seagull-v0.9.2-song","seagull-v1.2.3-alpha.0","seagull-v2147483648.2.3","seagull-v-1.2.3","seagull-v1.2.3/evil"})
   if(SeagullUpdateChannel.versionFromTag(invalid)!=null)throw new AssertionError(invalid);
  if(SeagullUpdateChannel.acceptsApkVersion("0.10.0")||SeagullUpdateChannel.acceptsApkVersion("0.10.0-song"))throw new AssertionError("Song APK accepted");
  if(!"BYD-Split-Seagull-0.9.2-alpha.1.apk".equals(SeagullUpdateChannel.assetName("seagull-v0.9.2-alpha.1")))throw new AssertionError("existing release naming");
  if(!SeagullUpdateChannel.newerTag("seagull-v0.9.2-alpha.10","seagull-v0.9.2-alpha.2"))throw new AssertionError("release ordering");
  System.out.println("Seagull update channel: isolation, prerelease ordering and asset naming passed");
 }
 private static void check(boolean expected,String tag,String installed){if(SeagullUpdateChannel.newer(tag,installed)!=expected)throw new AssertionError(tag+" / "+installed);}
}
