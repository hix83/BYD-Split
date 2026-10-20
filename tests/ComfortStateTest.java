package ru.logunov.bydsplit;
import java.util.Arrays;
import static ru.logunov.bydsplit.ComfortState.Field.*;
public final class ComfortStateTest {
    private static void check(boolean b){if(!b)throw new AssertionError();}
    private static void command(ComfortState s,ComfortState.Field f,int value,String method,int expected,int... args){
        ComfortState.Command c=s.command(f,value);check(c.method.equals(method)&&c.expected==expected&&Arrays.equals(c.args,args));
    }
    private static void reject(ComfortState s,ComfortState.Field f,int value){try{s.command(f,value);throw new AssertionError();}catch(IllegalArgumentException expected){}}
    public static void main(String[] args){
        ComfortState s=ComfortState.parse("UNIT=1\nDRIVER_TEMP=22\nPASSENGER_TEMP=23\nMAX=0\nVENT=0\nPOWER=1\nAUTO=0\nFAN=3\nWIND=2\nFRONT=0\nREAR=0\nDUAL=1\nDRIVER_HEAT=1\nPASSENGER_HEAT=3\nDRIVER_VENT=2\nPASSENGER_VENT=3\nWHEEL=1\nSDK log");
        command(s,DRIVER_TEMP,24,"setAcTemperature",24,1,24,0,1);
        command(s,PASSENGER_TEMP,25,"setAcTemperature",25,2,25,0,1);
        command(s,POWER,0,"stop",0,0);command(s,POWER,1,"start",1,0);
        command(s,AUTO,0,"setAcControlMode",0,0,0);
        command(s,MAX,1,"setAcMaxCoolingState",1,1);command(s,VENT,1,"setAcVentilationState",1,0,1);
        check(Arrays.equals(s.acFeatures(MAX),new String[]{"AC_MAX_COOLING_STATE_SET"}));
        check(Arrays.equals(s.acValues(MAX,1),new int[]{1}));
        check(Arrays.equals(s.acValues(VENT,1),new int[]{1,0}));reject(s,MAX,2);reject(s,VENT,-1);
        check(ComfortState.parse("MAX=255\nVENT=-1").get(MAX)==null);
        for(int mode=1;mode<=7;mode++)check(ComfortState.windMode(ComfortState.windMask(mode))==mode);
        check(ComfortState.windMode(0)==-1);check(ComfortState.windMask(99)==0);
        command(s,FRONT,1,"setAcDefrostState",1,0,1,1);command(s,REAR,1,"setAcDefrostState",1,0,2,1);
        command(s,DRIVER_HEAT,2,"comfort",3,1,2);command(s,PASSENGER_HEAT,0,"comfort",1,2,0);
        command(s,DRIVER_VENT,1,"comfort",2,3,1);command(s,PASSENGER_VENT,2,"comfort",3,4,2);command(s,WHEEL,1,"comfort",2,0,1);
        check(Arrays.equals(s.acFeatures(DRIVER_TEMP),new String[]{"AC_TEMPERATURE_UNIT_SET","AC_TEMP_MAIN_SET","AC_TEMP_DEPUTY_SET","AC_TEMP_REAR_SET","AC_CTRL_SOURCE_SET"}));
        check(Arrays.equals(s.acValues(DRIVER_TEMP,24),new int[]{1,24,0,0,0}));
        check(Arrays.equals(s.acValues(PASSENGER_TEMP,25),new int[]{1,0,25,0,0}));
        check(Arrays.equals(s.acValues(POWER,0),new int[]{0,0}));
        check(Arrays.equals(s.acFeatures(REAR),new String[]{"AC_DEFROST_REAR_STATE_SET","AC_CTRL_SOURCE_SET"}));
        check(s.nextUiValue(DRIVER_HEAT)==2);check(s.nextUiValue(PASSENGER_HEAT)==1);
        check(ComfortState.parse("DRIVER_HEAT=2").nextUiValue(DRIVER_HEAT)==0);
        check(ComfortState.parse("WHEEL=1").nextUiValue(WHEEL)==1);
        check(ComfortState.parse("WHEEL=2").nextUiValue(WHEEL)==0);
        check(ComfortState.parse("REAR=0").nextUiValue(REAR)==1);
        check(ComfortState.parse("REAR=1").nextUiValue(REAR)==0);
        check(ComfortState.parse("DRIVER_HEAT=4").get(DRIVER_HEAT)==null);
        reject(s,DRIVER_HEAT,3);reject(s,DRIVER_VENT,3);
        reject(s,WHEEL,2);reject(s,DRIVER_HEAT,-1);reject(s,DRIVER_TEMP,34);reject(s,WIND,0);reject(s,UNIT,0);
        ComfortState invalid=ComfortState.parse("POWER=-1\nDRIVER_HEAT=0\nFAN=255\nWHEEL=3\nUNIT=2\nDRIVER_TEMP=22\nREAR=invalid");
        check(!invalid.any());reject(invalid,POWER,1);reject(invalid,DRIVER_HEAT,1);reject(invalid,DRIVER_TEMP,22);
        ComfortState fahrenheit=ComfortState.parse("UNIT=0\nDRIVER_TEMP=72\nPASSENGER_TEMP=91");
        command(fahrenheit,DRIVER_TEMP,73,"setAcTemperature",73,1,73,0,0);reject(fahrenheit,DRIVER_TEMP,22);
        check(ComfortState.parse("DRIVER_TEMP=22").get(DRIVER_TEMP)==null);
        check(ComfortState.parse("FAN=0").get(FAN)==0);
        System.out.println("ComfortStateTest passed");
    }
}
