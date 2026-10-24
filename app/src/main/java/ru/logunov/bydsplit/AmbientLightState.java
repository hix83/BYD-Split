package ru.logunov.bydsplit;

final class AmbientLightState {
    final int color;
    final boolean music, dynamic;
    AmbientLightState(int color,boolean music,boolean dynamic){this.color=color;this.music=music;this.dynamic=dynamic;}
    String status(){
        if(music||dynamic)return "Подсветка: "+(music?"музыка":"")+(music&&dynamic?" + ":"")+(dynamic?"переливы":"")+" · выбранный цвет";
        return "Выбранный цвет передней подсветки";
    }
}
