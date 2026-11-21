package com.javmotion.api.visualizer;

import com.javmotion.api.visualizer.ConceptVisualizer.ConceptResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Concept visualizers (Phase 2). Pure computation on size-capped input — synchronous, no auth,
 * no user code executed. Response: {@code { key, title, code, trace }}.
 */
@RestController
@RequestMapping("/api/v1/visualizer")
public class VisualizerController {

    private final StringReverseVisualizer stringReverse;
    private final ArrayConceptVisualizer array;
    private final LoopsVisualizer loops;

    public VisualizerController(
            StringReverseVisualizer stringReverse, ArrayConceptVisualizer array, LoopsVisualizer loops) {
        this.stringReverse = stringReverse;
        this.array = array;
        this.loops = loops;
    }

    @PostMapping("/string-reverse")
    public ConceptResult stringReverse(@Valid @RequestBody StringReverseVisualizer.Request request) {
        return stringReverse.generate(request);
    }

    @PostMapping("/array")
    public ConceptResult array(@Valid @RequestBody ArrayConceptVisualizer.Request request) {
        return array.generate(request);
    }

    @PostMapping("/loops")
    public ConceptResult loops(@Valid @RequestBody LoopsVisualizer.Request request) {
        return loops.generate(request);
    }
}
