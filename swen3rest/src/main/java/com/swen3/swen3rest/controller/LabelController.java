package com.swen3.swen3rest.controller;

import com.swen3.swen3rest.entity.Label;
import com.swen3.swen3rest.repository.LabelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/labels")
public class LabelController {

    private static final Logger log = LoggerFactory.getLogger(LabelController.class);

    private final LabelRepository labelRepository;

    public LabelController(LabelRepository labelRepository) {
        this.labelRepository = labelRepository;
    }

    @PostMapping
    public ResponseEntity<Label> create(@RequestParam String name) {
        if (name.isBlank()) {
            log.warn("Rejected label: name is blank");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name must not be blank");
        }
        if (labelRepository.findByName(name).isPresent()) {
            log.warn("Rejected label '{}': already exists", name);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "label already exists");
        }
        Label label = new Label();
        label.setName(name);
        Label saved = labelRepository.save(label);
        log.info("Created label {} '{}'", saved.getId(), saved.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping
    public List<Label> list() {
        log.debug("Listing all labels");
        return labelRepository.findAll();
    }
}
