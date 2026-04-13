package com.mtg.collection.controller;

import com.mtg.collection.dto.UserStatistics;
import com.mtg.collection.service.StatisticsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@Controller
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/statistics")
    public String statisticsPage(Model model, @RequestParam(required = false) String user) {
        if (user != null && !user.isEmpty()) {
            UserStatistics stats = statisticsService.getStatisticsForUser(user);
            model.addAttribute("userStatistics", stats);
            model.addAttribute("selectedUser", user);
        } else {
            Map<String, UserStatistics> allStats = statisticsService.getStatisticsForAllUsers();
            model.addAttribute("allStatistics", allStats);
        }
        
        return "statistics";
    }
}