package ru.logunov.bydsplit;
public final class AdbRemoteWatchdogPolicyTest {
 public static void main(String[] args) {
  if(!AdbRemoteWatchdogPolicy.shouldRestart(true,false,1000,0))throw new AssertionError("initial restart");
  if(AdbRemoteWatchdogPolicy.shouldRestart(false,false,500000,1))throw new AssertionError("disabled");
  if(AdbRemoteWatchdogPolicy.shouldRestart(true,true,500000,1))throw new AssertionError("healthy");
  if(AdbRemoteWatchdogPolicy.shouldRestart(true,null,500000,1))throw new AssertionError("unknown is not crashed");
  if(AdbRemoteWatchdogPolicy.shouldRestart(true,false,120000,1000))throw new AssertionError("cooldown");
  if(!AdbRemoteWatchdogPolicy.shouldRestart(true,false,121000,1000))throw new AssertionError("retry");
  if(!AdbRemoteWatchdogPolicy.shouldRestart(true,false,1000,500000))throw new AssertionError("clock reset");
  System.out.println("ADB Remote watchdog: healthy/unknown/disabled states and retry throttle passed");
 }
}
