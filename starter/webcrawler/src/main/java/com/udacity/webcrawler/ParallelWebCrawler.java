package com.udacity.webcrawler;

import com.udacity.webcrawler.json.CrawlResult;
import com.udacity.webcrawler.parser.PageParser;
import com.udacity.webcrawler.parser.PageParserFactory;

import javax.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A concrete implementation of {@link WebCrawler} that runs multiple threads on a
 * {@link ForkJoinPool} to fetch and process multiple web pages in parallel.
 */
final class ParallelWebCrawler implements WebCrawler {

    private final Clock clock;
    private final Duration timeout;
    private final int popularWordCount;
    private final int maxDepth;
    private final ForkJoinPool pool;
    private final PageParserFactory parserFactory;
    private final List<Pattern> ignoredUrls;

    @Inject
    ParallelWebCrawler(
            Clock clock,
            PageParserFactory parserFactory,
            @Timeout Duration timeout,
            @PopularWordCount int popularWordCount,
            @MaxDepth int maxDepth,
            @IgnoredUrls List<Pattern> ignoredUrls,
            @TargetParallelism int threadCount) {
        this.clock = Objects.requireNonNull(clock);
        this.parserFactory = Objects.requireNonNull(parserFactory);
        this.timeout = Objects.requireNonNull(timeout);
        this.popularWordCount = popularWordCount;
        this.maxDepth = maxDepth;
        this.ignoredUrls = ignoredUrls;
        this.pool = new ForkJoinPool(Math.min(threadCount, getMaxParallelism()));
    }

    @Override
    public CrawlResult crawl(List<String> startingUrls) {
//        if (startingUrls.isEmpty()) {
//            return new CrawlResult.Builder().build();
//        }

        Instant deadline = clock.instant().plus(timeout);
        ConcurrentMap<String, Integer> counts = new ConcurrentHashMap<>();
        ConcurrentSkipListSet<String> visitedUrls = new ConcurrentSkipListSet<>();
        for (String url : startingUrls) {
            pool.invoke(new InternalCrawler(url, maxDepth, deadline, visitedUrls, counts));
        }

        if (counts.isEmpty()) {
            return new CrawlResult.Builder()
                    .setWordCounts(counts)
                    .setUrlsVisited(visitedUrls.size())
                    .build();
        }

        return new CrawlResult.Builder()
                .setWordCounts(WordCounts.sort(counts, popularWordCount))
                .setUrlsVisited(visitedUrls.size())
                .build();
    }
    /**
     * Recursive crawl task that fetches and parses a URL and spawns subtasks for links.
     */
    public class InternalCrawler extends RecursiveTask<Boolean> {
        private String url;
        private int maxDepth;
        private Instant deadline;
        private ConcurrentSkipListSet<String> visitedUrls;
        private ConcurrentMap<String, Integer> counts;

        public InternalCrawler(String url,
                               int maxDepth,
                               Instant deadline,
                               ConcurrentSkipListSet<String> visitedUrls,
                               ConcurrentMap<String, Integer> counts) {
            this.url = url;
            this.maxDepth = maxDepth;
            this.deadline = deadline;
            this.visitedUrls = visitedUrls;
            this.counts = counts;
        }

        @Override
        protected Boolean compute() {
            if (maxDepth == 0 || clock.instant().isAfter(deadline)) {
                return false;
            }

            for (Pattern pattern : ignoredUrls) {
                if (pattern.matcher(url).matches()) {
                    return false;
                }
            }

            if (!visitedUrls.add(url)) {
                return false; // already visited
            }


            PageParser.Result result = parserFactory.get(url).parse();


            for (Map.Entry<String, Integer> e : result.getWordCounts().entrySet()) {
                counts.compute(e.getKey(), (k, v) -> (v == null) ? e.getValue() : e.getValue()+v);
            }


            List<InternalCrawler> subTasks = result.getLinks().stream()
                    .map(link -> new InternalCrawler(link, maxDepth - 1, deadline, visitedUrls, counts))
                    .collect(Collectors.toList());

            invokeAll(subTasks);
            return true;
        }
    }


    @Override
    public int getMaxParallelism() {
        return Runtime.getRuntime().availableProcessors();
    }

}


