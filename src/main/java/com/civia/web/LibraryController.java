package com.civia.web;

import com.civia.ai.CiviaAi;
import com.civia.ai.assistant.ArchitectAssistant;
import com.civia.ai.library.KnowledgeBase;
import com.civia.ai.library.SearchIndex;
import com.civia.domain.LibraryArticle;
import com.civia.domain.LibraryFavorite;
import com.civia.domain.LibrarySearchLog;
import com.civia.domain.User;
import com.civia.repo.LibraryArticleRepository;
import com.civia.repo.LibraryFavoriteRepository;
import com.civia.repo.LibrarySearchLogRepository;
import com.civia.service.ProjectAccess;
import com.civia.web.Dto.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Biblioteca de arquitectura: categorías, artículos, glosario, búsqueda y preguntas directas. */
@RestController
@RequestMapping("/api/v1/library")
@Tag(name = "Biblioteca")
public class LibraryController {

    private final CiviaAi ai;
    private final ProjectAccess access;
    private final LibraryArticleRepository custom;
    private final LibraryFavoriteRepository favorites;
    private final LibrarySearchLogRepository searches;
    private volatile Set<String> customSlugs = Set.of();

    public LibraryController(CiviaAi ai, ProjectAccess access, LibraryArticleRepository custom,
                             LibraryFavoriteRepository favorites, LibrarySearchLogRepository searches) {
        this.ai = ai;
        this.access = access;
        this.custom = custom;
        this.favorites = favorites;
        this.searches = searches;
    }

    /** Suma a la búsqueda los artículos creados por los usuarios. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void loadCustomArticles() {
        List<LibraryArticle> all = custom.findAll();
        customSlugs = new HashSet<>(all.stream().map(LibraryArticle::getSlug).toList());
        // Siempre se reconstruye: así un artículo borrado también sale de la búsqueda.
        ai.extendLibrary(all.stream().map(LibraryController::toKb).toList());
    }

    private static KnowledgeBase.Article toKb(LibraryArticle a) {
        List<String> tags = a.getTagsCsv() == null || a.getTagsCsv().isBlank() ? List.of()
                : Arrays.stream(a.getTagsCsv().split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
        return new KnowledgeBase.Article(a.getSlug(), a.getCategory(), a.getTitle(), a.getSummary() == null ? "" : a.getSummary(), tags, a.getBody());
    }

    private ArticleSummary summary(KnowledgeBase.Article a) {
        return new ArticleSummary(a.slug(), a.category(), a.title(), a.summary(), a.tags(), customSlugs.contains(a.slug()));
    }

    /** Artículo destacado del día: cambia cada día y es el mismo para todos. */
    @GetMapping("/featured")
    public ArticleSummary featured() {
        List<KnowledgeBase.Article> all = ai.library().articles().stream()
                .sorted(java.util.Comparator.comparing(KnowledgeBase.Article::slug)).toList();
        if (all.isEmpty()) throw ProjectAccess.notFound("Artículo");
        int index = (int) Math.floorMod(java.time.LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay() * 7919L, all.size());
        return summary(all.get(index));
    }

    /** Tamaño de la biblioteca. */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        KnowledgeBase kb = ai.library();
        return Map.of("categories", kb.categories().size(), "articles", kb.articles().size(),
                "terms", kb.terms().size(), "custom", customSlugs.size());
    }

    @GetMapping("/categories")
    public List<CategoryDto> categories() {
        KnowledgeBase kb = ai.library();
        return kb.categories().stream()
                .map(c -> new CategoryDto(c.id(), c.name(), c.icon(), c.description(), kb.articlesIn(c.id()).size())).toList();
    }

    @GetMapping("/articles")
    public List<ArticleSummary> articles(@RequestParam(required = false) String category) {
        KnowledgeBase kb = ai.library();
        return (category == null ? kb.articles() : kb.articlesIn(category)).stream().map(this::summary).toList();
    }

    @GetMapping("/articles/{slug}")
    @Transactional(readOnly = true)
    public ArticleDto article(@PathVariable String slug) {
        KnowledgeBase kb = ai.library();
        KnowledgeBase.Article a = kb.article(slug).orElseThrow(() -> ProjectAccess.notFound("Artículo"));
        boolean fav = favorites.findByUserIdAndSlug(access.me().getId(), slug).isPresent();
        List<ArticleSummary> related = kb.searchArticles(a.title() + " " + String.join(" ", a.tags()), 6).stream()
                .filter(r -> !r.slug().equals(slug)).limit(4).map(this::summary).toList();
        return new ArticleDto(a.slug(), a.category(), a.title(), a.summary(), a.tags(), a.body(), customSlugs.contains(slug), fav, related);
    }

    @PostMapping("/articles")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ArticleSummary create(@RequestBody ArticleRequest req) {
        if (req.title() == null || req.title().isBlank() || req.body() == null || req.body().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El artículo necesita título y contenido.");
        }
        KnowledgeBase kb = ai.library();
        String category = kb.categories().stream().anyMatch(c -> c.id().equals(req.category())) ? req.category() : "diseno";
        String base = SearchIndex.fold(req.title()).replaceAll("[^a-z0-9ñ]+", "-").replaceAll("(^-|-$)", "");
        if (base.isEmpty()) base = "articulo";
        String slug = base;
        int n = 2;
        while (kb.article(slug).isPresent() || custom.existsBySlug(slug)) slug = base + "-" + n++;
        User me = access.me();
        LibraryArticle a = new LibraryArticle();
        a.setSlug(slug);
        a.setCategory(category);
        a.setTitle(req.title().strip());
        a.setSummary(req.summary());
        a.setBody(req.body());
        a.setTagsCsv(req.tags() == null ? "" : String.join(",", req.tags()));
        a.setAuthor(me);
        a.setOrganization(me.getOrganization());
        custom.save(a);
        loadCustomArticles();
        return summary(toKb(a));
    }

    @GetMapping("/search")
    @Transactional
    public Map<String, Object> search(@RequestParam String q) {
        KnowledgeBase kb = ai.library();
        List<ArticleSummary> articles = kb.searchArticles(q, 12).stream().map(this::summary).toList();
        String folded = SearchIndex.fold(q);
        List<TermDto> terms = kb.search(q, 20).stream().filter(h -> "term".equals(h.chunk().kind())).limit(6)
                .map(h -> kb.terms().stream().filter(t -> t.term().equals(h.chunk().title())).findFirst().orElse(null))
                .filter(t -> t != null).map(t -> new TermDto(t.term(), t.category(), t.definition())).toList();
        if (terms.isEmpty()) {
            terms = kb.terms().stream().filter(t -> SearchIndex.fold(t.term()).contains(folded)).limit(6)
                    .map(t -> new TermDto(t.term(), t.category(), t.definition())).toList();
        }
        LibrarySearchLog log = new LibrarySearchLog();
        log.setUser(access.me());
        log.setQuery(q);
        log.setResultCount(articles.size() + terms.size());
        searches.save(log);
        return Map.of("articles", articles, "terms", terms);
    }

    /** Últimas búsquedas del usuario (sin repetir), para retomarlas. Nunca se muestran las de otras personas. */
    @GetMapping("/recent-searches")
    @Transactional(readOnly = true)
    public List<String> recentSearches() {
        return searches.findTop40ByUserIdOrderByCreatedAtDesc(access.me().getId()).stream()
                .map(l -> l.getQuery() == null ? "" : l.getQuery().strip())
                .filter(q -> !q.isEmpty())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new))
                .stream().limit(8).toList();
    }

    @GetMapping("/terms")
    public List<TermDto> terms(@RequestParam(required = false) String q, @RequestParam(required = false) String category) {
        String folded = q == null ? "" : SearchIndex.fold(q);
        return ai.library().terms().stream()
                .filter(t -> category == null || category.isBlank() || category.equals(t.category()))
                .filter(t -> folded.isEmpty() || SearchIndex.fold(t.term() + " " + t.definition()).contains(folded))
                .map(t -> new TermDto(t.term(), t.category(), t.definition())).toList();
    }

    /** Una sola pregunta → respuesta compuesta con citas a la biblioteca. */
    @PostMapping("/ask")
    public ArchitectAssistant.Answer ask(@RequestBody AskRequest req) {
        access.me();
        return ai.assistant().ask(req.question(), List.of(), null, List.of());
    }

    @GetMapping("/favorites")
    @Transactional(readOnly = true)
    public List<ArticleSummary> favorites() {
        KnowledgeBase kb = ai.library();
        return favorites.findByUserIdOrderByCreatedAtDesc(access.me().getId()).stream()
                .map(f -> kb.article(f.getSlug()).orElse(null)).filter(a -> a != null).map(this::summary).toList();
    }

    @PutMapping("/favorites/{slug}")
    @Transactional
    public Map<String, Object> addFavorite(@PathVariable String slug) {
        ai.library().article(slug).orElseThrow(() -> ProjectAccess.notFound("Artículo"));
        User me = access.me();
        if (favorites.findByUserIdAndSlug(me.getId(), slug).isEmpty()) {
            LibraryFavorite f = new LibraryFavorite();
            f.setUser(me);
            f.setSlug(slug);
            favorites.save(f);
        }
        return Map.of("favorite", true);
    }

    @DeleteMapping("/favorites/{slug}")
    @Transactional
    public Map<String, Object> removeFavorite(@PathVariable String slug) {
        favorites.findByUserIdAndSlug(access.me().getId(), slug).ifPresent(favorites::delete);
        return Map.of("favorite", false);
    }
}
