package se.fredrikolsson.fb;


import com.google.common.collect.Lists;
import com.restfb.*;
import com.restfb.batch.BatchRequest;
import com.restfb.batch.BatchResponse;
import com.restfb.json.JsonObject;
import com.restfb.types.Comment;
import com.restfb.types.Comments;
import com.restfb.types.Post;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;


/**
 *
 */
public class FacebookCsv {
    private static String appId = "dummy";
   	private static String appSecret = "dummy";
   	private static String accessToken = "dummy";


       // TODO use from to date, and max number of posts to fetch as constraints
       // TODO page comments, the same way posts are paged
   	// TODO how to go from facebook page name to numeric id, e.g., facebook.com/dn.se to 321961491679
   	// TODO add optional filter terms so that only posts/comments including them (which?) are retained in the output


   	public static void main(String ... args) throws Exception {
   		FacebookClient client = new DefaultFacebookClient(accessToken, appSecret, Version.VERSION_2_8);

   		String pageId = "321961491679";

   		// https://developers.facebook.com/docs/graph-api/reference/v2.8/post

           List<String> targetPages = Lists.newArrayList("https://www.facebook.com/dn.se", "https://www.facebook.com/United/");

           JsonObject result = client.fetchObjects(targetPages, JsonObject.class, Parameter.with("fields", "likes.summary(true), fan_count.summary(true)"));
           System.out.println("result : " + result.toString());

           Map<String, String> idPageNameMap = new TreeMap<>();
           for (String targetPage : targetPages) {
               String id = ((JsonObject) result.get(targetPage)).get("id").asString();
               idPageNameMap.put(id, targetPage);
           }

           //System.exit(1);

           List<BatchRequest> requests = new ArrayList<>();

           for (Map.Entry<String, String> entry : idPageNameMap.entrySet()) {
               BatchRequest request = new BatchRequest.BatchRequestBuilder(pageId + "/feed")
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
   		List<BatchResponse> batchResponses = client.executeBatch(requests);

   		int responseNum = 0;
           int postNum = 0;
           // TODO make it max num per page!
           int maxPosts = 6;
           boolean shouldFinish = false;
   		for (BatchResponse response : batchResponses) {
               if (shouldFinish) {
                   break;
               }
   			responseNum++;
   			System.out.println("Response number: " + responseNum);
   			Connection<Post> posts = new Connection<>(client, response.getBody(), Post.class);
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
   					System.out.println("message: " + p.getMessage().replaceAll("\n", " "));
                       if (p.getCaption() != null) {
                           System.out.println("caption: " + p.getCaption());
                       }
   					System.out.println("status type: " + p.getStatusType());
   					System.out.println("created time: " + p.getCreatedTime());
   					System.out.println("post id: " + p.getId());
   					if (p.getUpdatedTime() != null) {
   						System.out.println("updated time: " + p.getUpdatedTime());
   					}
                       System.out.println("source: " + p.getSource());
                       if (p.getPromotionStatus() != null) {
                           System.out.println("promotion status: " + p.getPromotionStatus());
                       }
                       if (p.getAdminCreator() != null) {
                           System.out.println("admin creator: " + p.getAdminCreator().getName());
                       }
                       if (p.getReactions() != null) {
                           System.out.println("number of reactions (provided): " + p.getReactions().getTotalCount());
                           /*
                           int numReactions = 0;
                           Connection<Reactions.ReactionItem> connection = client.fetchConnection(p.getId() + "/reactions", Reactions.ReactionItem.class);
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
                           System.out.println("post number of likes: " + p.getLikes().getTotalCount());
                       }
                       if (p.getShares() != null) {
                           System.out.println("post number of shares: " + p.getShares().getCount());
                       }
   					if (p.getPlace() != null) {
   						System.out.println("place: " + p.getPlace().getLocationAsString());
   					}
                       if (p.getLink() != null) {
                           System.out.println("link: " + p.getLink());
                       }
                       if (p.getName() != null) {
                           System.out.println("name: " + p.getName());
                       }
   					System.out.println("permalink: " + p.getPermalinkUrl());
                       if (p.getParentId() != null) {
                           System.out.println("post parent id: " + p.getParentId());
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

}
