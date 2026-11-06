package ru.logunov.bydsplit;

import java.nio.*;
import java.nio.charset.*;
import java.util.*;

/** OEM F197 names first, identified ECU second, explicitly labelled BYDMate address hints last.
 * Address hints adapted from BYDMate DtcBlockClass. PolyForm Noncommercial; see NOTICE. */
final class DtcEcuNames {
    static final class Name {
        final String label,source,reported;
        Name(String label,String source,String reported){this.label=label;this.source=source;this.reported=reported;}
    }
    static String ownName(byte[] uds){
        if(uds==null||uds.length<=3||uds.length>259||(uds[0]&255)!=0x62||(uds[1]&255)!=0xf1||(uds[2]&255)!=0x97)return "";
        int end=uds.length;while(end>3&&(uds[end-1]==0||uds[end-1]==32||(uds[end-1]&255)==255))end--;
        try{String name=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(uds,3,end-3)).toString().trim();
            if(name.isEmpty()||name.length()>96)return "";for(int i=0;i<name.length();i++)if(Character.isISOControl(name.charAt(i)))return "";return name;
        }catch(CharacterCodingException e){return "";}
    }
    static Name resolve(int address,byte[] identity,byte[] name){
        String own=ownName(name),cls=DtcProtocol.blockClass(address,identity);
        if(!own.isEmpty())return new Name(translate(own),"Название сообщил блок · F197",own);
        if(cls!=null)return new Name(label(cls),"Назначение определено по идентификатору блока · F194","");
        // These are cross-model hints used by BYDMate, not verified Song identities.
        switch(address){
            case 0x712:cls="ADAS";break;case 0x720:cls="BODY_LEFT";break;case 0x726:cls="BODY_REAR";break;
            case 0x747:cls="BODY_RIGHT";break;case 0x782:cls="BRAKES";break;case 0x7f1:cls="AIRBAG";break;case 0x7f2:cls="RADAR";break;
        }
        return cls==null?new Name("Блок с неизвестным назначением","Блок не сообщил распознаваемое имя",""):
            new Name("Предположительно: "+label(cls),"Предварительно по карте адресов BYD · на Song не подтверждено","");
    }
    private static String translate(String own){
        String key=own.toUpperCase(Locale.ROOT);
        switch(key){
            case "BMS":case "HVBMS":case "BMU":return "Управление тяговой батареей · "+own;
            case "ECM":case "EMS":return "Управление бензиновым двигателем · "+own;
            case "VCU":case "URE":return "Управление силовой установкой · "+own;
            case "MCU":return "Управление электроприводом · MCU";
            case "BCM":return "Кузовная электроника · BCM";
            case "IPB":return "Интегрированный блок тормозов · IPB";
            case "ABS":case "ESC":case "ESP":return "Тормоза и стабилизация · "+own;
            case "EPS":return "Электроусилитель руля · EPS";
            case "SRS":case "AIRBAG":return "Подушки безопасности · "+own;
            case "ADAS":return "Ассистенты водителя · ADAS";
            case "OBC":return "Бортовое зарядное устройство · OBC";
            case "DCDC":case "DC-DC":return "Преобразователь высокого напряжения в 12 В · "+own;
            default:return own;
        }
    }
    static String label(String cls){if(cls==null)return "Электронный блок";switch(cls){
        case "BRAKES":return "Тормоза и стабилизация";case "BMS":return "Управление тяговой батареей · BMS";case "CHARGER":return "Зарядный блок";
        case "STEERING":return "Электроусилитель руля";case "AIRBAG":return "Подушки безопасности";case "ADAS":return "Ассистенты водителя · ADAS";
        case "RADAR":return "Радар";case "BODY_LEFT":return "Кузовная электроника · левый блок";case "BODY_RIGHT":return "Кузовная электроника · правый блок";
        case "BODY_REAR":return "Кузовная электроника · задний блок";case "URE":return "Управление силовой установкой";default:return cls;}}
}
