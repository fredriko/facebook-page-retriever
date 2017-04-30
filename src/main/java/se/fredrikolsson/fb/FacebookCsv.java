package se.fredrikolsson.fb;


import com.restfb.*;
import com.restfb.batch.BatchRequest;
import com.restfb.batch.BatchResponse;
import com.restfb.json.JsonArray;
import com.restfb.json.JsonObject;
import com.restfb.types.*;

import java.io.*;
import java.util.*;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static joptsimple.util.DateConverter.datePattern;


/**
 * Class for fetching Posts and Comments from a given set of Facebook Pages, and save them as
 * CSV files.
 */
public class FacebookCsv {

    private static Logger logger = LoggerFactory.getLogger(FacebookCsv.class);


    // https://developers.facebook.com/docs/graph-api/reference/v2.8/post

    /*
    TODO the program will have three modes:

    1) set-up: generate appAccessToken, e.g.:
    program --setup --appId <id> --appSecret <secret> --output facebookCredentials.properties

    2) expand given set of facebook page urls with the pages liked by the given facebook pages, in a given number of steps:
    program --findLikedPages --facebookCredentials <credentials.properties> --input <file> --output <file> --distance <integer>

    3) fetch data (main mode), e.g,
    program --facebookCredentials <credentials.properties> --input <facebookPagesUrlsFile> --outputDirectory [--since <date> --until <date>] --maxPosts --maxComments

     */

    // TODO keep track of rate limits - see "rate limiting" here: http://restfb.com/documentation/
    // TODO add optional filter terms so that only posts including them, and their associated comments, are retained in the output
    // TODO generate README with metadata for each run, alternatively generate master CSV with metadata.

    // TODO make program generate appAccessToken if there is none in the provided credentials properties file

    // TODO refactor code, check for unused dependencies so as to minimize final jar

    private static List<String> csvHeaderFields = new ArrayList<>();

    static {
        csvHeaderFields.add("facebook_page_url");
        csvHeaderFields.add("message");
        csvHeaderFields.add("from_id");
        csvHeaderFields.add("from_name");
        csvHeaderFields.add("status_type");
        csvHeaderFields.add("created_time");
        csvHeaderFields.add("id");
        csvHeaderFields.add("parent_id");
        csvHeaderFields.add("message_type");
        csvHeaderFields.add("likes_count");
        csvHeaderFields.add("shares_count");
        csvHeaderFields.add("url_in_message");
        csvHeaderFields.add("message_permalink_url");
    }

    private FacebookClient facebookClient;
    private Properties facebookCredentials;

    public static void main(String... args) throws Exception {

        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        OptionParser parser = new OptionParser();
        OptionSpec<Void> help = parser.accepts("help").forHelp();

        // --fetch -credentials <credentials file> -pages <string or file w page ids> -terms <filter terms> -since <date> -until <date> -maxPosts -maxComments -csv-out
        OptionSpec<Void> fetch = parser.acceptsAll(Arrays.asList("fetch", "f"));
        OptionSpec<File> credentials = parser.acceptsAll(Arrays.asList("credentials", "c")).requiredIf(fetch).withRequiredArg().ofType(File.class);
        OptionSpec<String> pages = parser.acceptsAll(Arrays.asList("pages", "p")).requiredIf(fetch).withRequiredArg().ofType(String.class);
        OptionSpec<String> terms = parser.acceptsAll(Arrays.asList("terms", "t")).availableIf(fetch).withRequiredArg().ofType(String.class).withValuesSeparatedBy(",");
        OptionSpec<Date> until = parser.acceptsAll(Arrays.asList("until", "u")).availableIf(fetch).withRequiredArg().withValuesConvertedBy(datePattern("yy-MM-dd"));
        OptionSpec<Date> since = parser.acceptsAll(Arrays.asList("since", "s")).availableIf(fetch).requiredIf(until).withRequiredArg().withValuesConvertedBy(datePattern("yy-MM-dd"));
        OptionSpec<Integer> maxPosts = parser.acceptsAll(Arrays.asList("maxPosts", "x")).availableIf(fetch).withRequiredArg().ofType(Integer.class);
        OptionSpec<Integer> maxComments = parser.acceptsAll(Arrays.asList("maxComments", "y")).availableIf(fetch).withRequiredArg().ofType(Integer.class);
        OptionSpec<File> outputDirectory = parser.acceptsAll(Arrays.asList("outputDirectory", "o")).requiredIf(fetch).withRequiredArg().ofType(File.class);

        OptionSet commandLine = null;
        try {
            commandLine = parser.parse(args);
        } catch (OptionException e) {
            System.out.println("Error while parsing the command line: " + e.getMessage());
            printUsage();
            System.exit(1);
        }

        if (commandLine.has(help)) {
            printUsage();
            System.exit(0);
        }

        System.out.println("Program started with arguments: " + String.join(" ", args));

        if (commandLine.has(fetch)) {

            FacebookCsv fb = new FacebookCsv(commandLine.valueOf(credentials).toString());
            // TODO set up pages to fetch: provided either as a string, or as a file. If string starting with a @, then try to read it as a file
            // TODO refactor into own method
            List<String> pagesToFetch = new ArrayList<>();
            if (commandLine.valueOf(pages).startsWith("@")) {
                // TODO read from file
                System.err.println("Reading pages from file not yet supported!");
            } else {
                for(String p : commandLine.valueOf(pages).split(",")) {
                    pagesToFetch.add(p.trim());
                }
            }
            // TODO refactor into own method
            List<String> filterTerms = new ArrayList<>();
            if (commandLine.has(terms)) {
                for (String term : commandLine.valueOf(terms).split(",")) {
                    filterTerms.add(term.trim());
                }
            }

            int maxNumPosts = -1;
            if (commandLine.has(maxPosts)) {
                maxNumPosts = commandLine.valueOf(maxPosts);
            }
            int maxNumComments = -1;
            if (commandLine.has(maxComments)) {
                maxNumComments = commandLine.valueOf(maxComments);
            }
            Date sinceDate = null;
            if (commandLine.has(since)) {
                sinceDate = commandLine.valueOf(since);
            }
            Date untilDate = null;
            if (commandLine.has(until)) {
                untilDate = commandLine.valueOf(until);
            }
            fb.process(pagesToFetch, filterTerms, commandLine.valueOf(outputDirectory), maxNumPosts, maxNumComments, sinceDate, untilDate);
        }

    }


    private static void printUsage() {
        System.out.println("Usage: ...");
    }

    private FacebookCsv(String propertiesFile) {
        init(propertiesFile);
    }

    private void process(List<String> facebookPageIdentifiers, List<String> filterTerms, File outputDirectory,
                         int maxNumPostsPerPage, int maxNumCommentsPerPage, Date since, Date until) throws IOException {

        if (!outputDirectory.isDirectory()) {
            logger.error("Provided output location {} is not a directory. Aborting!", outputDirectory.toString());
            return;
        }
        if (!outputDirectory.canWrite()) {
            logger.error("Cannot write to given output directory {}. Aborting!", outputDirectory.toString());
        }

        List<FacebookPageInfo> pages = fetchPageInfo(facebookPageIdentifiers);
        List<String> pageIds = new ArrayList<>();
        for (FacebookPageInfo page : pages) {
            pageIds.add(page.getId());
        }

        List<BatchRequest> requests = createBatchRequests(pageIds, 10, since, until);
        List<BatchResponse> batchResponses = getFacebookClient().executeBatch(requests);
        int responseNum = 0;

        for (BatchResponse response : batchResponses) {
            int numPostsSeenForCurrentPage = 0;
            boolean doneProcessingPage = false;

            // TODO refactor into new method
            String facebookPageUrl = pages.get(responseNum).getUrl();
            logger.info("Processing Facebook Page: {}", facebookPageUrl);
            String outputFileBaseName = outputDirectory.toString() + File.separator + createFileBaseName(facebookPageUrl);
            String postsFileName = outputFileBaseName + "-posts.csv";
            String commentsFileName = outputFileBaseName + "-comments.csv";
            CSVPrinter postCsv = new CSVPrinter(new PrintWriter(postsFileName), CSVFormat.DEFAULT);
            CSVPrinter commentCsv = new CSVPrinter(new PrintWriter(commentsFileName), CSVFormat.DEFAULT);
            postCsv.printRecord(getCsvHeaderFields());
            commentCsv.printRecord(getCsvHeaderFields());
            logger.info("Writing posts to file: {}", postsFileName);
            logger.info("Writing comments to file: {}", commentsFileName);

            responseNum++;

            Connection<Post> postConnection = new Connection<>(getFacebookClient(), response.getBody(), Post.class);
            for (List<Post> posts : postConnection) {
                if (doneProcessingPage) {
                    break;
                }
                for (Post post : posts) {
                    if (maxNumPostsPerPage != -1 && numPostsSeenForCurrentPage >= maxNumPostsPerPage) {
                        logger.info("Seen {} of maximum allowed {} posts for page {}",
                                numPostsSeenForCurrentPage, maxNumPostsPerPage, facebookPageUrl);
                        doneProcessingPage = true;
                        break;
                    }
                    numPostsSeenForCurrentPage++;

                    logger.info("Processing post number {} for page {}", numPostsSeenForCurrentPage, facebookPageUrl);

                    Map<String, String> postMap = getPostAsMap(post, facebookPageUrl);

                    if (!shouldIncludePost(post, filterTerms)) {
                        logger.info("Post did not fulfil filtering criteria. Skipping!");
                        continue;
                    }
                    printRecord(postCsv, postMap);

                    // TODO refactor into new method, handling comments only
                    // If maxNumCommentsPerPage == -1, then all comments should be fetched.
                    // If maxNumCommentsPerPage > 0, then a given number should be fetched.
                    // If maxNumCommentsPerPage == 0, then no comments should be fetched
                    if (maxNumCommentsPerPage != 0) {
                        int numCommentsSeenForCurrentPost = 0;
                        boolean doneProcessingComments = false;
                        Connection<Comment> commentConnection = fetchCommentConnection(post.getId());
                        int numComments = 0;
                        logger.info("Processing comments for post number {}: {}...",
                                numPostsSeenForCurrentPage, postMap.get("message_permalink_url"));
                        for (List<Comment> comments : commentConnection) {
                            if (doneProcessingComments) {
                                break;
                            }
                            for (Comment comment : comments) {
                                if (maxNumCommentsPerPage != -1 && numCommentsSeenForCurrentPost >= maxNumCommentsPerPage) {
                                    logger.info("Seen {} of maximum allowed {} comments for post {}",
                                            numCommentsSeenForCurrentPost, maxNumCommentsPerPage, post.getId());
                                    doneProcessingComments = true;
                                    break;
                                }
                                numCommentsSeenForCurrentPost++;
                                numComments++;
                                Map<String, String> commentMap =
                                        getCommentAsMap(comment, post.getId(), facebookPageUrl, "comment");
                                printRecord(commentCsv, commentMap);
                                if (!doneProcessingComments) {
                                    Connection<Comment> subCommentConnection = fetchCommentConnection(comment.getId());
                                    for (List<Comment> subComments : subCommentConnection) {
                                        for (Comment subComment : subComments) {
                                            if (maxNumCommentsPerPage != -1 && numCommentsSeenForCurrentPost >= maxNumCommentsPerPage) {
                                                logger.info("Seen {} of maximum allowed {} comments for post {}",
                                                        numCommentsSeenForCurrentPost, maxNumCommentsPerPage, post.getId());
                                                doneProcessingComments = true;
                                                break;
                                            }
                                            numCommentsSeenForCurrentPost++;
                                            numComments++;
                                            Map<String, String> subCommentMap =
                                                    getCommentAsMap(subComment, comment.getId(), facebookPageUrl, "sub_comment");
                                            printRecord(commentCsv, subCommentMap);
                                        }
                                    }
                                }
                            }
                        }
                        logger.info("Found {} {}", numComments, numComments == 1 ? "comment" : "comments");
                    }
                }
            }
            postCsv.close();
            commentCsv.close();
        }
    }

    private boolean shouldIncludePost(Post post, List<String> filterTerms) {
        boolean result = false;
        // If no filter terms are given, or if the post contains no text, then it should be included in
        // further processing.
        if (filterTerms.isEmpty() || post.getMessage() == null) {
            result = true;
        } else if (post.getMessage() != null) {
            String postContent = post.getMessage().toLowerCase();
            for (String filterTerm : filterTerms) {
                if (postContent.contains(filterTerm)) {
                    result = true;
                    break;
                }
            }
        }
        return result;
    }

    private String createFileBaseName(String facebookPageUrl) {
        facebookPageUrl = facebookPageUrl.endsWith("/")
                ? facebookPageUrl.substring(0, facebookPageUrl.length() - 1)
                : facebookPageUrl;
        return "facebook-page-"
                + facebookPageUrl.substring((facebookPageUrl.lastIndexOf('/') + 1), facebookPageUrl.length())
                .replaceAll("[\\.@_]+", "-")
                .toLowerCase();
    }

    private void printRecord(CSVPrinter out, Map<String, String> content) throws IOException {
        List<String> record = new ArrayList<>();
        for (String field : getCsvHeaderFields()) {
            if (content.containsKey(field)) {
                record.add(content.get(field));
            } else {
                record.add("");
            }
        }
        out.printRecord(record);
    }

    private Connection<Comment> fetchCommentConnection(String endPointId) {
        return getFacebookClient().fetchConnection(
                endPointId + "/comments",
                Comment.class,
                Parameter.with("fields", "from{id,name},message,created_time,comments,type,likes.summary(true){total_count}"));

    }

    private Map<String, String> getCommentAsMap(Comment c, String parentId, String facebookPageUrl, String statusType) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("facebook_page_url", facebookPageUrl);
        m.put("message", c.getMessage());
        m.put("from_id", c.getFrom().getId());
        m.put("from_name", c.getFrom().getName());
        m.put("status_type", statusType);
        if (c.getCreatedTime() != null) {
            m.put("created_time", c.getCreatedTime().toString());
        } else {
            m.put("created_time", null);
        }
        m.put("id", c.getId());
        m.put("parent_id", parentId);
        m.put("message_type", c.getType());
        if (c.getLikes() != null) {
            m.put("likes_count", c.getLikes().getTotalCount().toString());
        } else {
            m.put("likes_count", "0");
        }
        m.put("shares_count", null);
        m.put("url_in_message", null);
        m.put("message_permalink_url", null);
        return m;
    }

    private Map<String, String> getPostAsMap(Post p, String facebookPageUrl) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("facebook_page_url", facebookPageUrl);
        m.put("message", p.getMessage());
        m.put("from_id", p.getFrom().getId());
        m.put("from_name", p.getFrom().getName());
        m.put("status_type", p.getStatusType());
        if (p.getCreatedTime() != null) {
            m.put("created_time", p.getCreatedTime().toString());
        } else {
            m.put("created_time", null);
        }
        m.put("id", p.getId());
        m.put("parent_id", p.getParentId());
        m.put("message_type", p.getType());
        if (p.getLikes() != null) {
            m.put("likes_count", p.getLikes().getTotalCount().toString());
        } else {
            m.put("likes_count", "0");
        }
        if (p.getShares() != null) {
            m.put("shares_count", p.getShares().getCount().toString());
        } else {
            m.put("shares_count", "0");
        }
        m.put("url_in_message", p.getLink());
        m.put("message_permalink_url", p.getPermalinkUrl());
        return m;
    }


    protected List<BatchRequest> createBatchRequests(Collection<String> facebookPageIds, int requestLimit, Date since, Date until) {

        if (since != null && until != null && until.before(since)) {
            throw new IllegalArgumentException("Parameter \"until\" (" + until.toString() + ")cannot be before \"since\" (" + since.toString() + ")");
        }

        if (since == null && until != null) {
            throw new IllegalArgumentException("Parameter \"until\" requires a valid \"since\" parameter");
        }

        String fields = "from, "
                + "parent_id, "
                + "likes.limit(0).summary(true), "
                + "comments,"
                + "shares,"
                + "id,"
                + "name,"
                + "message,"
                + "status_type,"
                + "type,"
                + "created_time,"
                + "link,"
                + "permalink_url";

        List<BatchRequest> requests = new ArrayList<>();
        for (String id : facebookPageIds) {

            BatchRequest.BatchRequestBuilder builder;

            if (since != null && until != null) {
                builder = new BatchRequest.BatchRequestBuilder(id + "/feed")
                        .parameters(Parameter.with("limit", requestLimit), Parameter.with("fields", fields), Parameter.with("since", since), Parameter.with("until", until));
            } else {
                builder = new BatchRequest.BatchRequestBuilder(id + "/feed")
                        .parameters(Parameter.with("limit", requestLimit), Parameter.with("fields", fields));

            }
            requests.add(builder.build());
        }
        return requests;
    }


    private List<FacebookPageInfo> fetchPageInfo(List<String> facebookPageIdentifiers) {
        List<FacebookPageInfo> result = new ArrayList<>();
        for (String identifier : facebookPageIdentifiers) {
            FacebookPageInfo info = fetchPageInfo(identifier);
            logger.info("Got page info: {}", info.toString());
            result.add(info);
        }
        return result;
    }


    // TODO likes should be paged!
    // TODO add only liked pages that are of any of the (sub) categories of the primary, input, facebookPageIdentifier
    private FacebookPageInfo fetchPageInfo(String facebookPageIdentifier) {
        JsonObject result = getFacebookClient().fetchObject(facebookPageIdentifier, JsonObject.class, Parameter.with("fields", "id, link, name, likes{id, name, link, category, category_list{id, name, fb_page_categories}}, category, category_list{id, name, fb_page_categories}"));
        logger.info("result: {}", result.toString());

        FacebookPageInfo info = new FacebookPageInfo(result.get("name").asString(), result.get("id").asString());
        info.setUrl(result.get("link").asString());
        info.setPrimaryCategory(result.get("category").asString());

        List<FacebookPageInfo> likesUrls = new ArrayList<>();
        if (result.get("likes") != null) {
            JsonObject likes = result.get("likes").asObject();
            if (likes != null) {
                JsonArray data = likes.get("data").asArray();
                for (int i = 0; i < data.size(); i++) {
                    JsonObject datum = data.get(i).asObject();
                    logger.debug("datum: {}", datum.toString());
                    FacebookPageInfo likedPage = new FacebookPageInfo(datum.get("name").asString(), datum.get("id").asString());
                    likedPage.setUrl(datum.get("link").asString());
                    likedPage.setPrimaryCategory(datum.get("category").asString());
                    likesUrls.add(likedPage);
                }
            }
        }
        info.setLikes(likesUrls);
        return info;
    }

    private void init(String configFile) {
        try {
            Properties p = readProperties(configFile);
            setFacebookCredentials(p);
            setFacebookClient(new DefaultFacebookClient(p.getProperty("accessToken"), p.getProperty("appSecret"), Version.VERSION_2_8));
        } catch (IOException e) {
            throw new RuntimeException("Could not read properties file " + configFile + ": " + e.getMessage(), e);
        }
    }

    private FacebookClient getFacebookClient() {
        return facebookClient;
    }

    private void setFacebookClient(FacebookClient facebookClient) {
        this.facebookClient = facebookClient;
    }

    private Properties getFacebookCredentials() {
        return facebookCredentials;
    }

    private void setFacebookCredentials(Properties facebookCredentials) {
        this.facebookCredentials = facebookCredentials;
    }


    private static List<String> getCsvHeaderFields() {
        return csvHeaderFields;
    }

    private Properties readProperties(String propertiesFile) throws IOException {
        Properties p = new Properties();
        InputStream in = new FileInputStream(propertiesFile);
        p.load(in);
        in.close();
        return p;
    }


}
