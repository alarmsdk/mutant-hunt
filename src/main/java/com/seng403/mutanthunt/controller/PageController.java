package com.seng403.mutanthunt.controller;

import com.seng403.mutanthunt.entity.GameSession;
import com.seng403.mutanthunt.service.GameService;
import com.seng403.mutanthunt.service.QrCodeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Optional;

@Controller
public class PageController {

    private final GameService gameService;
    private final QrCodeService qrCodeService;

    public PageController(GameService gameService, QrCodeService qrCodeService) {
        this.gameService = gameService;
        this.qrCodeService = qrCodeService;
    }

    @GetMapping("/")
    public String studentPage(Model model) {
        Optional<GameSession> session = gameService.getActiveSession();
        model.addAttribute("hasSession", session.isPresent());
        model.addAttribute("nicknameMode", false);
        model.addAttribute("studentIdLength", 0);
        session.ifPresent(s -> {
            model.addAttribute("nicknameMode",
                s.getAnonymityMode() == com.seng403.mutanthunt.entity.AnonymityMode.NICKNAME);
            model.addAttribute("studentIdLength", s.getStudentIdLength());
        });
        return "student";
    }

    @GetMapping("/board")
    public String boardPage(Model model) {
        Optional<GameSession> session = gameService.getActiveSession();
        model.addAttribute("hasSession", session.isPresent());
        if (session.isPresent()) {
            model.addAttribute("joinUrl", qrCodeService.getJoinUrl());
        }
        return "board";
    }

    @GetMapping("/admin/login")
    public String adminLoginPage() {
        return "admin-login";
    }

    @GetMapping("/admin")
    public String adminPage() {
        return "admin";
    }
}
