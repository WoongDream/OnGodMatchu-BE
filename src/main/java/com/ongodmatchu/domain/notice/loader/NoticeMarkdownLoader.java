package com.ongodmatchu.domain.notice.loader;

import com.ongodmatchu.domain.notice.document.NoticeDocument;
import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

@Component
public class NoticeMarkdownLoader {

  private static final String ANNOUNCEMENTS_PATTERN = "classpath:notices/announcements/*.md";
  private static final String MD_SUFFIX = ".md";

  private static final Pattern FRONTMATTER =
      Pattern.compile("\\A---\\s*\\R([\\s\\S]*?)\\R---\\s*\\R?([\\s\\S]*)\\z");
  private static final Pattern KEY_VALUE =
      Pattern.compile("^([A-Za-z][A-Za-z0-9_-]*)\\s*:\\s*(.*)$");

  private List<NoticeDocument> announcements = List.of();
  private Map<String, NoticeDocument> announcementsBySlug = Map.of();

  @PostConstruct
  void load() throws IOException {
    ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    Resource[] resources = resolver.getResources(ANNOUNCEMENTS_PATTERN);

    List<NoticeDocument> docs = new ArrayList<>(resources.length);
    for (Resource resource : resources) {
      docs.add(parse(resource));
    }
    docs.sort(
        Comparator.comparing(NoticeDocument::publishedAt, Comparator.reverseOrder())
            .thenComparing(NoticeDocument::slug, Comparator.reverseOrder()));

    this.announcements = List.copyOf(docs);
    this.announcementsBySlug =
        docs.stream()
            .collect(Collectors.toUnmodifiableMap(NoticeDocument::slug, Function.identity()));
  }

  public List<NoticeDocument> findAllAnnouncements() {
    return announcements;
  }

  public Optional<NoticeDocument> findAnnouncementBySlug(String slug) {
    return Optional.ofNullable(announcementsBySlug.get(slug));
  }

  private NoticeDocument parse(Resource resource) throws IOException {
    String filename =
        Optional.ofNullable(resource.getFilename())
            .orElseThrow(() -> new IllegalStateException("Notice resource missing filename"));
    if (!filename.endsWith(MD_SUFFIX)) {
      throw new IllegalStateException("Notice resource is not a markdown file: " + filename);
    }
    String slug = filename.substring(0, filename.length() - MD_SUFFIX.length());

    String raw = readAll(resource);
    Matcher matcher = FRONTMATTER.matcher(raw);
    if (!matcher.matches()) {
      throw new IllegalStateException("Notice missing YAML frontmatter: " + filename);
    }

    Map<String, String> meta = parseFrontmatter(matcher.group(1));
    String body = matcher.group(2);

    String title = required(meta, "title", filename);
    LocalDate publishedAt;
    try {
      publishedAt = LocalDate.parse(required(meta, "publishedAt", filename));
    } catch (DateTimeParseException e) {
      throw new IllegalStateException(
          "Notice publishedAt must be ISO-8601 (yyyy-MM-dd): " + filename, e);
    }

    return new NoticeDocument(slug, title, body, publishedAt);
  }

  private static String readAll(Resource resource) throws IOException {
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
      return reader.lines().collect(Collectors.joining("\n"));
    }
  }

  private static Map<String, String> parseFrontmatter(String yamlText) {
    Map<String, String> meta = new java.util.LinkedHashMap<>();
    for (String line : yamlText.split("\\R", -1)) {
      if (line.isBlank() || line.trim().startsWith("#")) {
        continue;
      }
      Matcher kv = KEY_VALUE.matcher(line);
      if (!kv.matches()) {
        throw new IllegalStateException("Invalid frontmatter line: " + line);
      }
      meta.put(kv.group(1), stripQuotes(kv.group(2).trim()));
    }
    return meta;
  }

  private static String stripQuotes(String value) {
    if (value.length() >= 2) {
      char first = value.charAt(0);
      char last = value.charAt(value.length() - 1);
      if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
        return value.substring(1, value.length() - 1);
      }
    }
    return value;
  }

  private static String required(Map<String, String> meta, String key, String filename) {
    String value = meta.get(key);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(
          "Notice frontmatter missing required key '" + key + "': " + filename);
    }
    return value;
  }
}
