package com.votekante.config;

import com.votekante.entities.Election;
import com.votekante.repositories.ElectionRepository;
import com.votekante.services.ElectionService;
import com.votekante.services.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * First-boot bootstrap: creates the admin account (so there is always a way
 * in) and, when {@code app.seed-demo-data=true}, a demo election with parties
 * so the whole flow can be clicked through immediately.
 *
 * <p>Credentials come from configuration so they can be changed per
 * environment without recompiling.</p>
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserService userService;
    private final ElectionService electionService;
    private final ElectionRepository electionRepository;

    private final boolean seedDemoData;
    private final String adminUsername;
    private final String adminPassword;

    public DataSeeder(UserService userService,
                      ElectionService electionService,
                      ElectionRepository electionRepository,
                      @Value("${app.seed-demo-data:true}") boolean seedDemoData,
                      @Value("${app.admin.username:admin}") String adminUsername,
                      @Value("${app.admin.password:admin123}") String adminPassword) {
        this.userService = userService;
        this.electionService = electionService;
        this.electionRepository = electionRepository;
        this.seedDemoData = seedDemoData;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        // The configured admin is created if it does not exist yet.
        userService.ensureAdmin(adminUsername, adminPassword);
        log.info("Admin account ready: '{}'", adminUsername);

        if (seedDemoData && electionRepository.count() == 0) {
            log.info("Seeding demo election…");
            Election demo = electionService.createElection(
                    "Municipal Council 2026",
                    "Election for the town council. Demo data – one vote per account.");
            electionService.addParty(demo.getId(), "Green Future",
                    "Sustainability-first agenda for the city.");
            electionService.addParty(demo.getId(), "People's Alliance",
                    "Community services, housing and local jobs.");
            electionService.addParty(demo.getId(), "Civic Union",
                    "Fiscal responsibility and modernised infrastructure.");
            // Leave it closed; the admin starts it from the dashboard.
        }
    }
}
