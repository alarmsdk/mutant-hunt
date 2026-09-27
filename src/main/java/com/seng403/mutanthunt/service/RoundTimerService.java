package com.seng403.mutanthunt.service;

import com.seng403.mutanthunt.entity.Round;
import com.seng403.mutanthunt.entity.RoundStatus;
import com.seng403.mutanthunt.repository.RoundRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Closes OPEN rounds whose time limit has run out, so the board countdown and the
 * server agree. Submissions are additionally rejected after the deadline in
 * {@link GameService#submitTest}.
 */
@Service
public class RoundTimerService {

    private static final Logger log = LoggerFactory.getLogger(RoundTimerService.class);

    private final RoundRepository roundRepo;
    private final AdminService adminService;

    public RoundTimerService(RoundRepository roundRepo, AdminService adminService) {
        this.roundRepo = roundRepo;
        this.adminService = adminService;
    }

    @Scheduled(fixedDelay = 1000)
    public void closeExpiredRounds() {
        Instant cutoff = Instant.now().minus(GameService.DEADLINE_GRACE);
        for (Round r : roundRepo.findByStatus(RoundStatus.OPEN)) {
            Instant deadline = r.getDeadline();
            if (deadline != null && deadline.isBefore(cutoff)) {
                try {
                    adminService.closeRound(r.getId());
                    log.info("Round {} ('{}') closed automatically: time limit reached", r.getId(), r.getName());
                } catch (IllegalStateException e) {
                    // closed manually in the meantime
                }
            }
        }
    }
}
