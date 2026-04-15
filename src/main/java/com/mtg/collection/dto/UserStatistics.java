package com.mtg.collection.dto;

import com.mtg.collection.service.StatisticsService.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class UserStatistics {
    private String user;
    private int totalUploads;
    private LocalDate lastUpload;
    private int totalCards;
    private double totalValue;
    private List<CardWithPrice> mostExpensiveCards;
    private List<SetCount> topSetsByCount;
    private List<SetValue> topSetsByValue;
    private List<SetCompletion> completeSets;
    private List<SetCompletion> nearCompleteSets;   // 90%+
    private List<SetCompletion> nearComplete80;      // 80–89%
    private List<SetCompletion> nearComplete70;      // 70–79%
    private List<SetCompletion> nearComplete65;      // 65–69%
    private double dailyChange;
    private List<CardPriceChange> topWinners;
    private List<CardPriceChange> topLosers;

    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }
    public int getTotalUploads() { return totalUploads; }
    public void setTotalUploads(int totalUploads) { this.totalUploads = totalUploads; }
    public LocalDate getLastUpload() { return lastUpload; }
    public void setLastUpload(LocalDate lastUpload) { this.lastUpload = lastUpload; }
    public int getTotalCards() { return totalCards; }
    public void setTotalCards(int totalCards) { this.totalCards = totalCards; }
    public double getTotalValue() { return totalValue; }
    public void setTotalValue(double totalValue) { this.totalValue = totalValue; }
    public List<CardWithPrice> getMostExpensiveCards() { return mostExpensiveCards; }
    public void setMostExpensiveCards(List<CardWithPrice> mostExpensiveCards) { this.mostExpensiveCards = mostExpensiveCards; }
    public List<SetCount> getTopSetsByCount() { return topSetsByCount; }
    public void setTopSetsByCount(List<SetCount> topSetsByCount) { this.topSetsByCount = topSetsByCount; }
    public List<SetValue> getTopSetsByValue() { return topSetsByValue; }
    public void setTopSetsByValue(List<SetValue> topSetsByValue) { this.topSetsByValue = topSetsByValue; }
    public List<SetCompletion> getCompleteSets() { return completeSets; }
    public void setCompleteSets(List<SetCompletion> completeSets) { this.completeSets = completeSets; }
    public List<SetCompletion> getNearCompleteSets() { return nearCompleteSets; }
    public void setNearCompleteSets(List<SetCompletion> nearCompleteSets) { this.nearCompleteSets = nearCompleteSets; }
    public List<SetCompletion> getNearComplete80() { return nearComplete80; }
    public void setNearComplete80(List<SetCompletion> nearComplete80) { this.nearComplete80 = nearComplete80; }
    public List<SetCompletion> getNearComplete70() { return nearComplete70; }
    public void setNearComplete70(List<SetCompletion> nearComplete70) { this.nearComplete70 = nearComplete70; }
    public List<SetCompletion> getNearComplete65() { return nearComplete65; }
    public void setNearComplete65(List<SetCompletion> nearComplete65) { this.nearComplete65 = nearComplete65; }
    public double getDailyChange() { return dailyChange; }
    public void setDailyChange(double dailyChange) { this.dailyChange = dailyChange; }
    public List<CardPriceChange> getTopWinners() { return topWinners; }
    public void setTopWinners(List<CardPriceChange> topWinners) { this.topWinners = topWinners; }
    public List<CardPriceChange> getTopLosers() { return topLosers; }
    public void setTopLosers(List<CardPriceChange> topLosers) { this.topLosers = topLosers; }
}