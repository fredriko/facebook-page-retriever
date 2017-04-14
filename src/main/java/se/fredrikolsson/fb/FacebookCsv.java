package se.fredrikolsson.fb;


import com.google.common.collect.Lists;
import com.restfb.*;
import com.restfb.batch.BatchRequest;
import com.restfb.batch.BatchResponse;
import com.restfb.json.JsonObject;
import com.restfb.types.Comment;
import com.restfb.types.Comments;
import com.restfb.types.Post;

import java.io.*;
import java.util.*;


/**
 *
 */
public class FacebookCsv {

    // https://developers.facebook.com/docs/graph-api/reference/v2.8/post

    // TODO use from to date, and max number of posts to fetch as constraints
    // TODO page comments, the same way posts are paged
    // TODO how to go from facebook page name to numeric id, e.g., facebook.com/dn.se to 321961491679
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


    protected void process(List<String> facebookPageUrls) {

        List<BatchRequest> requests = createBatchRequests(facebookPageUrls);
        List<BatchResponse> batchResponses = getFacebookClient().executeBatch(requests);

        int responseNum = 0;
        int postNum = 0;
        // TODO make it max num per page!
        int maxPosts = 10;
        boolean shouldFinish = false;
        for (BatchResponse response : batchResponses) {
            if (shouldFinish) {
                break;
            }
            responseNum++;
            System.out.println("Response number: " + responseNum);
            Connection<Post> posts = new Connection<>(getFacebookClient(), response.getBody(), Post.class);
            for (List<Post> ps : posts) {
                if (shouldFinish) {
                    break;
                }
                for (Post p : ps) {
                    if (postNum >= maxPosts) {
                        System.out.println("Seen " + postNum + " of allowed " + maxPosts + " posts. Aborting!");
                        shouldFinish = true;
                        break;
                    }
                    postNum++;
                    if (p.getMessage() == null) {
                        continue;
                    }
                    System.out.println("\n#############");
                    System.out.println("Post number: " + postNum);

                    Map<String, String> postMap = getPostAsMap(p);
                    // DEBUG
                    for (Map.Entry<String, String> entry : postMap.entrySet()) {
                        System.out.println(entry.getKey() + " -> " + entry.getValue());
                    }


                    Comments comments = p.getComments();
                    if (comments != null) {
                        for (Comment comment : comments.getData()) {
                            System.out.println("    * comment: " + comment.getMessage().replaceAll("\n", " "));
                            System.out.println("      id: " + comment.getId());
                            System.out.println("      created time: " + comment.getCreatedTime());
                            if (comment.getLikes() != null) {
                                System.out.println("      likes count: " + comment.getLikes().getTotalCount());
                            }
                            if (comment.getFrom() != null) {
                                System.out.println("      from name: " + comment.getFrom().getName());
                                System.out.println("      from id: " + comment.getFrom().getId());
                            }
                            if (comment.getComments() != null) {
                                for (Comment c : comment.getComments().getData()) {
                                    System.out.println("        ** comment: " + c.getMessage().replaceAll("\n", " "));
                                    System.out.println("           id: " + c.getId());
                                    if (c.getLikes() != null) {
                                        System.out.println("           likes count: " + c.getLikes().getTotalCount());
                                    }
                                    if (c.getFrom() != null) {
                                        System.out.println("           from name: " + c.getFrom().getName());
                                        System.out.println("           from id: " + c.getFrom().getId());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    }


    protected Map<String, String> getPostAsMap(Post p) {
        Map<String, String> m = new LinkedHashMap<>();

        m.put("message", p.getMessage());
            m.put("caption", p.getCaption());
        if (p.getFrom() != null) {
            m.put("from_id", p.getFrom().getId());
            m.put("from_name", p.getFrom().getName());
        }
        m.put("status_type", p.getStatusType());
        if (p.getCreatedTime() != null) {
            m.put("created_time", p.getCreatedTime().toString());
        }
        m.put("post_id", p.getId());
        if (p.getUpdatedTime() != null) {
            m.put("updated_time", p.getUpdatedTime().toString());
        }
        m.put("source", p.getSource());
        if (p.getAdminCreator() != null) {
            m.put("admin_creator", p.getAdminCreator().getName());
        }
        if (p.getReactions() != null) {
            m.put("num_reactions", p.getReactions().getTotalCount().toString());
               /*
               int numReactions = 0;
               Connection<Reactions.ReactionItem> connection = getFacebookClient().fetchConnection(p.getId() + "/reactions", Reactions.ReactionItem.class);
               for (List<Reactions.ReactionItem> items : connection) {
                   for (Reactions.ReactionItem item : items) {
                       numReactions++;
                       //System.out.println("Reaction item : " + item.getType());
                   }
               }
               System.out.println("number of reactions (counted): " + numReactions);
               */
        }
        if (p.getLikes() != null) {
            m.put("num_likes", p.getLikes().getTotalCount().toString());
        }
        if (p.getShares() != null) {
            m.put("num_shares", p.getShares().getCount().toString());
        }
        if (p.getPlace() != null) {
            m.put("place", p.getPlace().getLocationAsString());
        }
        if (p.getLink() != null) {
            m.put("url_in_post", p.getLink());
        }
        m.put("post_permalink_url", p.getPermalinkUrl());
        return m;
    }

    protected List<BatchRequest> createBatchRequests(List<String> facebookPageUrls) {
        Map<String, String> idPageNameMap = fetchPageIds(facebookPageUrls);
        List<BatchRequest> requests = new ArrayList<>();
        for (Map.Entry<String, String> entry : idPageNameMap.entrySet()) {
            BatchRequest request = new BatchRequest.BatchRequestBuilder(entry.getKey() + "/feed")
                    .parameters(
                            Parameter.with("limit", 4),
                            // TODO reactions on comments, the same way they're used on posts
                            Parameter.with("fields",
                                    "reactions.summary(true), " +
                                            "caption, " +
                                            "from, " +
                                            "to, " +
                                            "likes.limit(0).summary(true), " +
                                            "comments.summary(true){" +
                                            "     reactions.summary(true)," +
                                            "     from{id,name}," +
                                            "     message," +
                                            "     parent," +
                                            "     likes.summary(true){total_count}," +
                                            "     total_count," +
                                            "     created_time," +
                                            "     comments.summary(true){" +
                                            "         reactions.summary(true)," +
                                            "         from{id,name}," +
                                            "         message," +
                                            "         parent," +
                                            "         likes.summary(true){total_count}," +
                                            "         total_count," +
                                            "         created_time}}, " +
                                            "shares, " +
                                            "id, " +
                                            "name, " +
                                            "message, " +
                                            "status_type, " +
                                            "type, " +
                                            "created_time, " +
                                            "link, " +
                                            "permalink_url, " +
                                            "place"))
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
    protected Map<String, String> fetchPageIds(List<String> facebookPageUrls) {
        JsonObject result = getFacebookClient().fetchObjects(facebookPageUrls, JsonObject.class, Parameter.with("fields", "likes.summary(true), fan_count.summary(true)"));
        Map<String, String> idPageNameMap = new TreeMap<>();
        for (String targetPage : facebookPageUrls) {
            String id = ((JsonObject) result.get(targetPage)).get("id").asString();
            idPageNameMap.put(id, targetPage);
        }
        return idPageNameMap;
    }


    protected void init(String configFile) {
        try {
            Properties p = readProperties(configFile);
            setFacebookCredentials(p);
            setFacebookClient(new DefaultFacebookClient(p.getProperty("accessToken"), p.getProperty("appSecret"), Version.VERSION_2_8));
        } catch (IOException e) {
            throw new RuntimeException("Could not read properties file " + configFile + ": " + e.getMessage(), e);
        }
    }

    protected FacebookClient getFacebookClient() {
        return facebookClient;
    }

    protected void setFacebookClient(FacebookClient facebookClient) {
        this.facebookClient = facebookClient;
    }

    protected Properties getFacebookCredentials() {
        return facebookCredentials;
    }

    protected void setFacebookCredentials(Properties facebookCredentials) {
        this.facebookCredentials = facebookCredentials;
    }


    Properties readProperties(String propertiesFile) throws IOException {
        Properties p = new Properties();
        InputStream in = new FileInputStream(propertiesFile);
        p.load(in);
        in.close();
        return p;
    }


}
