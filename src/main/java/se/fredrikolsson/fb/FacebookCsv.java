package se.fredrikolsson.fb;


import com.google.common.collect.Lists;
import com.restfb.*;
import com.restfb.batch.BatchRequest;
import com.restfb.batch.BatchResponse;
import com.restfb.json.JsonObject;
import com.restfb.types.*;

import java.io.*;
import java.util.*;


/**
 *
 */
public class FacebookCsv {

    // https://developers.facebook.com/docs/graph-api/reference/v2.8/post

    // TODO how to keep track of which page we're getting data for?
    // TODO use from to date, and max number of posts to fetch as constraints
    // TODO add optional filter terms so that only posts/comments including them (which?) are retained in the output
    // TODO make distribution of reactions optional, as it takes a long time to calculate (relatively)

    private FacebookClient facebookClient;
    private Properties facebookCredentials;


    public static void main(String... args) throws Exception {
        String propertiesFile = "/Users/fredriko/Dropbox/facebook-credentials.properties";
        List<String> targetPages = Lists.newArrayList("https://www.facebook.com/dn.se", "https://www.facebook.com/United/");

        FacebookCsv fb = new FacebookCsv(propertiesFile);
        fb.process(targetPages);
    }

    private FacebookCsv(String propertiesFile) {
        init(propertiesFile);
    }


    // TODO the CSV headers should be the same for Post and Comments files
    // TODO create output files depending on the requests
    private void process(List<String> facebookPageUrls) {

        List<BatchRequest> requests = createBatchRequests(facebookPageUrls);
        List<BatchResponse> batchResponses = getFacebookClient().executeBatch(requests);

        System.out.println("Got " + batchResponses.size() + " batchResponses");

        int responseNum = 0;
        int postNum = 0;
        int maxPosts = 3;
        boolean shouldFinish = false;
        for (BatchResponse response : batchResponses) {
            if (shouldFinish) {
                break;
            }
            String facebookPageUrl = facebookPageUrls.get(responseNum);
            responseNum++;
            System.out.println("Response number: " + responseNum);
            Connection<Post> postConnection = new Connection<>(getFacebookClient(), response.getBody(), Post.class);
            for (List<Post> posts : postConnection) {
                if (shouldFinish) {
                    break;
                }
                for (Post post : posts) {
                    if (postNum >= maxPosts) {
                        System.out.println("Seen " + postNum + " of allowed " + maxPosts + " posts. Aborting!");
                        shouldFinish = true;
                        break;
                    }
                    postNum++;
                    if (post.getMessage() == null) {
                        continue;
                    }
                    System.out.println("\n#############");
                    System.out.println("Post number: " + postNum);

                    Map<String, String> postMap = getPostAsMap(post, facebookPageUrl);
                    // TODO create CSV record from post map and print to designated post CSV file
                    for (Map.Entry<String, String> entry : postMap.entrySet()) {
                        System.out.println(entry.getKey() + " -> " + entry.getValue());
                    }

                    Connection<Comment> commentConnection = fetchCommentConnection(post.getId());
                    for (List<Comment> comments : commentConnection) {
                        for (Comment comment : comments) {

                            Map<String, String> commentMap = getCommentAsMap(comment, post.getId(), facebookPageUrl, "comment");

                            // TODO create CSV record from comment map and print to designated comments CSV file
                            for (Map.Entry<String, String> entry : commentMap.entrySet()) {
                                System.out.println("    * " + entry.getKey() + " -> " + entry.getValue());
                            }
                            System.out.println("");

                            Connection<Comment> subCommentConnection = fetchCommentConnection(comment.getId());
                            for (List<Comment> subComments : subCommentConnection) {
                                for (Comment subComment : subComments) {

                                    Map<String, String> subCommentMap = getCommentAsMap(subComment, comment.getId(), facebookPageUrl, "sub_comment");

                                    // TODO create CSV record from comment map and print to designated comments CSV file
                                    for (Map.Entry<String, String> entry : subCommentMap.entrySet()) {
                                        System.out.println("        ** " + entry.getKey() + " -> " + entry.getValue());
                                    }
                                    System.out.println("");

                                }
                            }
                        }
                    }
                }
            }
        }
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
        }
        m.put("id", c.getId());
        m.put("in_response_to_id", parentId);
        m.put("message_type", c.getType());
        if (c.getLikes() != null) {
            m.put("likes_count", c.getLikes().getTotalCount().toString());
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
        }
        m.put("id", p.getId());
        m.put("in_response_to_id", p.getParentId());
        m.put("message_type", p.getType());
        if (p.getLikes() != null) {
            m.put("likes_count", p.getLikes().getTotalCount().toString());
        }
        if (p.getShares() != null) {
            m.put("shares_count", p.getShares().getCount().toString());
        }
        m.put("url_in_message", p.getLink());
        m.put("message_permalink_url", p.getPermalinkUrl());
        return m;
    }

    /*
    TODO
                                Parameter.with("since", from),
                            Parameter.with("until", until),
     */
    private List<BatchRequest> createBatchRequests(List<String> facebookPageUrls) {
        Map<String, String> idPageNameMap = fetchPageIds(facebookPageUrls);
        List<BatchRequest> requests = new ArrayList<>();
        for (Map.Entry<String, String> entry : idPageNameMap.entrySet()) {
            BatchRequest request = new BatchRequest.BatchRequestBuilder(entry.getKey() + "/feed")
                    .parameters(
                            Parameter.with("limit", 10),
                            Parameter.with("fields",
                                            "from, " +
                                            "parent_id, " +
                                            "likes.limit(0).summary(true), " +
                                            "comments," +
                                            "shares, " +
                                            "id, " +
                                            "name, " +
                                            "message, " +
                                            "status_type, " +
                                            "type, " +
                                            "created_time, " +
                                            "link, " +
                                            "permalink_url"))
                    .build();
            requests.add(request);
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


    private Properties readProperties(String propertiesFile) throws IOException {
        Properties p = new Properties();
        InputStream in = new FileInputStream(propertiesFile);
        p.load(in);
        in.close();
        return p;
    }


}
