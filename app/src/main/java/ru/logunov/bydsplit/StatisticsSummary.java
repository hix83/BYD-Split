package ru.logunov.bydsplit;

import java.util.List;

/** Coverage-aware totals: unknown consumption never becomes a zero or dilutes a rate. */
final class StatisticsSummary {
    int trips, priced, fuelKnown, electricityKnown, incomplete;
    long moving, elapsed;
    double km, pricedKm, fuelKm, electricityKm, cost, pricedFuel, pricedElectricity;
    double litres, fuelCost, electricity, electricityCost;
    StatisticsSummary(List<StatisticsStore.Entry> entries) {
        for (StatisticsStore.Entry e : entries) {
            if (!"trip".equals(e.type)) continue;
            trips++; km+=e.km; moving+=e.moving;elapsed+=Math.max(0,e.end-e.start);
            if(e.missing>0) incomplete++;
            Double fuel=e.fuelLitres(), energy=e.electricityKwh(), total=e.cost();
            if(fuel!=null){fuelKnown++;litres+=fuel;fuelKm+=e.km;fuelCost+=e.fuelCost();}
            if(energy!=null){electricityKnown++;electricity+=energy;electricityKm+=e.km;electricityCost+=e.electricityCost();}
            if(total!=null){priced++;cost+=total;pricedKm+=e.km;pricedFuel+=total-e.electricityCost();pricedElectricity+=e.electricityCost();}
        }
    }
    Double costPerKm(){return pricedKm>=.1?cost/pricedKm:null;}
    Double fuelPer100(){return fuelKm>=.1?litres*100/fuelKm:null;}
    Double electricityPer100(){return electricityKm>=.1?electricity*100/electricityKm:null;}
}
