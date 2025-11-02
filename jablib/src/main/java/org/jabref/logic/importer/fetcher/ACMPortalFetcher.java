package org.jabref.logic.importer.fetcher;

import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.jabref.logic.help.HelpFile;
import org.jabref.logic.importer.FetcherException;
import org.jabref.logic.importer.Parser;
import org.jabref.logic.importer.SearchBasedParserFetcher;
import org.jabref.logic.importer.fetcher.transformers.DefaultQueryTransformer;
import org.jabref.logic.importer.fileformat.ACMPortalParser;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.search.query.BaseQueryNode;

import org.apache.hc.core5.net.URIBuilder;

public class ACMPortalFetcher implements SearchBasedParserFetcher {

    public static final String FETCHER_NAME = "ACM Portal";
    private static final String SEARCH_URL = "https://dl.acm.org/action/doSearch";
    private final HttpClient httpClient;

    public ACMPortalFetcher() {
        // website dl.acm.org requires cookies
        CookieHandler.setDefault(new CookieManager());
        this.httpClient = HttpClient.newBuilder()
                                    .followRedirects(HttpClient.Redirect.NORMAL)
                                    .connectTimeout(Duration.ofSeconds(10))
                                    .build();
    }

    @Override
    public String getName() {
        return FETCHER_NAME;
    }

    @Override
    public Optional<HelpFile> getHelpPage() {
        return Optional.of(HelpFile.FETCHER_ACM);
    }

    private static String createQueryString(BaseQueryNode queryNode) {
        return new DefaultQueryTransformer().transformSearchQuery(queryNode).orElse("");
    }

    @Override
    public URL getURLForQuery(BaseQueryNode queryNode) throws URISyntaxException, MalformedURLException {
        URIBuilder uriBuilder = new URIBuilder(SEARCH_URL);
        uriBuilder.addParameter("AllField", createQueryString(queryNode));
        return uriBuilder.build().toURL();
    }

    @Override
    public Parser getParser() {
        return new ACMPortalParser();
    }

    @Override
    public List<BibEntry> performSearch(BaseQueryNode queryNode) throws FetcherException {
        try {
            URI searchUri = new URIBuilder(SEARCH_URL)
                    .addParameter("AllField", createQueryString(queryNode))
                    .build();

            HttpRequest request = HttpRequest.newBuilder(searchUri)
                                             .timeout(Duration.ofSeconds(15))
                                             .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                                             .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                                             .header("Accept-Language", "en-US,en;q=0.5")
                                             .GET()
                                             .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new FetcherException("ACM Portal returned status: " + response.statusCode());
            }
            return getParser().parseEntries(response.body());
        } catch (URISyntaxException | MalformedURLException e) {
            throw new FetcherException("Invalid ACM search URL", e);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FetcherException("Network error while connecting to ACM Portal", e);
        } catch (Exception e) {
            throw new FetcherException("Failed to parse ACM Portal response", e);
        }
    }
}
