package com.hftm.gameshelf.web;

import com.hftm.gameshelf.dto.GenreRequest;
import com.hftm.gameshelf.dto.GenreResponse;
import com.hftm.gameshelf.service.GenreService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/genres")
public class GenreController {

    private final GenreService genreService;

    public GenreController(GenreService genreService) {
        this.genreService = genreService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GenreResponse erstellen(@Valid @RequestBody GenreRequest request) {
        return genreService.erstellen(request);
    }

    @GetMapping
    public List<GenreResponse> alle() {
        return genreService.alle();
    }
}
