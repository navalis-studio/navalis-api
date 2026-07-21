package io.navalis.api.interfaces.rest;

import io.navalis.api.infrastructure.persistence.entity.UserEntity;
import io.navalis.api.infrastructure.persistence.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/players")
public class RankingController {

    private final UserRepository userRepository;

    public RankingController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/ranking")
    public ResponseEntity<List<Map<String, Object>>> getRanking() {
        List<UserEntity> topPlayers = userRepository.findAll(
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "wins"))
        ).getContent();

        List<Map<String, Object>> ranking = topPlayers.stream()
                .filter(u -> u.getWins() > 0 || u.getLosses() > 0)
                .map(u -> Map.<String, Object>of(
                        "username", u.getUsername(),
                        "wins", u.getWins(),
                        "losses", u.getLosses()
                ))
                .toList();

        return ResponseEntity.ok(ranking);
    }
}
