package manifold.github.traffic;

import github.api.*;
import github.api.StarHistory.StarHistoryItem;
import manifold.ext.rt.api.Structural;
import manifold.ext.rt.api.auto;
import manifold.json.rt.api.Requester;
import manifold.rt.api.util.StreamUtil;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static manifold.github.traffic.AnsiColor.*;

/**
 * GitHub traffic CLI utility similar to the GitHub Traffic web page, but with additional features such as
 * added/removed stars between runs including identification of users who <i>unstarred</i> the repo, and more.
 */
@SuppressWarnings({"StringConcatenationInsideStringBufferAppend", "MalformedFormatString", "unchecked"})
public class Traffic {
    private static final String HEAVY_BLOCK = "▓";
    private static final String LIGHT_BLOCK = "░";

    private static final int MAX_BAR_LEN = 40;
    private static final int MAX_URL = 38;
    private static final int MAX_REFERRER_URL = 30;
    private static final int MAX_UNIQUE_URL_BAR = 5;
    private static final int MAX_COUNT_URL_BAR = 10;
    private static final String STAR_HISTORY_FILE = "star_history.txt";

    private final String _user;
    private final String _repo;
    private final String _token;
    private final int _days;
    private final StringBuilder _content;

    Traffic(Map<Arg, String> processedArgs) {
        _user = processedArgs.get(Arg.user);
        _repo = processedArgs.get(Arg.repo);
        _token = processedArgs.get(Arg.token);
        _days = Integer.parseInt(processedArgs.get(Arg.days));
        _content = new StringBuilder();
    }

    @SuppressWarnings("UnusedReturnValue")
    void report() throws IOException, InterruptedException {
        println();
        println(makeHeader());
        println();
        println(showStats());
        println();
        println("$_days-day summary$DKGREY (UTC time)$RESET");
        println();
        Tile root = new Tile(Tile.Layout.Column, Tile.Margin.Empty);
        Tile topCharts = new Tile(Tile.Layout.Row, Tile.Margin.Empty);
        topCharts.append(makePageViews(), new Tile.Margin(0, 0, 0, 4));
        topCharts.append(makeClones());
        root.append(topCharts);
        if (_days >= 14) { // bottom chart data applies to past 14 days
            Tile bottomCharts = new Tile(Tile.Layout.Row, new Tile.Margin(1, 0, 1, 0));
            bottomCharts.append(makePopularPaths(), new Tile.Margin(0, 0, 0, 4));
            bottomCharts.append(makePopularReferrers());
            root.append(bottomCharts);
        }
        String report = root.render();
        println(report);
        String diff = showStargazerDiff();
        println(diff);
    }

    private void println(String... data) {
        for (String s: data) {
            _content.append(s);
            System.out.print(s);
        }
        _content.append('\n');
        System.out.println();
    }

    private String makeHeader() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss"));
        return "Repo: $_user/$YELLOW$_repo  $DKGREY$timestamp$RESET";
    }

    private String showStats() throws IOException {
        auto history = readStarHistory();

        Repo repoData = getOne(Repo.request("https://api.github.com/repos/$_user/$_repo"));
        int stars = repoData.getStargazers_count();

        OpenPrCount openPrCount = getOne(OpenPrCount.request("https://api.github.com/search/issues?q=repo:$_user/$_repo%20is:pr%20is:open&per_page=1"));
        int openPrcCount = openPrCount.getTotal_count();

        updateStarHistory(history.starHistory, stars);

        StringBuilder sb = new StringBuilder();
        sb.append("$YELLOW$stars$RESET");
        if (history.prevStars >= 0) {
            int diff = stars - history.prevStars;
            if (diff != 0) {
                String sign = diff > 0 ? "$GREEN+" : "$RED";
                sb.append("$DKGREY($sign$diff${DKGREY})");
            }
            sb.append("$DKGREY stars");
        }
        String separator = " $DKGREY|$RESET ";
        sb.append(separator).append(count(repoData.getSubscribers_count(), "watcher"))
                .append(separator).append(count(repoData.getForks_count(), "fork"))
                .append(separator).append(count(openPrcCount, "pull request"))
                .append(separator).append(count(repoData.getOpen_issues() - openPrcCount, "issue"));
        return sb.toString();
    }

    private auto readStarHistory() throws IOException {
        int prevStars = -1;
        //noinspection UnusedAssignment
        StarHistory starHistory = null;
        File starHistoryFile = new File(getAppDirectory(), STAR_HISTORY_FILE);
        if (starHistoryFile.isFile()) {
            try (FileReader reader = new FileReader(starHistoryFile)) {
                starHistory = StarHistory.load().fromJsonReader(reader);
                if (!starHistory.isEmpty()) {
                    //noinspection UnusedAssignment
                    prevStars = starHistory.last().getCount();
                }
            }
        }
        return prevStars, starHistory;
    }

    private void updateStarHistory(StarHistory starHistory, Integer stars) throws IOException {
        LocalDateTime todayWithTime = todayWithTime();
        if (starHistory == null || starHistory.isEmpty()) {
            starHistory = StarHistory.create();
            starHistory.add(StarHistoryItem.create(todayWithTime, stars));
        } else {
            StarHistoryItem last = starHistory.last();
            if (last.getTimestamp().toLocalDate().isEqual(todayWithTime.toLocalDate())) {
                // update today's count (maintain one count per day)
                last.setTimestamp(todayWithTime);
                last.setCount(stars);
            } else if (last.getTimestamp().isAfter(todayWithTime)) {
                throw new IllegalStateException("Current timestamp predates last recorded star count timestamp: " +
                        "today: '$todayWithTime'' last: '${last.getTimestamp()}'");
            } else {
                starHistory = StarHistory.create();
                starHistory.add(StarHistoryItem.create(todayWithTime, stars));
            }
        }
        try (Writer writer = new FileWriter(new File(getAppDirectory(), STAR_HISTORY_FILE))) {
            starHistory.write().toJson(writer);
        }
    }

    private String makePageViews() {
        PageViews pageViews = getOne(PageViews.request("https://api.github.com/repos/$_user/$_repo/traffic/views"));
        return makeCountsChart("Views", pageViews.getViews(), this::viewsPerUser);
    }

    private String makeClones() {
        RepoClones repoClones = getOne(RepoClones.request("https://api.github.com/repos/$_user/$_repo/traffic/clones"));
        return makeCountsChart("Clones", repoClones.getClones(), (u, t) -> "");
    }

    private String makePopularPaths() {
        PopularPaths pp = getOne(PopularPaths.request("https://api.github.com/repos/$_user/$_repo/traffic/popular/paths"));
        return makePathsChart("Top views", MAX_URL, pp, item -> removeRepoPath(item.getPath()));
    }

    private String makePopularReferrers() {
        PopularReferrers pr = getOne(PopularReferrers.request("https://api.github.com/repos/$_user/$_repo/traffic/popular/referrers"));
        return makePathsChart("Referring sites", MAX_REFERRER_URL, pr, item -> item.getReferrer());
    }

    private String makeCountsChart(String title, List<? extends CountedItem> items, BiFunction<Integer, Integer, String> ratio) {
        auto totals = calcTotals(items);
        int digits = String.valueOf(totals.totalUniques).length();
        int width = digits + 1;
        double factor = (double) MAX_BAR_LEN / totals.maxCount;
        StringBuilder sb = new StringBuilder();
        sb.append("$title$DKGREY unique & total$RESET").append('\n');
        sb.append(makeCountsChart(items, factor, width, ratio));
        if (_days > 1) {
            sb.append(makeTotalsBar(totals.totalUniques, totals.totalCount, factor, width, ratio)).append('\n');
        }
        return sb.toString();
    }

    private <P> String makePathsChart(String title, int maxUrl, List<P> items, Function<P, String> urlProcessor) {
        int maxUniques = 0;
        int maxCount = 0;
        //noinspection unchecked
        List<CountedItem> countedItems = (List<CountedItem>)items;
        for (CountedItem item : countedItems) {
            maxUniques = Math.max(maxUniques, item.getUniques());
            maxCount = Math.max(maxCount, item.getCount());
        }
        StringBuilder sb = new StringBuilder();
        sb.append("$title\n");
        for (CountedItem item : countedItems) {
            //noinspection unchecked
            String url = urlProcessor.apply((P)item);
            makePathUrlBar(sb, url, GREY, maxUniques, maxCount, maxUrl, item.getUniques(), item.getCount());
        }
        return sb.toString();
    }

    private void makePathUrlBar(StringBuilder sb, String url, @SuppressWarnings("SameParameterValue") String color,
                                int maxUniques, int maxCount, int maxUrl, int uniques, int count) {
        url = clipUrl(url, maxUrl);
        int uniquesWidth = String.valueOf(maxUniques).length();
        double factorUniques = (double) MAX_UNIQUE_URL_BAR / maxUniques;
        double factorCount = (double) MAX_COUNT_URL_BAR / maxCount;

        int uniquesBarWidth = (int) (Math.ceil(factorUniques * uniques));
        sb.append(String.format("%${uniquesWidth}d", uniques))
                .append(color + HEAVY_BLOCK.repeat(uniquesBarWidth)).append(LIGHT_BLOCK.repeat(MAX_UNIQUE_URL_BAR + 1 - uniquesBarWidth))
                .append(url).append(LIGHT_BLOCK.repeat(maxUrl - url.length() + (int) (Math.ceil(factorCount * count))) + RESET)
                .append(count).append('\n');
    }

    private String makeTotalsBar(int totalUniques, int totalCount, double factor, int width, BiFunction<Integer, Integer, String> ratio) {
        StringBuilder sb = new StringBuilder();
        sb.append("${DKGREY}Total:   $RESET");
        sb.append(String.format("%${width}d", totalUniques));
        if (totalCount == 0) {
            return sb.toString();
        }
        int uniquesWidth = (int) Math.ceil((double) totalUniques * factor / _days);
        int totalCountWidth = (int) Math.ceil((double) totalCount * factor / _days) - uniquesWidth;
        //noinspection StringConcatenationInsideStringBufferAppend
        sb.append(PURPLE + HEAVY_BLOCK.repeat(uniquesWidth) + LIGHT_BLOCK.repeat(totalCountWidth) + RESET + totalCount +
                  " " + DKGREY + ratio.apply(totalUniques, totalCount) + RESET );
        return sb.toString();
    }

    private String makeCountsChart(List<? extends CountedItem> cloneItems, double factor, int width, BiFunction<Integer, Integer, String> showRatio) {
        StringBuilder clonesChart = new StringBuilder();
        DateTimeFormatter dayMonthFormat = DateTimeFormatter.ofPattern("dd MMM");
        DateTimeFormatter dayOfWeekFormat = DateTimeFormatter.ofPattern("EEEEE");
        int size = cloneItems.size();
        LocalDate now = today();
        LocalDate csrDate = now.minusDays(_days - 1);
        List<String> bars = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            CountedItem item = cloneItems.get(i);
            if (size - i > _days) {
                if (item.getTimestamp().toLocalDate().isEqual(csrDate)) {
                    csrDate = csrDate.plusDays(1);
                }
                continue;
            }
            LocalDate timestamp = item.getTimestamp().toLocalDate();
            if (timestamp.isBefore(csrDate)) {
                continue;
            }
            int diff = (int) ChronoUnit.DAYS.between(csrDate, timestamp);
            while (diff > 0) {
                bars.add(DKGREY + '(' + dayMonthFormat.format(csrDate) + ')' + dayOfWeekFormat.format(csrDate) +
                        String.format("%${width}d", 0) + "$RESET\n");
                csrDate = csrDate.plusDays(1);
                diff--;
            }
            csrDate = timestamp.plusDays(1);
            String color = timestamp.isEqual(now) ? GREEN : BLUE;
            int uniquesWidth = (int) Math.ceil((double) item.getUniques() * factor);
            int countWidth = (int) Math.ceil((double) item.getCount() * factor) - uniquesWidth;
            bars.add(DKGREY + '(' + dayMonthFormat.format(timestamp) + ')' + dayOfWeekFormat.format(timestamp) + RESET +
                    String.format("%${width}d", item.getUniques()) +
                    color + HEAVY_BLOCK.repeat(uniquesWidth) + LIGHT_BLOCK.repeat(countWidth) + RESET +
                    item.getCount() + " " + DKGREY + showRatio.apply(item.getUniques(), item.getCount()) + RESET + "\n");
        }
        while (csrDate.isBefore(now) || csrDate.isEqual(now)) {
            bars.add(DKGREY + '(' + dayMonthFormat.format(csrDate) + ')' + dayOfWeekFormat.format(csrDate) +
                    String.format("%${width}d", 0) + "$RESET\n");
            csrDate = csrDate.plusDays(1);
        }
        bars.reversed().forEach(clonesChart::append);
        return clonesChart.toString();
    }

    private String viewsPerUser(int uniques, int total) {
        DecimalFormat value = new DecimalFormat("##.#");
        return value.format((double) total / uniques);
    }

    private auto calcTotals(List<?> clones) {
        int totalUniques = 0;
        int totalCount = 0;
        int maxCount = 0;
        int size = clones.size();
        LocalDate csrDate = today().minusDays(_days - 1);
        for (int i = 0; i < size; i++) {
            if (size - i > _days) {
                continue;
            }
            CountedItem item = (CountedItem) clones.get(i);
            LocalDate timestamp = item.getTimestamp().toLocalDate();
            if (timestamp.isBefore(csrDate)) {
                continue;
            }
            totalUniques += item.getUniques();
            totalCount += item.getCount();
            maxCount = Math.max(maxCount, item.getCount());
        }
        return totalUniques, totalCount, maxCount;
    }

    String showStargazerDiff() throws IOException, InterruptedException {
        LinkedHashSet<String> nowGazers = fetchStargazers();
        File stargazersFile = new File(getAppDirectory(), "stargazers.txt");
        String result = "";
        if (stargazersFile.isFile()) {
            List<String> lost = new ArrayList<>();
            List<String> gained = new ArrayList<>();
            String prev;
            try (FileReader readerPrev = new FileReader(stargazersFile)) {
                prev = StreamUtil.getContent(readerPrev);
            }
            Files.copy(stargazersFile.toPath(),
                    Paths.get(getAppDirectory().getAbsolutePath(), "stargazers_prior.txt"), REPLACE_EXISTING);
            StringTokenizer tokPrev = new StringTokenizer(prev, "\n");
            Set<String> prevGazers = new HashSet<>();
            int prevPos = 0;
            while (tokPrev.hasMoreTokens()) {
                prevPos++;
                String gazer = tokPrev.nextToken();
                prevGazers.add(gazer);
                if (!nowGazers.contains(gazer)) {
                    lost.add("#$prevPos $gazer");
                }
            }
            for (String gazer : nowGazers) {
                if (!prevGazers.contains(gazer)) {
                    gained.add(gazer);
                }
            }
            Tile parent = new Tile(Tile.Layout.Row, Tile.Margin.Empty);
            if (!gained.isEmpty()) {
                parent.append(makeGazersList(gained, "New stars", "+", GREEN), new Tile.Margin(0, 0, 0, 2));
            }
            if (!lost.isEmpty()) {
                parent.append(makeGazersList(lost, "Lost stars", "-", RED));
            }
            result = parent.render();
        }
        //noinspection ResultOfMethodCallIgnored
        getAppDirectory().mkdirs();
        try (FileWriter writer = new FileWriter(stargazersFile)) {
            for (String gazer : nowGazers) {
                writer.write(gazer + "\n");
            }
        }
        return result;
    }

    private LinkedHashSet<String> fetchStargazers() throws InterruptedException {
        Progress progress = new Progress("Fetching stargazers...");
        LinkedHashSet<String> nowGazers = new LinkedHashSet<>();
        Thread thread = new Thread(() -> {
            Stargazers onePage;
            int page = 0;
            int pageSize = 100; // max
            do {
                page++;
                onePage = getOne(Stargazers.request("https://api.github.com/repos/$_user/$_repo/stargazers?per_page=$pageSize&page=$page"));
                for (auto item : onePage.asOption0()) {
                    String gazer = item.getLogin();
                    nowGazers.add(gazer);
                }
            } while (!onePage.asOption0().isEmpty());
        });
        thread.start();
        while(thread.isAlive()) {
            progress.bumpProgress();
            //noinspection BusyWait
            Thread.sleep(250);
        }
        progress.clearProgress();
        return nowGazers;
    }

    private String makeGazersList(List<String> gazers, String title, String bullet, String color) {
        StringBuilder sb = new StringBuilder();
        if (!gazers.isEmpty()) {
            sb.append("$title\n");
            int count = gazers.size();
            for (int i = 0; i < count && i < 10; i++) {
                String gazer = gazers.get(i);
                sb.append("$color$bullet $gazer\n$RESET");
            }
            if (count > 10) {
                sb.append("$bullet ...and ${count-10} more\n");
            }
        }
        return sb.toString();
    }

    private String count(int count, String label) {
        if (count != 1) {
            label += "s";
        }
        return "$count $DKGREY$label$RESET";
    }

    private <T> T getOne(Requester<T> requester) {
        try {
            return requester
                    .withHeader("X-GitHub-Api-Version", "2022-11-28")
                    .withBearerAuthorization(_token)
                    .getOne();
        } catch(RuntimeException e) {
            if (e.getCause() instanceof IOException) {
                if (e.getCause().getMessage().contains("HTTP response code: 401")) {
                    throw new ReportedException("Unauthorized access for -token: $_token", e);
                }
                throw new ReportedException("-user and/or -repo not found: ${e.getCause().getMessage()}", e);
            }
            throw e;
        }
    }

    private String clipUrl(String url, int maxUrl) {
        if (url.length() > maxUrl) {
            url = url.substring(url.length() - maxUrl);
            int sep = url.indexOf('/');
            if (sep > 0) {
                url = url.substring(sep + 1);
            }
        }
        return url;
    }

    private String removeRepoPath(String url) {
        String repoPath = "/$_user/$_repo/";
        int i = url.indexOf(repoPath);
        if (i > 0) {
            url = url.substring(i + repoPath.length());
        }
        return url;
    }

    private static LocalDate today() {
        // the github api works off UTC time
        return LocalDate.now(ZoneOffset.UTC);
    }

    private static LocalDateTime todayWithTime() {
        // the github api works off UTC time
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private File getAppDirectory() {
        return new File(new File(System.getProperty("user.home"), "github-traffic"), "$_user${File.separator}$_repo");
    }

    @Structural
    interface CountedItem {
        LocalDateTime getTimestamp();
        int getUniques();
        int getCount();
    }
}
