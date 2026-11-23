package dev.normalbias.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static dev.normalbias.api.AuthPolicy.*;

/** Additive action discovery; the original catalogue fields and pagination remain intact. */
@RestControllerAdvice
class AuthRepresentations implements ResponseBodyAdvice<Object> {
    private final AuthSecurity security; private final ObjectMapper json;
    AuthRepresentations(AuthSecurity security, ObjectMapper json) { this.security = security; this.json = json; }
    @Override public boolean supports(MethodParameter type, Class<? extends HttpMessageConverter<?>> converter) { return true; }
    @Override public Object beforeBodyWrite(Object body, MethodParameter type, MediaType media, Class<? extends HttpMessageConverter<?>> converter,
                                          ServerHttpRequest request, ServerHttpResponse response) {
        if (security.demo()) return body;
        Identity identity = identity(SecurityContextHolder.getContext().getAuthentication());
        if (body instanceof Track track) return track(track, identity);
        if (body instanceof TrackPage page) {
            Map<String, Object> result = map(page); result.put("items", page.items().stream().map(t -> track(t, identity)).toList());
            result.put("_links", pages(request, page.offset(), page.limit(), page.total())); return result;
        }
        if (body instanceof LibraryController.Page page) {
            Map<String, Object> result = map(page);
            result.put("items", page.items().stream().map(group -> {
                var row = map(group); String artist = URLEncoder.encode(group.artist(), StandardCharsets.UTF_8);
                String href = request.getURI().getPath().endsWith("artists") ? "/api/library/albums?artist=" + artist
                        : "/api/tracks?artist=" + artist + "&album=" + URLEncoder.encode(group.album(), StandardCharsets.UTF_8);
                row.put("_links", Map.of("contents", link(href, "GET"), "artwork", link("/api/tracks/" + row.get("trackId") + "/artwork", "GET"))); return row;
            }).toList());
            result.put("_links", pages(request, page.offset(), page.limit(), page.total())); return result;
        }
        return body;
    }
    private Map<String, Object> track(Track track, Identity identity) {
        Map<String, Object> result = map(track); String path = "/api/tracks/" + track.id();
        var links = new LinkedHashMap<String, Link>();
        if (allows(identity, Action.LISTEN)) {
            links.put("self", link(path, "GET"));
            links.put("artwork", link(path + "/artwork", "GET")); links.put("playback-grant", link(path + "/playback-grants", "POST"));
        }
        if (allows(identity, Action.MANAGE)) { links.put("edit", link(path, "PATCH")); links.put("delete", link(path, "DELETE")); }
        result.put("_links", links); return result;
    }
    private Map<String, Link> pages(ServerHttpRequest request, int offset, int limit, long total) {
        var links = new LinkedHashMap<String, Link>();
        var builder = UriComponentsBuilder.fromPath(request.getURI().getRawPath()).query(request.getURI().getRawQuery());
        links.put("self", link(builder.build(true).toUriString(), "GET"));
        if ((long) offset + limit < total) links.put("next", link(builder.cloneBuilder().replaceQueryParam("offset", (long) offset + limit).build(true).toUriString(), "GET"));
        if (offset > 0) links.put("previous", link(builder.cloneBuilder().replaceQueryParam("offset", Math.max(0, offset - limit)).build(true).toUriString(), "GET"));
        return links;
    }
    private Map<String, Object> map(Object value) { return json.convertValue(value, new TypeReference<LinkedHashMap<String, Object>>() { }); }
}
