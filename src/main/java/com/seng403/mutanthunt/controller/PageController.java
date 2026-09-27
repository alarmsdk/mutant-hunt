package com.seng403.mutanthunt.controller;

import com.seng403.mutanthunt.entity.AnonymityMode;
import com.seng403.mutanthunt.entity.GameSession;
import com.seng403.mutanthunt.i18n.UiMessages;
import com.seng403.mutanthunt.service.GameService;
import com.seng403.mutanthunt.service.QrCodeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Controller
public class PageController {

    private final GameService gameService;
    private final QrCodeService qrCodeService;
    private final UiMessages uiMessages;

    public PageController(GameService gameService, QrCodeService qrCodeService, UiMessages uiMessages) {
        this.gameService = gameService;
        this.qrCodeService = qrCodeService;
        this.uiMessages = uiMessages;
    }

    /** All UI texts, inlined into every page as {@code window.MSG} for the JS code. */
    @ModelAttribute("messages")
    public Map<String, String> messages(Locale locale) {
        return uiMessages.all(locale);
    }

    @GetMapping("/")
    public String studentPage(Model model) {
        Optional<GameSession> session = gameService.getActiveSession();
        model.addAttribute("hasSession", session.isPresent());
        model.addAttribute("nicknameMode",
            session.map(s -> s.getAnonymityMode() == AnonymityMode.NICKNAME).orElse(false));
        model.addAttribute("studentIdLength", session.map(GameSession::getStudentIdLength).orElse(0));
        return "student";
    }

    @GetMapping("/board")
    public String boardPage(Model model) {
        model.addAttribute("joinUrl", qrCodeService.getJoinUrl());
        return "board";
    }

    @GetMapping("/admin/login")
    public String adminLoginPage() {
        return "admin-login";
    }

    @GetMapping("/admin")
    public String adminPage(Model model) {
        model.addAttribute("joinUrl", qrCodeService.getJoinUrl());
        return "admin";
    }
}
