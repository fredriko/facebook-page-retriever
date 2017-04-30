package se.fredrikolsson.fb;

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class FacebookPageInfo {

    private String name;
    private String id;
    private String url;
    private String primaryCategory;
    private List<String> subCategories;
    private List<FacebookPageInfo> likes = new ArrayList<>();

    FacebookPageInfo(String id) {
        this.id = id;
    }

    FacebookPageInfo(String name, String id) {
        this.name = name;
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public List<FacebookPageInfo> getLikes() {
        return likes;
    }

    public void setLikes(List<FacebookPageInfo> likes) {
        this.likes = likes;
    }

    public String getPrimaryCategory() {
        return primaryCategory;
    }

    public void setPrimaryCategory(String primaryCategory) {
        this.primaryCategory = primaryCategory;
    }

    public List<String> getSubCategories() {
        return subCategories;
    }

    public void setSubCategories(List<String> subCategories) {
        this.subCategories = subCategories;
    }

    @Override
    public String toString() {
        return "FacebookPageId[name=" + getName() +", id=" + getId() + ", url=" + getUrl() + ", category=" + getPrimaryCategory() + ", likes=" + getLikes() + "]";
    }
}
