package com.rabbitmq.concourse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class GithubReleaseDeleteResource {

  static final Gson GSON =
      new GsonBuilder()
          .registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeDeserializer())
          .create();

  public static void main(String[] args) {
    Scanner scanner = new Scanner(System.in);
    StringBuilder builder = new StringBuilder();
    while (scanner.hasNextLine()) {
      String line = scanner.nextLine();
      builder.append(line);
    }

    String command = args[0];
    if ("in".equals(command)) {
      log("Getting special version <DELETED> is a no-op; returning it as is");
      out(JSON_DELETED_VERSION);
    } else if ("out".equals(command)) {
      Input input = GSON.fromJson(builder.toString(), Input.class);
      ReleaseAccess access = new GitubRestApiReleaseAccess(input);

      List<Release> releases = access.list();
      List<Release> filteredReleases = filterByTag(releases, input.params().tagFilter());
      if (!filteredReleases.isEmpty()) {
        Collections.sort(filteredReleases, Comparator.comparing(Release::publication));
      }
      List<Release> toDeleteReleases = filterForDeletion(filteredReleases, input.params().keepLastN());

      logGreen("Tag filter: " + input.params().tagFilter());

      filteredReleases.forEach(r -> {
        if (toDeleteReleases.contains(r)) {
          logYellow("Removing release with tag " + r.tag());
          try {
            access.delete(r);
            access.deleteTag(r);
          } catch (Exception e) {
            logRed("Error while deleting release " + r + ": " + e.getMessage());
          }
        } else {
          log(" Keeping release with tag " + r.tag());
        }
      });

      out(JSON_DELETED_VERSION);
    } else {
      throw new IllegalArgumentException("command not supported: " + command);
    }
  }

  private static final String JSON_DELETED_VERSION = "{\n"
      + "  \"version\": { \"version\": \"<DELETED>\" },\n"
      + "  \"metadata\": [ ]\n"
      + "}";

  static void logGreen(String message) {
    log("\u001B[32m" + message + "\u001B[0m");
  }

  static void logYellow(String message) {
    log("\u001B[33m" + message + "\u001B[0m");
  }

  static void logRed(String message) {
    log("\u001B[31m" + message + "\u001B[0m");
  }


  static void log(String message) {
    System.err.println(message);
  }

  static void out(String message) {
    System.out.println(message);
  }

  static List<Release> filterByTag(List<Release> releases, String tagRegex) {
    Pattern pattern = Pattern.compile(tagRegex);
    return releases.stream()
        .filter(r -> pattern.matcher(r.tag()).matches())
        .collect(Collectors.toList());
  }

  static List<Release> filterForDeletion(List<Release> releases, int keepLastN) {
    if (releases.isEmpty()) {
      return Collections.emptyList();
    } else if (keepLastN <= 0) {
      // do not want to keep any, return all
      return releases;
    } else if (keepLastN >= releases.size()) {
      // we want to keep more than we have, so nothing to delete
      return Collections.emptyList();
    } else {
      releases = new ArrayList<>(releases);
      Collections.sort(releases, Comparator.comparing(Release::publication));
      return releases.subList(0, releases.size() - keepLastN);
    }
  }

  interface ReleaseAccess {

    List<Release> list();

    void delete(Release release);

    void deleteTag(Release release);
  }

  static class GitubRestApiReleaseAccess implements ReleaseAccess {

    private final HttpClient client =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(60)).build();

    private final Input input;

    GitubRestApiReleaseAccess(Input input) {
      this.input = input;
    }

    @Override
    public List<Release> list() {
      // TODO handle pagination (https://docs.github.com/en/rest/guides/traversing-with-pagination)
      // link header:
      // link: <https://api.github.com/repositories/343344332/releases?per_page=1&page=1>;
      // rel="prev", <https://api.github.com/repositories/343344332/releases?per_page=1&page=3>;
      // rel="next", <https://api.github.com/repositories/343344332/releases?per_page=1&page=4>;
      // rel="last", <https://api.github.com/repositories/343344332/releases?per_page=1&page=1>;
      // rel="first"
      HttpRequest request = requestBuilder("/releases").GET().build();
      try {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        // TODO check response code
        List<Release> releases =
            GSON.fromJson(
                response.body(), TypeToken.getParameterized(List.class, Release.class).getType());
        return releases;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void delete(Release release) {
      HttpRequest request = requestBuilder()
          .DELETE()
          .uri(URI.create(release.url())).build();
      try {
        HttpResponse<Void> response = client.send(request, BodyHandlers.discarding());
        // TODO check response code
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void deleteTag(Release release) {
      //https://api.github.com/repos/rabbitmq/rabbitmq-server-binaries-dev/git/refs/tags/v3.9.0-alpha-test.1
      HttpRequest request = requestBuilder("/git/refs/tags/" + release.tag()).DELETE().build();
      try {
        HttpResponse<Void> response = client.send(request, BodyHandlers.discarding());
        // TODO check response code
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }


    private Builder requestBuilder() {
      return auth(HttpRequest.newBuilder());
    }

    private Builder requestBuilder(String path) {
      return auth(HttpRequest.newBuilder()
          .uri(
              URI.create(
                  "https://api.github.com/repos/"
                      + input.source().owner()
                      + "/"
                      + input.source().repository()
                      + path)));

    }

    private Builder auth(Builder builder) {
      return builder
          .setHeader("Authorization", "token " + input.source().accessToken());
    }
  }

  static class ZonedDateTimeDeserializer implements JsonDeserializer<ZonedDateTime> {

    @Override
    public ZonedDateTime deserialize(
        JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException {
      return ZonedDateTime.parse(json.getAsString(), DateTimeFormatter.ISO_ZONED_DATE_TIME);
    }
  }

  static class Release {

    private long id;
    private String url;
    private ZonedDateTime published_at;
    private String tag_name;

    Release() {}

    Release(long id, String tag) {
      this.id = id;
      this.tag_name = tag;
    }

    Release(long id, ZonedDateTime published_at) {
      this.id = id;
      this.published_at = published_at;
    }

    long id() {
      return this.id;
    }

    String url() {
      return this.url;
    }

    String tag() {
      return this.tag_name;
    }

    ZonedDateTime publication() {
      return this.published_at;
    }

    @Override
    public String toString() {
      return "Release{"
          + "id="
          + id
          + ", url='"
          + url
          + '\''
          + ", published_at="
          + published_at
          + ", tag_name='"
          + tag_name
          + '\''
          + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Release release = (Release) o;
      return id == release.id;
    }

    @Override
    public int hashCode() {
      return Objects.hash(id);
    }
  }

  static class Input {

    private Params params;
    private Source source;

    Params params() {
      return params;
    }

    Source source() {
      return source;
    }

    @Override
    public String toString() {
      return "Input{" + "params=" + params + ", source=" + source + '}';
    }
  }

  static class Params {

    private String tag_filter;
    private int keep_last_n;

    String tagFilter() {
      return tag_filter;
    }

    int keepLastN() {
      return keep_last_n;
    }

    @Override
    public String toString() {
      return "Params{" + "tag_filter='" + tag_filter + '\'' + ", keep_last_n=" + keep_last_n + '}';
    }
  }

  static class Source {

    private String owner;
    private String repository;
    private String access_token;

    String owner() {
      return owner;
    }

    String repository() {
      return repository;
    }

    String accessToken() {
      return access_token;
    }

    @Override
    public String toString() {
      return "Source{"
          + "owner='"
          + owner
          + '\''
          + ", repository='"
          + repository
          + '\''
          + ", access_token='"
          + access_token
          + '\''
          + '}';
    }
  }
}
