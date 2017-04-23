package se.fredrikolsson.fb;


import com.restfb.*;
import com.restfb.batch.BatchRequest;
import com.restfb.batch.BatchResponse;
import com.restfb.json.JsonObject;
import com.restfb.types.*;

import java.io.*;
import java.util.*;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Class for fetching Posts and Comments from a given set of Facebook Pages, and save them as
 * CSV files.
 */
public class FacebookCsv {

    private static Logger logger = LoggerFactory.getLogger(FacebookCsv.class);


    // https://developers.facebook.com/docs/graph-api/reference/v2.8/post

    // TODO make fetching of comments optional
    // TODO make it possible to have constraints on the number of comments fetched per post
    // TODO keep track of rate limits - see "rate limiting" here: http://restfb.com/documentation/
    // TODO use from to date, and max number of posts to fetch as constraints: Parameter.with("since", from), Parameter.with("until", until)
    // TODO add optional filter terms so that only posts/comments including them (which?) are retained in the output
    // TODO generate README with metadata for each run, alternatively generate master CSV with metadata.
    // TODO make program generate appAccessToken if there is none in the provided credentials properties file
    // TODO refactor code

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
        String propertiesFile = "/Users/fredriko/Dropbox/facebook-credentials.properties";
        String outputDirectoryName = "/Users/fredriko/Dropbox/tmp";
        List<String> targetPages = new ArrayList<>();
        targetPages.add("https://www.facebook.com/dn.se");
        targetPages.add("https://www.facebook.com/United/");

        Date oneDayAgo = new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000);
        logger.info("oneDayAgo: {}", oneDayAgo.toString());
        FacebookCsv fb = new FacebookCsv(propertiesFile);
        fb.process(targetPages, outputDirectoryName, 4, 2, oneDayAgo, null);
    }

    private FacebookCsv(String propertiesFile) {
        init(propertiesFile);
    }


    private void process(List<String> facebookPageUrls, String outputDirectoryName, int maxNumPostsPerPage, int maxNumCommentsPerPage, Date since, Date until) throws IOException {

        List<BatchRequest> requests = createBatchRequests(facebookPageUrls, 10, since, until);
        List<BatchResponse> batchResponses = getFacebookClient().executeBatch(requests);

        int responseNum = 0;

        for (BatchResponse response : batchResponses) {
            int numPostsSeenForCurrentPage = 0;
            boolean doneProcessingPage = false;

            // TODO refactor into new method
            String facebookPageUrl = facebookPageUrls.get(responseNum);
            logger.info("Processing Facebook Page: {}", facebookPageUrl);
            String outputFileBaseName = outputDirectoryName + File.separator + createFileBaseName(facebookPageUrl);
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


    // TODO test
    protected List<BatchRequest> createBatchRequests(List<String> facebookPageUrls, int requestLimit, Date since, Date until) {

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

        Map<String, String> idPageNameMap = fetchPageIds(facebookPageUrls);
        List<BatchRequest> requests = new ArrayList<>();
        for (Map.Entry<String, String> entry : idPageNameMap.entrySet()) {


            BatchRequest.BatchRequestBuilder builder;

            if (since == null && until == null) {
                builder = new BatchRequest.BatchRequestBuilder(entry.getKey() + "/feed")
                        .parameters(Parameter.with("limit", requestLimit), Parameter.with("fields", fields));
            } else {
                builder = new BatchRequest.BatchRequestBuilder(entry.getKey() + "/feed")
                        .parameters(Parameter.with("limit", requestLimit), Parameter.with("fields", fields), Parameter.with("since", since), Parameter.with("until", until));
            }
            requests.add(builder.build());
        }
        return requests;
    }

    /**
     * Fetches the Facebook page ids from given Facebook Page URLs.
     *
     * @param facebookPageUrls A list of Facebook URLs.
     * @return A map where a key is the Facebook id, and the corresponding value is the Facebook URL.
     */
    private Map<String, String> fetchPageIds(List<String> facebookPageUrls) {
        JsonObject result = getFacebookClient().fetchObjects(facebookPageUrls, JsonObject.class);
        Map<String, String> idPageNameMap = new LinkedHashMap<>();
        for (String targetPage : facebookPageUrls) {
            String id = ((JsonObject) result.get(targetPage)).get("id").asString();
            idPageNameMap.put(id, targetPage);
        }
        return idPageNameMap;
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
